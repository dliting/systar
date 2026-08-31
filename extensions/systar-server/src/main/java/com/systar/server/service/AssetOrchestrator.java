package com.systar.server.service;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.server.dto.AssetCreateRequest;
import com.systar.server.dto.AssetUpdateRequest;
import com.systar.server.dto.BatchResult;
import com.systar.server.event.AssetChangedEvent;
import com.systar.server.event.AssetChangedEvent.Action;
import com.systar.server.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AssetOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AssetOrchestrator.class);

    private final AssetRepository repo;
    private final AssetStore assetStore;
    private final ApplicationEventPublisher eventPublisher;

    public AssetOrchestrator(AssetRepository repo,
                             AssetStore assetStore,
                             ApplicationEventPublisher eventPublisher) {
        this.repo = repo;
        this.assetStore = assetStore;
        this.eventPublisher = eventPublisher;
    }

    // ======================== create ========================

    @Transactional
    public int createAsset(AssetCreateRequest req) {
        AssetKind kind = parseKind(req.kind());
        validateTypeName(kind, req.typeName());
        validateParentExists(req.parentId());

        int newId = repo.nextId(kind);

        Asset<?> asset = switch (kind) {
            case SPACE -> createSpace(newId, req);
            case DEVICE -> createDevice(newId, req);
            case SERVICE -> createService(newId, req);
            case PROBE -> createProbe(newId, req);
            case CONTROL -> createControl(newId, req);
        };

        repo.saveAttributes(newId, req.attributes());
        eventPublisher.publishEvent(new AssetChangedEvent(Action.CREATED, newId, kind, asset));
        log.info("Created asset: kind={} id={} name={}", kind, newId, req.name());
        return newId;
    }

    private Asset<?> createSpace(int newId, AssetCreateRequest req) {
        var p = req.properties();
        var row = new AssetRepository.SpaceRow(newId, req.name(), req.caption(), req.parentId(),
                getIntProp(p, "area"),
                getIntProp(p, "sequence", 0),
                getIntProp(p, "showInClient", 1),
                req.typeName());
        repo.insertSpace(row);
        repo.insertAssetView(req.name(), req.caption(), AssetKind.SPACE, req.parentId(), newId);
        return repo.findSpaceById(newId);
    }

    private Asset<?> createDevice(int newId, AssetCreateRequest req) {
        var p = req.properties();
        var row = new AssetRepository.DeviceRow(newId, req.name(), req.caption(), req.parentId(),
                getShortProp(p, "catalog"), getStrProp(p, "vendor"),
                null, null, getFloatProp(p, "healthIndex"),
                getStrProp(p, "model"), getStrProp(p, "serialNumber"),
                null, getStrProp(p, "lifecycleStatus"),
                getStrProp(p, "responsiblePerson"), getStrProp(p, "department"),
                getStrProp(p, "supplierContact"), getIntProp(p, "maintenanceCycle"),
                null, getStrProp(p, "remark"), req.typeName());
        repo.insertDevice(row);
        repo.insertAssetView(req.name(), req.caption(), AssetKind.DEVICE, req.parentId(), newId);
        return repo.findDeviceById(newId);
    }

    private Asset<?> createService(int newId, AssetCreateRequest req) {
        var p = req.properties();
        var row = new AssetRepository.ServiceRow(newId, req.name(), req.caption(), req.parentId(),
                extractMode(p), getStrProp(p, "driverClass"),
                getIntProp(p, "maxConnections"), req.typeName());
        repo.insertService(row);
        repo.insertAssetView(req.name(), req.caption(), AssetKind.SERVICE, req.parentId(), newId);
        return repo.findServiceById(newId);
    }

    private Asset<?> createProbe(int newId, AssetCreateRequest req) {
        var p = req.properties();
        validateVirtualProbe(p);
        var row = new AssetRepository.ProbeRow(newId, req.name(), req.caption(), req.parentId(),
                getIntProp(p, "serviceId"), getStrProp(p, "unit"),
                getStrProp(p, "detectInterval"), getStrProp(p, "savingInterval"),
                getStrProp(p, "warnCondition"), getStrProp(p, "transform"),
                getShortProp(p, "catalog"), extractDataTypeStr(p),
                getFloatProp(p, "minValue"), getFloatProp(p, "maxValue"), req.typeName(),
                getIntProp(p, "isVirtual"), getStrProp(p, "expression"), getStrProp(p, "dependsOn"));
        repo.insertProbe(row);
        repo.insertAssetView(req.name(), req.caption(), AssetKind.PROBE, req.parentId(), newId);
        return repo.findProbeById(newId);
    }

    private Asset<?> createControl(int newId, AssetCreateRequest req) {
        var p = req.properties();
        var row = new AssetRepository.ControlRow(newId, req.name(), req.caption(), req.parentId(),
                getIntProp(p, "serviceId"), getStrProp(p, "unit"),
                getStrProp(p, "detectInterval"), getStrProp(p, "savingInterval"),
                getStrProp(p, "warnCondition"), getStrProp(p, "transform"),
                getShortProp(p, "catalog"), getIntProp(p, "refreshDelay"),
                getFloatProp(p, "minValue"), getFloatProp(p, "maxValue"), req.typeName());
        repo.insertControl(row);
        repo.insertAssetView(req.name(), req.caption(), AssetKind.CONTROL, req.parentId(), newId);
        return repo.findControlById(newId);
    }

    // ======================== update ========================

    @Transactional
    public void updateAsset(int id, AssetKind kind, AssetUpdateRequest req) {
        validateTypeName(kind, req.typeName());

        Asset<?> asset = switch (kind) {
            case SPACE -> updateSpace(id, req);
            case DEVICE -> updateDevice(id, req);
            case SERVICE -> updateService(id, req);
            case PROBE -> updateProbe(id, req);
            case CONTROL -> updateControl(id, req);
        };

        if (req.attributes() != null) {
            repo.deleteAttributes(id);
            repo.saveAttributes(id, req.attributes());
        }

        eventPublisher.publishEvent(new AssetChangedEvent(Action.UPDATED, id, kind, asset));
        log.info("Updated asset: kind={} id={}", kind, id);
    }

    private Asset<?> updateSpace(int id, AssetUpdateRequest req) {
        var p = req.properties();
        var fields = new AssetRepository.SpaceUpdateFields(
                req.name(), req.caption(), null,
                getIntProp(p, "area"), getIntProp(p, "sequence"),
                getIntProp(p, "showInClient"), req.typeName());
        repo.updateSpace(id, fields);
        Asset<?> asset = repo.findSpaceById(id);
        repo.updateAssetView(id, AssetKind.SPACE, asset.getName(), asset.getCaption());
        return asset;
    }

    private Asset<?> updateDevice(int id, AssetUpdateRequest req) {
        var p = req.properties();
        var fields = new AssetRepository.DeviceUpdateFields(
                req.name(), req.caption(), null, getShortProp(p, "catalog"),
                getStrProp(p, "vendor"), null, null, getFloatProp(p, "healthIndex"),
                getStrProp(p, "model"), getStrProp(p, "serialNumber"), null,
                getStrProp(p, "lifecycleStatus"), getStrProp(p, "responsiblePerson"),
                getStrProp(p, "department"), getStrProp(p, "supplierContact"),
                getIntProp(p, "maintenanceCycle"), null, getStrProp(p, "remark"),
                req.typeName());
        repo.updateDevice(id, fields);
        Asset<?> asset = repo.findDeviceById(id);
        repo.updateAssetView(id, AssetKind.DEVICE, asset.getName(), asset.getCaption());
        return asset;
    }

    private Asset<?> updateService(int id, AssetUpdateRequest req) {
        var p = req.properties();
        var fields = new AssetRepository.ServiceUpdateFields(
                req.name(), req.caption(), null, extractMode(p),
                getStrProp(p, "driverClass"), getIntProp(p, "maxConnections"),
                req.typeName());
        repo.updateService(id, fields);
        Asset<?> asset = repo.findServiceById(id);
        repo.updateAssetView(id, AssetKind.SERVICE, asset.getName(), asset.getCaption());
        return asset;
    }

    private Asset<?> updateProbe(int id, AssetUpdateRequest req) {
        var p = req.properties();
        validateVirtualProbe(p);
        var fields = new AssetRepository.ProbeUpdateFields(
                req.name(), req.caption(), null, getIntProp(p, "serviceId"),
                getStrProp(p, "unit"), getStrProp(p, "detectInterval"),
                getStrProp(p, "savingInterval"), getStrProp(p, "warnCondition"),
                getStrProp(p, "transform"), getShortProp(p, "catalog"),
                extractDataTypeStr(p), getFloatProp(p, "minValue"),
                getFloatProp(p, "maxValue"), req.typeName(),
                getIntProp(p, "isVirtual"), getStrProp(p, "expression"), getStrProp(p, "dependsOn"));
        repo.updateProbe(id, fields);
        Asset<?> asset = repo.findProbeById(id);
        repo.updateAssetView(id, AssetKind.PROBE, asset.getName(), asset.getCaption());
        return asset;
    }

    private Asset<?> updateControl(int id, AssetUpdateRequest req) {
        var p = req.properties();
        var fields = new AssetRepository.ControlUpdateFields(
                req.name(), req.caption(), null, getIntProp(p, "serviceId"),
                getStrProp(p, "unit"), getStrProp(p, "detectInterval"),
                getStrProp(p, "savingInterval"), getStrProp(p, "warnCondition"),
                getStrProp(p, "transform"), getShortProp(p, "catalog"),
                getIntProp(p, "refreshDelay"), getFloatProp(p, "minValue"),
                getFloatProp(p, "maxValue"), req.typeName());
        repo.updateControl(id, fields);
        Asset<?> asset = repo.findControlById(id);
        repo.updateAssetView(id, AssetKind.CONTROL, asset.getName(), asset.getCaption());
        return asset;
    }

    // ======================== delete ========================

    @Transactional
    public void deleteAsset(int id, AssetKind kind) {
        validateDeleteConstraints(id, kind);

        switch (kind) {
            case SPACE -> repo.deleteSpace(id);
            case DEVICE -> repo.deleteDevice(id);
            case SERVICE -> repo.deleteService(id);
            case PROBE -> repo.deleteProbe(id);
            case CONTROL -> repo.deleteControl(id);
        }

        eventPublisher.publishEvent(new AssetChangedEvent(Action.DELETED, id, kind, null));
        log.info("Deleted asset: kind={} id={}", kind, id);
    }

    // ======================== enable / disable ========================

    @Transactional
    public void enableAsset(int id, AssetKind kind) {
        repo.setEnabled(kind, id, true);
        eventPublisher.publishEvent(new AssetChangedEvent(Action.ENABLED, id, kind, null));
    }

    @Transactional
    public void disableAsset(int id, AssetKind kind) {
        repo.setEnabled(kind, id, false);
        eventPublisher.publishEvent(new AssetChangedEvent(Action.DISABLED, id, kind, null));
    }

    // ======================== runtime start / stop ========================

    public void startAsset(int id, AssetKind kind) {
        validateIsMonitor(id, "start");
        eventPublisher.publishEvent(new AssetChangedEvent(Action.STARTED, id, kind, null));
    }

    public void stopAsset(int id, AssetKind kind) {
        validateIsMonitor(id, "stop");
        eventPublisher.publishEvent(new AssetChangedEvent(Action.STOPPED, id, kind, null));
    }

    // ======================== batch operations ========================

    @Transactional
    public BatchResult batchDelete(List<Integer> ids) {
        BatchResult result = new BatchResult();
        for (int id : ids) {
            try {
                AssetKind kind = resolveKind(id);
                if (kind == null) {
                    result.addFailure(id, "Asset not found");
                    continue;
                }
                deleteAsset(id, kind);
                result.addSuccess(id);
            } catch (AssetException e) {
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }

    public BatchResult batchStart(List<Integer> ids) {
        BatchResult result = new BatchResult();
        for (int id : ids) {
            try {
                AssetKind kind = resolveKind(id);
                if (kind == null) {
                    result.addFailure(id, "Asset not found");
                    continue;
                }
                startAsset(id, kind);
                result.addSuccess(id);
            } catch (AssetException e) {
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }

    public BatchResult batchStop(List<Integer> ids) {
        BatchResult result = new BatchResult();
        for (int id : ids) {
            try {
                AssetKind kind = resolveKind(id);
                if (kind == null) {
                    result.addFailure(id, "Asset not found");
                    continue;
                }
                stopAsset(id, kind);
                result.addSuccess(id);
            } catch (AssetException e) {
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }

    @Transactional
    public BatchResult batchEnable(List<Integer> ids) {
        BatchResult result = new BatchResult();
        for (int id : ids) {
            try {
                AssetKind kind = resolveKind(id);
                if (kind == null) {
                    result.addFailure(id, "Asset not found");
                    continue;
                }
                enableAsset(id, kind);
                result.addSuccess(id);
            } catch (AssetException e) {
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }

    @Transactional
    public BatchResult batchDisable(List<Integer> ids) {
        BatchResult result = new BatchResult();
        for (int id : ids) {
            try {
                AssetKind kind = resolveKind(id);
                if (kind == null) {
                    result.addFailure(id, "Asset not found");
                    continue;
                }
                disableAsset(id, kind);
                result.addSuccess(id);
            } catch (AssetException e) {
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }

    private AssetKind resolveKind(int id) {
        Asset<?> asset = assetStore.findAsset(id);
        return asset != null ? asset.getKind() : null;
    }

    // ======================== validation ========================

    private AssetKind parseKind(String kind) {
        try {
            return AssetKind.valueOf(kind.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AssetException("Invalid asset kind: " + kind);
        }
    }

    private void validateTypeName(AssetKind kind, String typeName) {
        if (typeName == null || typeName.isBlank()) return;
        AssetTypeManager<?> manager = switch (kind) {
            case SPACE -> assetStore.getSpaceTypes();
            case DEVICE -> assetStore.getDeviceTypes();
            case SERVICE -> assetStore.getServiceTypes();
            case PROBE -> assetStore.getProbeTypes();
            case CONTROL -> assetStore.getControlTypes();
        };
        if (manager.find(typeName) == null) {
            throw new AssetException(String.format(
                    "Unknown type '%s' for kind %s. Registered types: %s",
                    typeName, kind, manager.getAll().stream().map(AssetType::getName).toList()));
        }
    }

    private void validateParentExists(int parentId) {
        if (parentId <= 0) return;
        if (assetStore.findAsset(parentId) == null) {
            throw new AssetException("Parent asset not found: " + parentId);
        }
    }

    private void validateIsMonitor(int id, String action) {
        Asset<?> asset = assetStore.findAsset(id);
        if (asset == null) {
            throw new AssetException("Asset %d not found; cannot %s.".formatted(id, action));
        }
        if (!(asset instanceof Monitor<?>)) {
            throw new AssetException("Asset %d is not a monitor; cannot %s.".formatted(id, action));
        }
    }

    private void validateDeleteConstraints(int id, AssetKind kind) {
        Asset<?> asset = assetStore.findAsset(id);
        if (asset instanceof CompoundAsset<?> compound && compound.children().iterator().hasNext()) {
            throw new AssetException("Cannot delete asset with children. Remove or move children first.");
        }

        long alarmCount = repo.countAlarmRulesForMonitor(id);
        if (alarmCount > 0) {
            throw new AssetException(String.format(
                    "Cannot delete asset: %d alarm rule(s) are associated. Remove them first.", alarmCount));
        }

        long linkageCauseCount = repo.countLinkageCausesForMonitor(id);
        if (linkageCauseCount > 0) {
            throw new AssetException(String.format(
                    "Cannot delete asset: %d linkage cause rule(s) are associated. Remove them first.", linkageCauseCount));
        }

        long linkageEffectCount = repo.countLinkageEffectsForMonitor(id);
        if (linkageEffectCount > 0) {
            throw new AssetException(String.format(
                    "Cannot delete asset: %d linkage effect rule(s) are associated. Remove them first.", linkageEffectCount));
        }
    }

    // ======================== property helpers ========================

    private static Integer getIntProp(Map<String, Object> props, String key) {
        if (props == null) return null;
        Object val = props.get(key);
        return val instanceof Number n ? n.intValue() : null;
    }

    private static int getIntProp(Map<String, Object> props, String key, int defaultValue) {
        Integer val = getIntProp(props, key);
        return val != null ? val : defaultValue;
    }

    private static Short getShortProp(Map<String, Object> props, String key) {
        if (props == null) return null;
        Object val = props.get(key);
        return val instanceof Number n ? n.shortValue() : null;
    }

    private static Float getFloatProp(Map<String, Object> props, String key) {
        if (props == null) return null;
        Object val = props.get(key);
        return val instanceof Number n ? n.floatValue() : null;
    }

    private static String getStrProp(Map<String, Object> props, String key) {
        if (props == null) return null;
        Object val = props.get(key);
        return val instanceof String s ? s : null;
    }

    private static String extractDataTypeStr(Map<String, Object> props) {
        if (props == null) return null;
        Object val = props.get("dataType");
        return val instanceof String s ? s.toUpperCase() : null;
    }

    private static Integer extractMode(Map<String, Object> props) {
        if (props == null) return null;
        Object val = props.get("mode");
        if (val instanceof String s) return MonitorMode.valueOf(s.toUpperCase()).getCode();
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    private void validateVirtualProbe(Map<String, Object> properties) {
        if (properties == null) return;
        Integer isVirtual = getIntProp(properties, "isVirtual");
        if (isVirtual == null || isVirtual != 1) return;
        String expression = getStrProp(properties, "expression");
        if (expression == null || expression.isBlank()) {
            throw new AssetException("Virtual probe requires a non-blank expression");
        }
        String dependsOn = getStrProp(properties, "dependsOn");
        if (dependsOn == null || dependsOn.isBlank()) {
            throw new AssetException("Virtual probe requires at least one dependency probe ID");
        }
        for (String segment : dependsOn.split(",")) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                int depId;
                try {
                    depId = Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    throw new AssetException("Invalid probe ID in dependsOn: '" + trimmed + "'");
                }
                Asset<?> dep = assetStore.findAsset(depId);
                if (dep == null) {
                    throw new AssetException("Dependency probe not found: id=" + depId);
                }
                if (!(dep instanceof Probe)) {
                    throw new AssetException("Dependency must be a probe, but id=" + depId + " is a " + dep.getKind());
                }
            }
        }
    }
}
