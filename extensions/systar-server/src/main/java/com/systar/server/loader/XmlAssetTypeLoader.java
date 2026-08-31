package com.systar.server.loader;

import com.systar.data.entity.AssetTypeConfigEntity;
import com.systar.data.mapper.AssetTypeConfigMapper;
import com.systar.monitor.asset.AssetException;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.type.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * Loads asset type definitions from XML configuration files on the classpath.
 * <p>
 * Reads {@code config/assets/Assets.xml} as the master index, then parses each
 * referenced file to extract Space/Device/Service/Probe/Control type definitions.
 * Each parsed type is registered with the corresponding {@link AssetTypeManager}
 * in the {@link AssetStore} and synced to the {@code t_asset_type_config} database table.
 * <p>
 * Key loading rules:
 * <ul>
 *   <li>Loading order: Space → Device → Service → Probe → Control</li>
 *   <li>Type inheritance via {@code Super="ParentType"} attribute</li>
 *   <li>Probe/Control {@code <Source>} references Service type by name</li>
 *   <li>DB sync uses file last-modified as version; obsolete configs are deleted</li>
 * </ul>
 */
@Component
public class XmlAssetTypeLoader implements com.systar.monitor.asset.type.AssetTypeLoader {

    private static final Logger log = LoggerFactory.getLogger(XmlAssetTypeLoader.class);
    private static final String CONFIG_DIR = "classpath:config/assets/";
    private static final String MASTER_INDEX = CONFIG_DIR + "Assets.xml";

    private final AssetTypeConfigMapper typeConfigMapper;

    /** Stores AssetStore reference for type lookup during inheritance/source resolution. */
    private AssetStore store;

    /** Collects config file names per kind, populated from master index. */
    private final Map<AssetKind, List<String>> configFilesByKind = new EnumMap<>(AssetKind.class);

    public XmlAssetTypeLoader(AssetTypeConfigMapper typeConfigMapper) {
        this.typeConfigMapper = typeConfigMapper;
    }

    @Override
    public void load(AssetStore store) {
        this.store = store;
        log.info("Loading asset types from XML...");

        var resolver = new PathMatchingResourcePatternResolver();
        Resource master = resolver.getResource(MASTER_INDEX);
        if (!master.exists()) {
            throw new AssetException("Master index %s not found; cannot load asset types.", MASTER_INDEX);
        }

        try {
            parseMasterIndex(master);
        } catch (AssetException e) {
            log.error("Asset type loading failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse master index", e);
            throw new AssetException(e, "Asset type loading failed: %s", e.getMessage());
        }

        int count = 0;
        try {
            count += loadKindGroup(resolver, AssetKind.SPACE,
                    "Spaces", "Space", store.getSpaceTypes());
            count += loadKindGroup(resolver, AssetKind.DEVICE,
                    "Devices", "Device", store.getDeviceTypes());
            count += loadKindGroup(resolver, AssetKind.SERVICE,
                    "Services", "Service", store.getServiceTypes());
            count += loadKindGroup(resolver, AssetKind.PROBE,
                    "ProbeList", "Probe", store.getProbeTypes());
            count += loadKindGroup(resolver, AssetKind.CONTROL,
                    "ControlList", "Control", store.getControlTypes());
        } catch (AssetException e) {
            log.error("Asset type loading failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to load asset types from XML", e);
            throw new AssetException(e, "Asset type loading failed: %s", e.getMessage());
        }

        log.info("Asset types loaded: {} types registered.", count);
    }

    // ======================== master index parsing ========================

    private void parseMasterIndex(Resource master) throws Exception {
        configFilesByKind.clear();
        SAXReader reader = createSaxReader();
        Document doc = reader.read(master.getInputStream());
        Element root = doc.getRootElement();

        // Tag name → kind mapping convention: "SpaceConfig" → SPACE, etc.
        Map<String, AssetKind> tagToKind = Map.of(
                "SpaceConfig", AssetKind.SPACE,
                "DeviceConfig", AssetKind.DEVICE,
                "ServiceConfig", AssetKind.SERVICE,
                "ProbeConfig", AssetKind.PROBE,
                "ControlConfig", AssetKind.CONTROL
        );

        for (Object nodeObj : root.elements()) {
            Element node = (Element) nodeObj;
            AssetKind kind = tagToKind.get(node.getName());
            if (kind != null) {
                configFilesByKind.computeIfAbsent(kind, k -> new ArrayList<>())
                        .add(node.getTextTrim());
            }
        }
    }

