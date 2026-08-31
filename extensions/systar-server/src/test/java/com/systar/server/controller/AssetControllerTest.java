package com.systar.server.controller;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import com.systar.monitor.server.MonitorServer;
import com.systar.common.api.Result;
import com.systar.server.controller.vo.AssetNodeVO;
import com.systar.server.controller.vo.AssetVO;
import com.systar.server.controller.vo.MonitorAssetNodeVO;
import com.systar.server.controller.vo.MonitorAssetVO;
import com.systar.server.dto.BatchAssetRequest;
import com.systar.server.dto.BatchResult;
import com.systar.server.repository.AssetRepository;
import com.systar.server.service.AssetOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetControllerTest {

    private AssetController controller;
    private MonitorServer monitorServer;
    private AssetStore assetStore;
    private AssetOrchestrator orchestrator;
    private AssetRepository repo;

    @BeforeEach
    void setUp() {
        monitorServer = mock(MonitorServer.class);
        assetStore = new AssetStore();
        orchestrator = mock(AssetOrchestrator.class);
        repo = mock(AssetRepository.class);
        controller = new AssetController(monitorServer, assetStore, orchestrator, repo);
    }

    @SuppressWarnings("unchecked")
    private void stubFindAsset(int id, Asset<?> asset) {
        doReturn(asset).when(monitorServer).findAsset(id);
    }

    @Nested
    @DisplayName("GET /api/monitor/tree")
    class GetAssetTree {

        @Test
        @DisplayName("returns virtual root when no spaces loaded")
        void noRoot() {
            // AssetStore self-initializes virtual root anchor (id=-1, empty name).
            // With no children loaded, the virtual root is returned as-is.
            Result<AssetNodeVO> result = controller.getAssetTree();
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isNotNull();
            assertThat(result.getData().getId()).isEqualTo(AssetStore.VIRTUAL_ROOT_ID);
        }

        @Test
        @DisplayName("returns tree with root node")
        void withRoot() {
            assetStore.createRoot(new SpaceType("root"), "root-space");
            Result<AssetNodeVO> result = controller.getAssetTree();
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isNotNull();
            assertThat(result.getData().getName()).isEqualTo("root-space");
            assertThat(result.getData().getKind()).isEqualTo("SPACE");
            assertThat(result.getData().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("tree node includes stateCaption, typeName, typeCaption")
        void nodeIncludesCaptions() {
            SpaceType type = new SpaceType("Building");
            type.setCaption("建筑");
            Space space = new Space();
            space.init(type, 10, "bld-a");
            assetStore.addAsset(space);

            Result<AssetNodeVO> result = controller.getAssetTree();
            AssetNodeVO node = findNode(result.getData(), 10);
            assertThat(node).isNotNull();
            assertThat(node.getStateCaption()).isEqualTo("正常");
            assertThat(node.getTypeName()).isEqualTo("Building");
            assertThat(node.getTypeCaption()).isEqualTo("建筑");
        }

        @Test
        @DisplayName("tree node for Probe returns MonitorAssetNodeVO with dataType/viewType")
        void probeTreeNodeReturnsMonitorNode() {
            ProbeType pt = new ProbeType("SimulateFloat");
            pt.setDataType(com.systar.monitor.asset.type.DataType.FLOAT);
            pt.setViewType(com.systar.monitor.asset.type.ViewType.SLIDER);
            Probe probe = new Probe() {
                @Override public void detect(com.systar.monitor.result.IMonitorResult r) {}
            };
            probe.init(pt, 20, "probe-temp");
            assetStore.addAsset(probe);

            Result<AssetNodeVO> result = controller.getAssetTree();
            AssetNodeVO node = findNode(result.getData(), 20);
            assertThat(node).isNotNull();
            assertThat(node).isInstanceOf(MonitorAssetNodeVO.class);
            MonitorAssetNodeVO mNode = (MonitorAssetNodeVO) node;
            assertThat(mNode.getDataType()).isEqualTo("FLOAT");
            assertThat(mNode.getViewType()).isEqualTo("SLIDER");
        }

        @Test
        @DisplayName("tree node for Space returns plain AssetNodeVO without Monitor fields")
        void spaceTreeNodeReturnsPlainNode() {
            Space space = new Space();
            space.init(new SpaceType("Room"), 30, "room-1");
            assetStore.addAsset(space);

            Result<AssetNodeVO> result = controller.getAssetTree();
            AssetNodeVO node = findNode(result.getData(), 30);
            assertThat(node).isNotNull();
            assertThat(node).isNotInstanceOf(MonitorAssetNodeVO.class);
        }

        @Test
        @DisplayName("tree node for Control returns MonitorAssetNodeVO with dataType/viewType")
        void controlTreeNodeReturnsMonitorNode() {
            ControlType ct = new ControlType("SwitchControl");
            ct.setDataType(com.systar.monitor.asset.type.DataType.BOOLEAN);
            ct.setViewType(com.systar.monitor.asset.type.ViewType.YESNO);
            Control control = new Control() {
                @Override public void execute(String command) {}
            };
            control.init(ct, 40, "ctrl-switch");
            assetStore.addAsset(control);

            Result<AssetNodeVO> result = controller.getAssetTree();
            AssetNodeVO node = findNode(result.getData(), 40);
            assertThat(node).isNotNull();
            assertThat(node).isInstanceOf(MonitorAssetNodeVO.class);
            MonitorAssetNodeVO mNode = (MonitorAssetNodeVO) node;
            assertThat(mNode.getDataType()).isEqualTo("BOOLEAN");
            assertThat(mNode.getViewType()).isEqualTo("YESNO");
        }

        private AssetNodeVO findNode(AssetNodeVO root, int id) {
            if (root.getId() == id) return root;
            if (root.getChildren() == null) return null;
            for (AssetNodeVO child : root.getChildren()) {
                AssetNodeVO found = findNode(child, id);
                if (found != null) return found;
            }
            return null;
        }
    }

    @Nested
    @DisplayName("GET /api/monitor/assets")
    class GetAssets {

        @Test
        @DisplayName("returns all assets when no kind filter")
        void noKindFilter() {
            when(monitorServer.getAssets()).thenReturn(List.of());
            Result<List<AssetVO>> result = controller.getAssets(null);
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isEmpty();
        }

        @Test
        @DisplayName("filters by valid kind")
        void validKindFilter() {
            Probe probe = new Probe() {
                @Override public void detect(com.systar.monitor.result.IMonitorResult r) {}
            };
            probe.init(new ProbeType("test"), 1, "probe-1");
            when(monitorServer.getAssetsByKind(AssetKind.PROBE)).thenReturn(List.of(probe));
            Result<List<AssetVO>> result = controller.getAssets("PROBE");
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getKind()).isEqualTo("PROBE");
        }

        @Test
        @DisplayName("returns error for invalid kind")
        void invalidKind() {
            Result<List<AssetVO>> result = controller.getAssets("INVALID_KIND");
            assertThat(result.getCode()).isEqualTo(1);
            assertThat(result.getMessage()).contains("Invalid asset kind");
        }

        @Test
        @DisplayName("blank kind returns all assets")
        void blankKindReturnsAll() {
            when(monitorServer.getAssets()).thenReturn(List.of());
            Result<List<AssetVO>> result = controller.getAssets("   ");
            assertThat(result.getCode()).isEqualTo(0);
            verify(monitorServer).getAssets();
        }
    }

    @Nested
    @DisplayName("GET /api/monitor/assets/{id}")
    class GetAsset {

        @Test
        @DisplayName("returns not-found when asset not found")
        void assetNotFound() {
            stubFindAsset(999, null);
            Result<AssetVO> result = controller.getAsset(999);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
            assertThat(result.getMessage()).contains("Asset not found");
        }

        @Test
        @DisplayName("returns asset when found")
        void assetFound() {
            Probe probe = new Probe() {
                @Override public void detect(com.systar.monitor.result.IMonitorResult r) {}
            };
            probe.init(new ProbeType("test"), 1, "probe-1");
            stubFindAsset(1, probe);
            Result<AssetVO> result = controller.getAsset(1);
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isNotNull();
            assertThat(result.getData().getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns stateCaption in Chinese")
        void stateCaptionPopulated() {
            Probe probe = new Probe() {
                @Override public void detect(com.systar.monitor.result.IMonitorResult r) {}
            };
            probe.init(new ProbeType("test"), 1, "probe-1");
            stubFindAsset(1, probe);
            Result<AssetVO> result = controller.getAsset(1);
            assertThat(result.getData().getStateCaption()).isEqualTo("正常");
        }

        @Test
        @DisplayName("returns MonitorAssetVO for Probe")
        void probeReturnsMonitorAssetVO() {
            Probe probe = new Probe() {
                @Override public void detect(com.systar.monitor.result.IMonitorResult r) {}
            };
            ProbeType pt = new ProbeType("SimulateFloat");
            pt.setDataType(com.systar.monitor.asset.type.DataType.FLOAT);
            pt.setViewType(com.systar.monitor.asset.type.ViewType.SLIDER);
            probe.init(pt, 1, "probe-1");
            probe.setMetadata("unit", "℃");
            probe.setMetadata("minValue", 0.0f);
            probe.setMetadata("maxValue", 100.0f);
            stubFindAsset(1, probe);
            Result<AssetVO> result = controller.getAsset(1);
            assertThat(result.getData()).isInstanceOf(MonitorAssetVO.class);
            MonitorAssetVO mvo = (MonitorAssetVO) result.getData();
            assertThat(mvo.getValue()).isNull();           // no detection yet
            assertThat(mvo.getMode()).isEqualTo("ACTIVE"); // default mode
            assertThat(mvo.getDetecting()).isFalse();
            assertThat(mvo.getUnit()).isEqualTo("℃");
            assertThat(mvo.getMinValue()).isEqualTo(0.0f);
            assertThat(mvo.getMaxValue()).isEqualTo(100.0f);
            assertThat(mvo.getDataType()).isEqualTo("FLOAT");
            assertThat(mvo.getViewType()).isEqualTo("SLIDER");
            assertThat(mvo.getDetectTimeoutMs()).isNotNull();
        }

        @Test
        @DisplayName("returns plain AssetVO for Space")
        void spaceReturnsPlainAssetVO() {
            Space space = new Space();
            space.init(new SpaceType("st"), 2, "space-1");
            stubFindAsset(2, space);
            Result<AssetVO> result = controller.getAsset(2);
            assertThat(result.getData()).isInstanceOf(AssetVO.class);
            assertThat(result.getData()).isNotInstanceOf(MonitorAssetVO.class);
        }

        @Test
        @DisplayName("returns MonitorAssetVO for Control")
        void controlReturnsMonitorAssetVO() {
            Control control = new Control() {
                @Override public void execute(String command) {}
            };
            ControlType ct = new ControlType("SwitchControl");
            ct.setDataType(com.systar.monitor.asset.type.DataType.BOOLEAN);
            ct.setViewType(com.systar.monitor.asset.type.ViewType.YESNO);
            control.init(ct, 3, "ctrl-1");
            stubFindAsset(3, control);
            Result<AssetVO> result = controller.getAsset(3);
            assertThat(result.getData()).isInstanceOf(MonitorAssetVO.class);
            MonitorAssetVO mvo = (MonitorAssetVO) result.getData();
            assertThat(mvo.getKind()).isEqualTo("CONTROL");
            assertThat(mvo.getDataType()).isEqualTo("BOOLEAN");
            assertThat(mvo.getViewType()).isEqualTo("YESNO");
        }
    }

    @Nested
    @DisplayName("POST /api/monitor/assets (create)")
    class CreateAsset {

        @Test
        @DisplayName("returns new id on success")
        void success() {
            var req = new com.systar.server.dto.AssetCreateRequest("SPACE", 0, "x", null, null, null, null);
            when(orchestrator.createAsset(req)).thenReturn(42);
            Result<Integer> result = controller.createAsset(req);
            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getData()).isEqualTo(42);
        }

        @Test
        @DisplayName("returns 400 when service throws AssetException")
        void badRequest() {
            var req = new com.systar.server.dto.AssetCreateRequest("SPACE", 0, "x", null, null, null, null);
            when(orchestrator.createAsset(req)).thenThrow(new AssetException("bad"));
            Result<Integer> result = controller.createAsset(req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
            assertThat(result.getMessage()).isEqualTo("bad");
        }
    }

    @Nested
    @DisplayName("PUT /api/monitor/assets/{id} (update)")
    class UpdateAsset {

        @Test
        @DisplayName("returns success when update succeeds")
        void success() {
            Space space = new Space();
            space.init(new SpaceType("st"), 1, "s");
            stubFindAsset(1, space);
            var req = new com.systar.server.dto.AssetUpdateRequest(null, "new-cap", null, null, null);
            Result<Void> result = controller.updateAsset(1, req);
            assertThat(result.getCode()).isEqualTo(0);
            verify(orchestrator).updateAsset(eq(1), eq(AssetKind.SPACE), any());
        }

        @Test
        @DisplayName("returns 404 when asset not found")
        void notFound() {
            stubFindAsset(999, null);
            var req = new com.systar.server.dto.AssetUpdateRequest(null, "x", null, null, null);
            Result<Void> result = controller.updateAsset(999, req);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
            verify(orchestrator, never()).updateAsset(anyInt(), any(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/monitor/assets/{id}")
    class DeleteAsset {

        @Test
        @DisplayName("returns success when delete succeeds")
        void success() {
            Probe probe = new Probe() {};
            probe.init(new ProbeType("pt"), 5, "p");
            stubFindAsset(5, probe);
            Result<Void> result = controller.deleteAsset(5);
            assertThat(result.getCode()).isEqualTo(0);
            verify(orchestrator).deleteAsset(5, AssetKind.PROBE);
        }

        @Test
        @DisplayName("returns 404 when asset not found")
        void notFound() {
            stubFindAsset(99, null);
            Result<Void> result = controller.deleteAsset(99);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
        }

        @Test
        @DisplayName("returns 409 when AssetException")
        void conflict() {
            Probe probe = new Probe() {};
            probe.init(new ProbeType("pt"), 5, "p");
            stubFindAsset(5, probe);
            doThrow(new AssetException("Cannot delete")).when(orchestrator).deleteAsset(5, AssetKind.PROBE);
            Result<Void> result = controller.deleteAsset(5);
            assertThat(result.getCode()).isEqualTo(Result.CODE_CONFLICT);
        }
    }

    @Nested
    @DisplayName("PUT /api/monitor/assets/{id}/start")
    class StartAsset {

        @Test
        @DisplayName("returns success")
        void success() {
            Probe probe = new Probe() {};
            probe.init(new ProbeType("pt"), 1, "p");
            stubFindAsset(1, probe);
            Result<Void> result = controller.startAsset(1);
            assertThat(result.getCode()).isEqualTo(0);
            verify(orchestrator).startAsset(1, AssetKind.PROBE);
        }

        @Test
        @DisplayName("returns 404 when asset not found")
        void notFound() {
            stubFindAsset(99, null);
            Result<Void> result = controller.startAsset(99);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /api/monitor/assets/{id}/stop")
    class StopAsset {

        @Test
        @DisplayName("returns success")
        void success() {
            Probe probe = new Probe() {};
            probe.init(new ProbeType("pt"), 1, "p");
            stubFindAsset(1, probe);
            Result<Void> result = controller.stopAsset(1);
            assertThat(result.getCode()).isEqualTo(0);
            verify(orchestrator).stopAsset(1, AssetKind.PROBE);
        }

        @Test
        @DisplayName("returns 404 when asset not found")
        void notFound() {
            stubFindAsset(99, null);
            Result<Void> result = controller.stopAsset(99);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /api/monitor/assets/{id}/disable")
    class DisableAsset {

        @Test
        @DisplayName("returns success")
        void success() {
            Space space = new Space();
            space.init(new SpaceType("st"), 1, "s");
            stubFindAsset(1, space);
            Result<Void> result = controller.disableAsset(1);
            assertThat(result.getCode()).isEqualTo(0);
            verify(orchestrator).disableAsset(1, AssetKind.SPACE);
        }

        @Test
        @DisplayName("returns 404 when asset not found")
        void notFound() {
            stubFindAsset(99, null);
            Result<Void> result = controller.disableAsset(99);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /api/monitor/assets/{id}/enable")
    class EnableAsset {

        @Test
        @DisplayName("returns success")
        void success() {
            Probe probe = new Probe() {};
            probe.init(new ProbeType("pt"), 2, "p");
            stubFindAsset(2, probe);
            Result<Void> result = controller.enableAsset(2);
            assertThat(result.getCode()).isEqualTo(0);
            verify(orchestrator).enableAsset(2, AssetKind.PROBE);
        }
    }

    @Nested
    @DisplayName("POST /api/monitor/assets/{id}/detect")
    class DetectAsset {

        @Test
        @DisplayName("returns success for active Probe")
        void success() {
            Probe probe = new Probe() {};
            probe.init(new ProbeType("pt"), 1, "p");
            stubFindAsset(1, probe);
            Result<Map<String, Object>> result = controller.detectAsset(1);
            assertThat(result.getCode()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 404 when asset not found")
        void notFound() {
            stubFindAsset(99, null);
            Result<Map<String, Object>> result = controller.detectAsset(99);
            assertThat(result.getCode()).isEqualTo(Result.CODE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Batch operations")
    class BatchOps {

        @Test
        @DisplayName("PUT /assets/batch/start delegates to orchestrator")
        void batchStart() {
            when(orchestrator.batchStart(List.of(1, 2))).thenReturn(new BatchResult());
            Result<BatchResult> result = controller.batchStart(new BatchAssetRequest(List.of(1, 2)));
            assertThat(result.getCode()).isEqualTo(0);
            verify(orchestrator).batchStart(List.of(1, 2));
        }

        @Test
        @DisplayName("PUT /assets/batch/stop delegates to orchestrator")
        void batchStop() {
            when(orchestrator.batchStop(List.of(1))).thenReturn(new BatchResult());
            Result<BatchResult> result = controller.batchStop(new BatchAssetRequest(List.of(1)));
            assertThat(result.getCode()).isEqualTo(0);
        }

        @Test
        @DisplayName("DELETE /assets/batch delegates to orchestrator")
        void batchDelete() {
            when(orchestrator.batchDelete(List.of(1, 2, 3))).thenReturn(new BatchResult());
            Result<BatchResult> result = controller.batchDelete(new BatchAssetRequest(List.of(1, 2, 3)));
            assertThat(result.getCode()).isEqualTo(0);
        }

        @Test
        @DisplayName("batch with empty ids returns error")
        void emptyIds() {
            Result<BatchResult> result = controller.batchStart(new BatchAssetRequest(List.of()));
            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
        }
    }
}
