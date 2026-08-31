package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.AssetType;
import com.systar.monitor.asset.type.MonitorType;
import com.systar.monitor.asset.type.Space;
import com.systar.monitor.server.MonitorServer;
import com.systar.server.controller.vo.AssetNodeVO;
import com.systar.server.controller.vo.AssetVO;
import com.systar.server.controller.vo.MonitorAssetNodeVO;
import com.systar.server.controller.vo.MonitorAssetVO;
import com.systar.server.dto.AssetCreateRequest;
import com.systar.server.dto.AssetUpdateRequest;
import com.systar.server.dto.BatchAssetRequest;
import com.systar.server.dto.BatchResult;
import com.systar.server.dto.TypePropertyVO;
import com.systar.server.repository.AssetRepository;
import com.systar.server.service.AssetOrchestrator;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitor")
public class AssetController {

    private final MonitorServer monitorServer;
    private final AssetStore assetStore;
    private final AssetOrchestrator orchestrator;
    private final AssetRepository repo;

    public AssetController(MonitorServer monitorServer,
                           AssetStore assetStore,
                           AssetOrchestrator orchestrator,
                           AssetRepository repo) {
        this.monitorServer = monitorServer;
        this.assetStore = assetStore;
        this.orchestrator = orchestrator;
        this.repo = repo;
    }

    // ======================== query ========================

    @RequirePermission("iot:asset:list")
    @GetMapping("/tree")
    public Result<AssetNodeVO> getAssetTree() {
        Space root = assetStore.getRoot();
        if (root == null) {
            return Result.success(null);
        }
        if (root.getId() == AssetStore.VIRTUAL_ROOT_ID && !root.children().isEmpty()) {
            Asset<?> firstChild = root.children().iterator().next();
            return Result.success(buildTreeNode(firstChild));
        }
        return Result.success(buildTreeNode(root));
    }

    @RequirePermission("iot:asset:list")
    @GetMapping("/assets")
    public Result<List<AssetVO>> getAssets(
            @RequestParam(required = false) String kind) {
        Collection<Asset<?>> assets;
        if (kind != null && !kind.isBlank()) {
            try {
                AssetKind assetKind = AssetKind.valueOf(kind.toUpperCase());
                assets = monitorServer.getAssetsByKind(assetKind);
            } catch (IllegalArgumentException e) {
                return Result.error("Invalid asset kind: " + kind);
            }
        } else {
            assets = monitorServer.getAssets();
        }
        List<AssetVO> result = assets.stream()
                .map(this::toAssetVO)
                .collect(Collectors.toList());
        return Result.success(result);
    }

    @RequirePermission("iot:asset:query")
    @GetMapping("/assets/{id}")
    public Result<AssetVO> getAsset(@PathVariable int id) {
        Asset<?> asset = monitorServer.findAsset(id);
        if (asset == null) {
            return Result.error(Result.CODE_NOT_FOUND, "Asset not found: " + id);
        }
        return Result.success(toAssetVO(asset));
    }

    // ======================== CRUD ========================