    // ======================== type group loading ========================

    private <T extends AssetType> int loadKindGroup(
            PathMatchingResourcePatternResolver resolver,
            AssetKind kind,
            String rootElementName,
            String itemElementName,
            AssetTypeManager<T> manager) throws Exception {

        List<String> configFiles = configFilesByKind.getOrDefault(kind, List.of());
        Set<String> seenInXml = new HashSet<>();
        int count = 0;

        for (String fileName : configFiles) {
            String location = CONFIG_DIR + fileName + ".xml";
            Resource resource = resolver.getResource(location);
            if (!resource.exists()) {
                throw new AssetException("Type config %s referenced by master index but not found.", location);
            }

            SAXReader reader = createSaxReader();
            Document doc = reader.read(resource.getInputStream());
            Element root = doc.getRootElement();

            if (!root.getName().equals(rootElementName)) {
                log.warn("Config '{}' has root element <{}> but expected <{}>. "
                        + "Type loading may fail if child element names also don't match.",
                        location, root.getName(), rootElementName);
            }

            List<?> items = root.elements(itemElementName);
            if (items.isEmpty()) {
                log.warn("Config '{}' has 0 <{}> elements under root <{}>. "
                        + "Element names must be exactly '{}' (not protocol-prefixed like 'SnmpService'). "
                        + "Types in this file will NOT be loaded.",
                        location, itemElementName, root.getName(), itemElementName);
            }

            for (Object itemObj : items) {
                Element item = (Element) itemObj;
                T type = parseType(item, kind, manager);
                seenInXml.add(type.getName());
                try {
                    manager.register(type);
                    count++;
                } catch (IllegalArgumentException e) {
                    throw new AssetException("Duplicate type name '%s' in %s config; type names must be unique across all config files.",
                            type.getName(), kind);
                }
            }
        }

        syncDbConfigs(kind, seenInXml);
        log.info("Loaded {} {} types.", count, kind.name().toLowerCase());
        return count;
    }

    // ======================== XML type parsing ========================

    @SuppressWarnings("unchecked")
    private <T extends AssetType> T parseType(Element item, AssetKind kind,
                                               AssetTypeManager<T> manager) {
        String name = getAttr(item, "Name");
        if (name.isEmpty()) {
            throw new AssetException("Anonymous type found in %s config; all types must have a Name attribute.", kind);
        }

        AssetType type = switch (kind) {
            case SPACE -> new SpaceType(name);
            case DEVICE -> new DeviceType(name);
            case SERVICE -> new ServiceType(name);
            case PROBE -> new ProbeType(name);
            case CONTROL -> new ControlType(name);
        };

        loadCommonInfo(type, item, kind);

        if (type instanceof MonitorType mt) {
            loadMonitorAttrs(item, mt);
        }

        return (T) type;
    }

    private void loadCommonInfo(AssetType type, Element item, AssetKind kind) {
        // Caption
        String caption = getAttr(item, "Caption");
        type.setCaption(caption);

        // Inheritance: Super="ParentType" — copy properties from parent first
        loadSuperType(type, item, kind);

        // JavaClass
        Element classEl = item.element("JavaClass");
        if (classEl != null) {
            type.setRelatedClass(classEl.getTextTrim());
        }

        // Abstract flag
        type.setAbstractType(Boolean.parseBoolean(getAttr(item, "Abstract")));

        // PropertyList
        loadPropertyList(type, item.element("PropertyList"));
    }

    /**
     * Handles type inheritance via {@code Super="ParentType"} attribute.
     * Copies all properties from the parent type before loading this type's own properties.
     */
    @SuppressWarnings("unchecked")
    private void loadSuperType(AssetType type, Element item, AssetKind kind) {
        String superName = getAttr(item, "Super");
        if (superName.isEmpty()) return;

        AssetType superType = resolveSuperType(superName, kind);
        if (superType == null) {
            throw new AssetException("Super type '%s' not found for '%s'; inheritance chain is broken.",
                    superName, type.getName());
        }

        type.setSuperType(superType);

        // Copy inherited properties using copy constructor (preserves required field)
        for (AssetTypeProperty prop : superType.getProperties()) {
            type.addProperty(new AssetTypeProperty(prop));
        }
    }

