package com.systar.server.loader;

import com.systar.data.entity.AssetTypeConfigEntity;
import com.systar.data.mapper.AssetTypeConfigMapper;
import com.systar.monitor.asset.AssetException;
import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.type.*;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Loads asset type definitions by scanning XML resources — drivers
 * self-register, there is no central index file.
 * <p>
 * Scanned locations are the built-in defaults — type XMLs shipped inside the
 * drivers module ({@value #DRIVERS_PATTERN}) and the server-side generic
 * Device XMLs ({@value #GENERIC_PATTERN}) — plus any extra patterns from
 * the {@code systar.asset-type.scan-paths} property (comma-separated resource
 * location patterns, e.g. {@code file:./config/drivers/*.xml} for private
 * driver types kept outside the repository).
 * <p>
 * Key loading rules:
 * <ul>
 *   <li>Each file is classified by its root element:
 *       {@code Devices/Services/ProbeList/ControlList}; an unknown root
 *       element fails fast with the file name and the legal roots</li>
 *   <li>Loading order: Device → Service → Probe → Control; within a
 *       kind, files are processed in sorted location order and document order
 *       is preserved, so a {@code Super} parent declared earlier in the same
 *       file resolves</li>
 *   <li>Type names are unique within a kind across all scan paths — a
 *       duplicate throws (no override semantics)</li>
 *   <li>Probe/Control {@code <Source>} references a Service type by name,
 *       satisfied by the kind loading order</li>
 *   <li>DB sync inserts new configs (version 1), updates with version+1 only
 *       when caption/driverClass/serialized properties actually changed, and
 *       deletes configs no longer present in any scan path</li>
 * </ul>
 */
@Component
public class XmlAssetTypeLoader implements com.systar.monitor.asset.type.AssetTypeLoader {

    private static final Logger log = LoggerFactory.getLogger(XmlAssetTypeLoader.class);

    /** Built-in pattern: every XML under the drivers module's package tree. */
    static final String DRIVERS_PATTERN = "classpath*:com/systar/monitor/drivers/**/*.xml";

    /** Built-in pattern: generic Device type XMLs kept in the server module. */
    static final String GENERIC_PATTERN = "classpath*:config/assets/generic-*.xml";

    /** Root element name → asset kind. */
    private static final Map<String, AssetKind> ROOT_TO_KIND = Map.of(
            "Devices", AssetKind.DEVICE,
            "Services", AssetKind.SERVICE,
            "ProbeList", AssetKind.PROBE,
            "ControlList", AssetKind.CONTROL);

    /** Version stamped on first insert of a type config row. */
    private static final int INITIAL_VERSION = 1;

    private final AssetTypeConfigMapper typeConfigMapper;

    /** All scan patterns: built-in defaults first, then configured extras. */
    private final List<String> scanPatterns;

    @Autowired
    public XmlAssetTypeLoader(AssetTypeConfigMapper typeConfigMapper,
                              @Value("${systar.asset-type.scan-paths:}") String extraScanPaths) {
        this.typeConfigMapper = typeConfigMapper;
        List<String> patterns = new ArrayList<>(List.of(DRIVERS_PATTERN, GENERIC_PATTERN));
        for (String pattern : extraScanPaths.split(",")) {
            if (!pattern.isBlank()) {
                patterns.add(pattern.trim());
            }
        }
        this.scanPatterns = List.copyOf(patterns);
    }

    /** Constructor without extra scan paths (built-in defaults only). */
    public XmlAssetTypeLoader(AssetTypeConfigMapper typeConfigMapper) {
        this(typeConfigMapper, "");
    }

    @Override
    public void load(AssetStore store) {
        log.info("Loading asset types from scan paths {}...", scanPatterns);

        Map<AssetKind, List<TypeResource>> resourcesByKind = scanAndClassify();
        Map<AssetKind, Set<String>> seenByKind = new EnumMap<>(AssetKind.class);

        int count = 0;
        try {
            count += loadKindGroup(store, AssetKind.DEVICE, "Device",
                    store.getDeviceTypes(), resourcesByKind, seenByKind);
            count += loadKindGroup(store, AssetKind.SERVICE, "Service",
                    store.getServiceTypes(), resourcesByKind, seenByKind);
            count += loadKindGroup(store, AssetKind.PROBE, "Probe",
                    store.getProbeTypes(), resourcesByKind, seenByKind);
            count += loadKindGroup(store, AssetKind.CONTROL, "Control",
                    store.getControlTypes(), resourcesByKind, seenByKind);
            syncDbConfigs(store, seenByKind);
        } catch (AssetException e) {
            log.error("Asset type loading failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to load asset types from XML", e);
            throw new AssetException(e, "Asset type loading failed: %s", e.getMessage());
        }

        log.info("Asset types loaded: {} types registered.", count);
    }

    // ======================== resource scanning ========================

    /** A scanned XML resource with its resolved location (sort/dedup key). */
    private record TypeResource(String location, Resource resource) {}

    /**
     * Resolves every scan pattern and classifies each unique resource by its
     * root element. Resources matching several patterns load once (first
     * pattern wins); unknown root elements fail fast.
     */
    private Map<AssetKind, List<TypeResource>> scanAndClassify() {
        var resolver = new PathMatchingResourcePatternResolver();
        Map<String, TypeResource> uniqueByLocation = new TreeMap<>();

        for (String pattern : scanPatterns) {
            Resource[] matched;
            try {
                matched = resolver.getResources(pattern);
            } catch (IOException e) {
                throw new AssetException(e, "Cannot resolve asset type scan pattern '%s'.", pattern);
            }
            for (Resource resource : matched) {
                if (!resource.isReadable()) {
                    continue;
                }
                TypeResource typeResource = describe(resource);
                uniqueByLocation.putIfAbsent(typeResource.location(), typeResource);
            }
        }

        Map<AssetKind, List<TypeResource>> byKind = new EnumMap<>(AssetKind.class);
        for (TypeResource typeResource : uniqueByLocation.values()) {
            String rootName = readRootElementName(typeResource);
            AssetKind kind = ROOT_TO_KIND.get(rootName);
            if (kind == null) {
                throw new AssetException(
                        "File '%s' has unrecognized root element <%s>; expected one of %s.",
                        typeResource.location(), rootName, legalRoots());
            }
            byKind.computeIfAbsent(kind, k -> new ArrayList<>()).add(typeResource);
        }
        return byKind;
    }

    /** Legal root names, sorted for a deterministic error message. */
    private static String legalRoots() {
        return String.join(", ", new TreeSet<>(ROOT_TO_KIND.keySet()));
    }

    private TypeResource describe(Resource resource) {
        try {
            return new TypeResource(resource.getURL().toExternalForm(), resource);
        } catch (IOException e) {
            log.debug("Cannot resolve URL for resource {}; using toString() as sort key.", resource, e);
            return new TypeResource(resource.toString(), resource);
        }
    }

    private String readRootElementName(TypeResource typeResource) {
        try (InputStream in = typeResource.resource().getInputStream()) {
            return createSaxReader().read(in).getRootElement().getName();
        } catch (Exception e) {
            throw new AssetException(e, "Cannot parse asset type XML '%s'.", typeResource.location());
        }
    }

    // ======================== type group loading ========================

    private <T extends AssetType> int loadKindGroup(AssetStore store,
                                                    AssetKind kind,
                                                    String itemElementName,
                                                    AssetTypeManager<T> manager,
                                                    Map<AssetKind, List<TypeResource>> resourcesByKind,
                                                    Map<AssetKind, Set<String>> seenByKind) throws Exception {
        Set<String> seenInXml = seenByKind.computeIfAbsent(kind, k -> new LinkedHashSet<>());
        int count = 0;

        for (TypeResource typeResource : resourcesByKind.getOrDefault(kind, List.of())) {
            SAXReader reader = createSaxReader();
            Element root;
            try (InputStream in = typeResource.resource().getInputStream()) {
                root = reader.read(in).getRootElement();
            }

            List<?> items = root.elements(itemElementName);
            if (items.isEmpty()) {
                log.warn("Config '{}' has 0 <{}> elements under root <{}>. "
                        + "Element names must be exactly '{}' (not protocol-prefixed like 'SnmpService'). "
                        + "Types in this file will NOT be loaded.",
                        typeResource.location(), itemElementName, root.getName(), itemElementName);
            }

            for (Object itemObj : items) {
                Element item = (Element) itemObj;
                T type = parseType(store, item, kind, manager);
                seenInXml.add(type.getName());
                try {
                    manager.register(type);
                    count++;
                } catch (IllegalArgumentException e) {
                    throw new AssetException("Duplicate type name '%s' in %s config; type names must be unique within a kind across all scan paths.",
                            type.getName(), kind);
                }
            }
        }

        log.info("Loaded {} {} types.", count, kind.name().toLowerCase());
        return count;
    }

    // ======================== XML type parsing ========================

    @SuppressWarnings("unchecked")
    private <T extends AssetType> T parseType(AssetStore store, Element item, AssetKind kind,
                                               AssetTypeManager<T> manager) {
        String name = getAttr(item, "Name");
        if (name.isEmpty()) {
            throw new AssetException("Anonymous type found in %s config; all types must have a Name attribute.", kind);
        }

        AssetType type = switch (kind) {
            case DEVICE -> new DeviceType(name);
            case SERVICE -> new ServiceType(name);
            case PROBE -> new ProbeType(name);
            case CONTROL -> new ControlType(name);
        };

        loadCommonInfo(store, type, item, kind);

        if (type instanceof MonitorType mt) {
            loadMonitorAttrs(store, item, mt);
        }

        return (T) type;
    }

    private void loadCommonInfo(AssetStore store, AssetType type, Element item, AssetKind kind) {
        // Caption
        String caption = getAttr(item, "Caption");
        type.setCaption(caption);

        // Inheritance: Super="ParentType" — copy properties from parent first
        loadSuperType(store, type, item, kind);

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
    private void loadSuperType(AssetStore store, AssetType type, Element item, AssetKind kind) {
        String superName = getAttr(item, "Super");
        if (superName.isEmpty()) return;

        AssetType superType = resolveType(store, superName, kind);
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

    private void loadMonitorAttrs(AssetStore store, Element item, MonitorType type) {
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
     *   <li>Types present in XML but not in DB → INSERT (version 1)</li>
     *   <li>Types present in both whose content (caption/driverClass/properties)
     *       actually changed → UPDATE with version+1; unchanged types are
     *       skipped so the version tracks real content changes only</li>
     *   <li>Types present in DB but not in XML → DELETE (obsolete)</li>
     * </ul>
     */
    private void syncDbConfigs(AssetStore store, Map<AssetKind, Set<String>> seenByKind) {
        List<AssetTypeConfigEntity> existing = typeConfigMapper.selectList(null);
        Map<String, AssetTypeConfigEntity> dbIndex = new HashMap<>();
        for (AssetTypeConfigEntity entity : existing) {
            dbIndex.put(dbKey(entity.getKind(), entity.getTypeName()), entity);
        }

        for (Map.Entry<AssetKind, Set<String>> entry : seenByKind.entrySet()) {
            AssetKind kind = entry.getKey();
            for (String typeName : entry.getValue()) {
                AssetType type = resolveType(store, typeName, kind);
                AssetTypeConfigEntity dbEntity = dbIndex.remove(dbKey(kind.name(), typeName));
                if (dbEntity == null) {
                    AssetTypeConfigEntity entity = new AssetTypeConfigEntity();
                    entity.setKind(kind.name());
                    entity.setTypeName(type.getName());
                    entity.setCaption(type.getCaption());
                    entity.setDriverClass(type.getRelatedClass());
                    entity.setProperties(serializeProperties(type.getProperties()));
                    entity.setVersion(INITIAL_VERSION);
                    typeConfigMapper.insert(entity);
                } else if (contentChanged(dbEntity, type)) {
                    dbEntity.setCaption(type.getCaption());
                    dbEntity.setDriverClass(type.getRelatedClass());
                    dbEntity.setProperties(serializeProperties(type.getProperties()));
                    dbEntity.setVersion(nextVersion(dbEntity.getVersion()));
                    typeConfigMapper.updateById(dbEntity);
                }
            }
        }

        // Delete obsolete configs (present in DB but not in any scan path)
        for (AssetTypeConfigEntity obsolete : dbIndex.values()) {
            typeConfigMapper.deleteById(obsolete.getId());
            log.info("Deleted obsolete type config: {}/{}", obsolete.getKind(), obsolete.getTypeName());
        }
    }

    private static String dbKey(String kind, String typeName) {
        return kind + "/" + typeName;
    }

    /** Version for an updated row; a NULL version (hand-edited row) counts as 0. */
    private static int nextVersion(Integer current) {
        return (current == null ? 0 : current) + 1;
    }

    private boolean contentChanged(AssetTypeConfigEntity dbEntity, AssetType type) {
        return !Objects.equals(dbEntity.getCaption(), type.getCaption())
                || !Objects.equals(dbEntity.getDriverClass(), type.getRelatedClass())
                || !Objects.equals(dbEntity.getProperties(), serializeProperties(type.getProperties()));
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

    private AssetType resolveType(AssetStore store, String name, AssetKind kind) {
        return switch (kind) {
            case DEVICE -> store.getDeviceTypes().find(name);
            case SERVICE -> store.getServiceTypes().find(name);
            case PROBE -> store.getProbeTypes().find(name);
            case CONTROL -> store.getControlTypes().find(name);
        };
    }

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