    @RequirePermission("iot:asset:add")
    @PostMapping("/assets")
    public Result<Integer> createAsset(@RequestBody AssetCreateRequest request) {
        try {
            int id = orchestrator.createAsset(request);
            return Result.success(id);
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }

    @RequirePermission("iot:asset:edit")
    @PutMapping("/assets/{id}")
    public Result<Void> updateAsset(@PathVariable int id,
                                     @RequestBody AssetUpdateRequest request) {
        try {
            AssetKind kind = resolveKindFromStore(id);
            if (kind == null) {
                return Result.error(Result.CODE_NOT_FOUND, "Asset not found: " + id);
            }
            orchestrator.updateAsset(id, kind, request);
            return Result.success();
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }

    @RequirePermission("iot:asset:delete")
    @DeleteMapping("/assets/{id}")
    public Result<Void> deleteAsset(@PathVariable int id) {
        try {
            AssetKind kind = resolveKindFromStore(id);
            if (kind == null) {
                return Result.error(Result.CODE_NOT_FOUND, "Asset not found: " + id);
            }
            orchestrator.deleteAsset(id, kind);
            return Result.success();
        } catch (AssetException e) {
            return Result.error(Result.CODE_CONFLICT, e.getMessage());
        }
    }

    // ======================== runtime operations ========================

    @RequirePermission("iot:asset:start")
    @PutMapping("/assets/{id}/start")
    public Result<Void> startAsset(@PathVariable int id) {
        try {
            AssetKind kind = resolveKindFromStore(id);
            if (kind == null) {
                return Result.error(Result.CODE_NOT_FOUND, "Asset not found: " + id);
            }
            orchestrator.startAsset(id, kind);
            return Result.success();
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }

    @RequirePermission("iot:asset:stop")
    @PutMapping("/assets/{id}/stop")
    public Result<Void> stopAsset(@PathVariable int id) {
        try {
            AssetKind kind = resolveKindFromStore(id);
            if (kind == null) {
                return Result.error(Result.CODE_NOT_FOUND, "Asset not found: " + id);
            }
            orchestrator.stopAsset(id, kind);
            return Result.success();
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Manual one-shot detection. Submits to the scheduler for async execution
     * and returns immediately. The result arrives via WebSocket push.
     */
    @RequirePermission("iot:asset:start")
    @PostMapping("/assets/{id}/detect")
    public Result<Map<String, Object>> detectAsset(@PathVariable int id) {
        try {
            AssetKind kind = resolveKindFromStore(id);
            if (kind == null) {
                return Result.error(Result.CODE_NOT_FOUND, "Asset not found: " + id);
            }
            monitorServer.detectImmediately(id);
            return Result.success(Map.of("status", "accepted"));
        } catch (IllegalStateException e) {
            return Result.error(Result.CODE_CONFLICT, e.getMessage());
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }

    @RequirePermission("iot:asset:disable")
    @PutMapping("/assets/{id}/disable")
    public Result<Void> disableAsset(@PathVariable int id) {
        try {
            AssetKind kind = resolveKindFromStore(id);
            if (kind == null) {
                return Result.error(Result.CODE_NOT_FOUND, "Asset not found: " + id);
            }
            orchestrator.disableAsset(id, kind);
            return Result.success();
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }

    @RequirePermission("iot:asset:enable")
    @PutMapping("/assets/{id}/enable")
    public Result<Void> enableAsset(@PathVariable int id) {
        try {
            AssetKind kind = resolveKindFromStore(id);
            if (kind == null) {
                return Result.error(Result.CODE_NOT_FOUND, "Asset not found: " + id);
            }
            orchestrator.enableAsset(id, kind);
            return Result.success();
        } catch (AssetException e) {
            return Result.error(Result.CODE_BAD_REQUEST, e.getMessage());
        }
    }

    // ======================== batch operations ========================

    @RequirePermission("iot:asset:start")
    @PutMapping("/assets/batch/start")
    public Result<BatchResult> batchStart(@RequestBody BatchAssetRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return Result.error(Result.CODE_BAD_REQUEST, "ids must not be empty");
        }
        return Result.success(orchestrator.batchStart(request.ids()));
    }

    @RequirePermission("iot:asset:stop")
    @PutMapping("/assets/batch/stop")
    public Result<BatchResult> batchStop(@RequestBody BatchAssetRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return Result.error(Result.CODE_BAD_REQUEST, "ids must not be empty");
        }
        return Result.success(orchestrator.batchStop(request.ids()));
    }

    @RequirePermission("iot:asset:enable")
    @PutMapping("/assets/batch/enable")
    public Result<BatchResult> batchEnable(@RequestBody BatchAssetRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return Result.error(Result.CODE_BAD_REQUEST, "ids must not be empty");
        }
        return Result.success(orchestrator.batchEnable(request.ids()));
    }

    @RequirePermission("iot:asset:disable")
    @PutMapping("/assets/batch/disable")
    public Result<BatchResult> batchDisable(@RequestBody BatchAssetRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return Result.error(Result.CODE_BAD_REQUEST, "ids must not be empty");
        }
        return Result.success(orchestrator.batchDisable(request.ids()));
    }

    @RequirePermission("iot:asset:delete")
    @DeleteMapping("/assets/batch")
    public Result<BatchResult> batchDelete(@RequestBody BatchAssetRequest request) {
        if (request.ids() == null || request.ids().isEmpty()) {
            return Result.error(Result.CODE_BAD_REQUEST, "ids must not be empty");
        }
        return Result.success(orchestrator.batchDelete(request.ids()));
    }

    // ======================== type definitions ========================

    @RequirePermission("iot:asset:list")
    @GetMapping("/asset-types")
    public Result<Map<String, List<String>>> getAssetTypes() {
        return Result.success(Map.of(
                "SPACE", typeNames(assetStore.getSpaceTypes()),
                "DEVICE", typeNames(assetStore.getDeviceTypes()),
                "SERVICE", typeNames(assetStore.getServiceTypes()),
                "PROBE", typeNames(assetStore.getProbeTypes()),
                "CONTROL", typeNames(assetStore.getControlTypes())
        ));
    }

    @RequirePermission("iot:asset:list")
    @GetMapping("/asset-types/{kind}")
    public Result<List<String>> getAssetTypesByKind(@PathVariable String kind) {
        try {
            AssetKind assetKind = AssetKind.valueOf(kind.toUpperCase());
            List<String> names = switch (assetKind) {
                case SPACE -> typeNames(assetStore.getSpaceTypes());
                case DEVICE -> typeNames(assetStore.getDeviceTypes());
                case SERVICE -> typeNames(assetStore.getServiceTypes());
                case PROBE -> typeNames(assetStore.getProbeTypes());
                case CONTROL -> typeNames(assetStore.getControlTypes());
            };
            return Result.success(names);
        } catch (IllegalArgumentException e) {
            return Result.error("Invalid asset kind: " + kind);
        }
    }

    @RequirePermission("iot:asset:list")
    @GetMapping("/asset-types/{kind}/{typeName}")
    public Result<List<TypePropertyVO>> getTypeProperties(
            @PathVariable String kind, @PathVariable String typeName) {
        try {
            AssetKind assetKind = AssetKind.valueOf(kind.toUpperCase());
            var manager = switch (assetKind) {
                case SPACE -> assetStore.getSpaceTypes();
                case DEVICE -> assetStore.getDeviceTypes();
                case SERVICE -> assetStore.getServiceTypes();
                case PROBE -> assetStore.getProbeTypes();
                case CONTROL -> assetStore.getControlTypes();
            };
            var type = manager.find(typeName);
            if (type == null) {
                return Result.error(Result.CODE_NOT_FOUND,
                        "Type not found: " + typeName + " for kind " + kind);
            }
            var props = type.getProperties();
            if (props == null || props.isEmpty()) return Result.success(List.of());
            List<TypePropertyVO> result = new ArrayList<>();
            for (var p : props) {
                result.add(new TypePropertyVO(
                        p.getName(),
                        p.getDescription() != null ? p.getDescription() : p.getName(),
                        p.getDataType() != null ? p.getDataType().name() : "STRING",
                        p.getViewType() != null ? p.getViewType().name() : null,
                        p.getDefaultValue(),
                        p.isRequired(),
                        p.getMin(),
                        p.getMax(),
                        p.getMaxLength()
                ));
            }
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error("Invalid asset kind: " + kind);
        }
    }

    // ======================== private helpers ========================

    private AssetKind resolveKindFromStore(int id) {
        Asset<?> asset = monitorServer.findAsset(id);
        return asset != null ? asset.getKind() : null;
    }

    private List<String> typeNames(com.systar.monitor.asset.type.AssetTypeManager<?> manager) {
        return manager.getAll().stream()
                .map(com.systar.monitor.asset.type.AssetType::getName)
                .collect(Collectors.toList());
    }

    private AssetNodeVO buildTreeNode(Asset<?> asset) {
        AssetNodeVO node = asset instanceof Monitor<?>
                ? new MonitorAssetNodeVO()
                : new AssetNodeVO();

        node.setId(asset.getId());
        node.setName(asset.getName());
        node.setCaption(asset.getCaption());
        node.setKind(asset.getKind().name());
        node.setState(asset.getState().name());
        node.setStateCaption(asset.getState().getCaption());
        if (asset.getType() != null) {
            node.setTypeName(asset.getType().getName());
            node.setTypeCaption(asset.getType().getCaption());
        }
        node.setEnabled(asset.isEnabled());

        AssetType at = asset.getType();
        if (at instanceof MonitorType mt && node instanceof MonitorAssetNodeVO mNode) {
            if (mt.getDataType() != null) mNode.setDataType(mt.getDataType().name());
            if (mt.getViewType() != null) mNode.setViewType(mt.getViewType().name());
        }

        if (asset instanceof CompoundAsset<?> compound) {
            List<AssetNodeVO> children = new java.util.ArrayList<>();
            for (Asset<?> child : compound.children()) {
                children.add(buildTreeNode(child));
            }
            node.setChildren(children);
        }
        return node;
    }

    private AssetVO toAssetVO(Asset<?> asset) {
        if (asset instanceof Monitor<?> m) {
            return toMonitorAssetVO(m);
        }
        AssetVO vo = new AssetVO();
        fillBaseFields(vo, asset);
        return vo;
    }

    private MonitorAssetVO toMonitorAssetVO(Monitor<?> monitor) {
        MonitorAssetVO vo = new MonitorAssetVO();
        fillBaseFields(vo, monitor);

        vo.setValue(monitor.getValue());
        vo.setLastDetectTime(monitor.getLastDetectTimeMs());
        vo.setMode(monitor.getMode() != null ? monitor.getMode().name() : null);
        vo.setDetecting(monitor.isDetecting());
        vo.setRuntimeDesc(monitor.getRuntimeDesc());
        vo.setDetectTimeoutMs(monitor.getDetectTimeoutMs());

        String unit = monitor.getMetadata("unit");
        if (unit != null) vo.setUnit(unit);
        Object minVal = monitor.getMetadata("minValue");
        if (minVal instanceof Number n) vo.setMinValue(n.floatValue());
        Object maxVal = monitor.getMetadata("maxValue");
        if (maxVal instanceof Number n) vo.setMaxValue(n.floatValue());

        AssetType type = monitor.getType();
        if (type instanceof MonitorType mt) {
            if (mt.getDataType() != null) vo.setDataType(mt.getDataType().name());
            if (mt.getViewType() != null) vo.setViewType(mt.getViewType().name());
        }

        if (monitor instanceof VirtualProbe vp) {
            vo.setIsVirtual(true);
            if (vp.getType() instanceof com.systar.monitor.asset.type.VirtualProbeType vpt) {
                vo.setExpression(vpt.getExpression());
            }
            vo.setDependsOn(vp.getDependsOn().isEmpty() ? null
                    : vp.getDependsOn().stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(",")));
        }

        return vo;
    }

    private void fillBaseFields(AssetVO vo, Asset<?> asset) {
        vo.setId(asset.getId());
        vo.setName(asset.getName());
        vo.setCaption(asset.getCaption());
        vo.setKind(asset.getKind().name());
        vo.setState(asset.getState().name());
        vo.setStateCaption(asset.getState().getCaption());
        vo.setParentId(asset.getParentId());
        vo.setEnabled(asset.isEnabled());
        vo.setPath(assetStore.getFullPath(asset));

        if (asset.getType() != null) {
            vo.setTypeName(asset.getType().getName());
            vo.setTypeCaption(asset.getType().getCaption());
        }

        Map<String, String> attrs = repo.findAttributes(asset.getId());
        if (!attrs.isEmpty()) {
            vo.setAttributes(attrs);
        }
    }
}