    /**
     * Resolves a super type by name. Because types are loaded in order
     * (Space→Device→Service→Probe→Control) and within each group the master index
     * controls the file order, parent types should already be registered.
     */
    private AssetType resolveSuperType(String name, AssetKind kind) {
        return resolveType(name, kind);
    }

    private AssetType resolveType(String name, AssetKind kind) {
        return switch (kind) {
            case SPACE -> store.getSpaceTypes().find(name);
            case DEVICE -> store.getDeviceTypes().find(name);
            case SERVICE -> store.getServiceTypes().find(name);
            case PROBE -> store.getProbeTypes().find(name);
            case CONTROL -> store.getControlTypes().find(name);
        };
    }

    private void loadPropertyList(AssetType type, Element propListEl) {
        if (propListEl == null) return;

        for (Object propObj : propListEl.elements("Property")) {
            Element propEl = (Element) propObj;
            String propName = getAttr(propEl, "Name");
            String propCaption = getAttr(propEl, "Caption");
            if (propCaption.isEmpty()) propCaption = propName;

            DataType dataType   = DataType.STRING;
            Double   min        = null;
            Double   max        = null;
            Integer  maxLength  = null;

            Element dtEl = propEl.element("DataType");
            if (dtEl != null) {
                dataType = parseDataType(dtEl.getTextTrim());
                min       = getDoubleAttr(dtEl, "Min");
                max       = getDoubleAttr(dtEl, "Max");
                maxLength = getIntAttr(dtEl, "MaxLength");
                // 兼容旧配置: MaxValue 是 Max 的旧属性别名
                if (max == null) max = getDoubleAttr(dtEl, "MaxValue");
            }

            String defaultVal = propEl.attributeValue("Default");
            boolean required  = Boolean.parseBoolean(propEl.attributeValue("Required"));

            AssetTypeProperty prop = new AssetTypeProperty(propName, dataType, defaultVal,
                    propCaption, min, max, maxLength);
            prop.setRequired(required);

            // Property-level ViewType (constructor already inferred default from DataType)
            Element propViewTypeEl = propEl.element("ViewType");
            if (propViewTypeEl != null && propViewTypeEl.getTextTrim() != null) {
                try {
                    prop.setViewType(ViewType.valueOf(propViewTypeEl.getTextTrim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Unrecognized ViewType '{}' for property '{}' in type '{}'; ignoring.",
                            propViewTypeEl.getTextTrim(), propName, type.getName());
                }
            }

            type.addProperty(prop);
        }
    }

    private void loadMonitorAttrs(Element item, MonitorType type) {
        // Source: references a Service type by name (must be already registered)
        String sourceName = item.elementText("Source");
        if (sourceName != null && !sourceName.isBlank()) {
            ServiceType sourceType = store.getServiceTypes().find(sourceName);
            if (sourceType == null) {
                throw new AssetException("Source service type '%s' not found for monitor type '%s'.",
                        sourceName, type.getName());
            }
            type.setSource(sourceName);
        }

        // Type-level DataType (e.g. <DataType>BOOLEAN</DataType> on Probe/Control)
        Element dataTypeEl = item.element("DataType");
        if (dataTypeEl != null && dataTypeEl.getTextTrim() != null) {
            type.setDataType(parseDataType(dataTypeEl.getTextTrim()));
        }

        // Type-level ViewType (e.g. <ViewType>YESNO</ViewType> on Probe/Control)
        Element viewTypeEl = item.element("ViewType");
        if (viewTypeEl != null && viewTypeEl.getTextTrim() != null) {
            try {
                type.setViewType(ViewType.valueOf(viewTypeEl.getTextTrim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unrecognized ViewType '{}' for type '{}'; ignoring.",
                        viewTypeEl.getTextTrim(), type.getName());
            }
        } else if (type.getDataType() != null) {
            type.setViewType(ViewType.infer(type.getDataType()));
        }

        // Description
        Element descEl = item.element("Description");
        if (descEl != null) {
            type.setTransform(descEl.getTextTrim());
        }
    }

    // ======================== DB sync ========================

    /**
     * Syncs XML type configs to the database.
     * <ul>
     *   <li>Types present in XML but not in DB → INSERT</li>
     *   <li>Types present in both → UPDATE (increment version)</li>
     *   <li>Types present in DB but not in XML → DELETE (obsolete)</li>
     * </ul>
     */
    private void syncDbConfigs(AssetKind kind, Set<String> seenInXml) {
        // Load existing DB configs for this kind
        QueryWrapper<AssetTypeConfigEntity> qw = new QueryWrapper<>();
        qw.eq("kind", kind.name());
        List<AssetTypeConfigEntity> existing = typeConfigMapper.selectList(qw);

        Map<String, AssetTypeConfigEntity> dbMap = new HashMap<>();
        for (AssetTypeConfigEntity e : existing) {
            dbMap.put(e.getTypeName(), e);
        }

        // Insert or update
        for (String typeName : seenInXml) {
            AssetTypeConfigEntity dbEntity = dbMap.remove(typeName);
            AssetType type = resolveType(typeName, kind);
            if (dbEntity != null) {
                // Update existing
                dbEntity.setCaption(type != null ? type.getCaption() : null);
                dbEntity.setDriverClass(type != null ? type.getRelatedClass() : null);
                dbEntity.setProperties(type != null ? serializeProperties(type.getProperties()) : null);
                dbEntity.setVersion(dbEntity.getVersion() + 1);
                typeConfigMapper.updateById(dbEntity);
            } else if (type != null) {
                // Insert new
                AssetTypeConfigEntity entity = new AssetTypeConfigEntity();
                entity.setKind(kind.name());
                entity.setTypeName(type.getName());
                entity.setCaption(type.getCaption());
                entity.setDriverClass(type.getRelatedClass());
                entity.setProperties(serializeProperties(type.getProperties()));
                entity.setVersion(1);
                typeConfigMapper.insert(entity);
            }
        }

        // Delete obsolete configs (present in DB but not in XML)
        for (AssetTypeConfigEntity obsolete : dbMap.values()) {
            typeConfigMapper.deleteById(obsolete.getId());
            log.info("Deleted obsolete type config: {}/{}", kind, obsolete.getTypeName());
        }
    }

    private String serializeProperties(Collection<AssetTypeProperty> properties) {
        if (properties == null || properties.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (AssetTypeProperty p : properties) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"name\":\"").append(escapeJson(p.getName())).append("\"");
            sb.append(",\"dataType\":\"").append(p.getDataType()).append("\"");
            if (p.getDefaultValue() != null) {
                sb.append(",\"defaultValue\":\"").append(escapeJson(p.getDefaultValue())).append("\"");
            }
            if (p.getDescription() != null) {
                sb.append(",\"description\":\"").append(escapeJson(p.getDescription())).append("\"");
            }
            if (p.getMin() != null) {
                sb.append(",\"min\":").append(p.getMin());
            }
            if (p.getMax() != null) {
                sb.append(",\"max\":").append(p.getMax());
            }
            if (p.getMaxLength() != null) {
                sb.append(",\"maxLength\":").append(p.getMaxLength());
            }
            if (p.getViewType() != null) {
                sb.append(",\"viewType\":\"").append(p.getViewType().name()).append("\"");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Double getDoubleAttr(Element el, String name) {
        String val = el.attributeValue(name);
        if (val == null || val.isBlank()) return null;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getIntAttr(Element el, String name) {
        String val = el.attributeValue(name);
        if (val == null || val.isBlank()) return null;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ======================== utilities ========================

    private DataType parseDataType(String text) {
        if (text == null) return DataType.STRING;
        String token = text.toUpperCase().trim();
        return switch (token) {
            case "INT", "INTEGER"            -> DataType.INT;
            case "FLOAT", "DOUBLE", "NUMBER" -> DataType.FLOAT;
            case "BOOL", "BOOLEAN"           -> DataType.BOOLEAN;
            case "STRING", "TEXT"            -> DataType.STRING;
            case "TIMESPAN", "DURATION"      -> DataType.TIMESPAN;
            default -> {
                log.warn("Unrecognized DataType token '{}' in XML; falling back to STRING. " +
                        "Supported: INT/INTEGER, FLOAT/DOUBLE/NUMBER, BOOL/BOOLEAN, " +
                        "STRING/TEXT, TIMESPAN/DURATION.", text);
                yield DataType.STRING;
            }
        };
    }

    private String getAttr(Element el, String name) {
        String value = el.attributeValue(name);
        return value == null ? "" : value.trim();
    }

    private SAXReader createSaxReader() {
        SAXReader reader = new SAXReader();
        try {
            reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            log.warn("Could not configure SAX security features", e);
        }
        return reader;
    }
}
