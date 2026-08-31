package com.systar.server.controller.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AssetVOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> toJsonMap(Object vo) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(vo), Map.class);
    }

    @Test
    @DisplayName("AssetVO has only base fields — no Monitor-specific fields")
    void assetVOHasNoMonitorFields() throws Exception {
        AssetVO vo = new AssetVO();
        vo.setId(1);
        vo.setName("1F Server Room");
        vo.setKind("SPACE");
        vo.setState("NORMAL");
        vo.setEnabled(true);

        Map<String, Object> map = toJsonMap(vo);

        assertThat(map).containsKey("id");
        assertThat(map).containsKey("name");
        assertThat(map).containsKey("kind");
        assertThat(map).containsKey("state");
        assertThat(map).containsKey("enabled");

        // Monitor-specific fields must not appear
        assertThat(map).doesNotContainKey("value");
        assertThat(map).doesNotContainKey("detecting");
        assertThat(map).doesNotContainKey("mode");
        assertThat(map).doesNotContainKey("runtimeDesc");
        assertThat(map).doesNotContainKey("unit");
        assertThat(map).doesNotContainKey("minValue");
        assertThat(map).doesNotContainKey("maxValue");
        assertThat(map).doesNotContainKey("dataType");
        assertThat(map).doesNotContainKey("viewType");
        assertThat(map).doesNotContainKey("detectTimeoutMs");
        assertThat(map).doesNotContainKey("lastDetectTime");
    }

    @Test
    @DisplayName("MonitorAssetVO includes both base and Monitor fields in flat JSON")
    void monitorAssetVOFlatSerialization() throws Exception {
        MonitorAssetVO vo = new MonitorAssetVO();
        vo.setId(2);
        vo.setName("Temp Sensor");
        vo.setKind("PROBE");
        vo.setState("NORMAL");
        vo.setEnabled(true);
        vo.setValue(23.5);
        vo.setUnit("℃");
        vo.setDetecting(false);
        vo.setDataType("ANALOG");
        vo.setViewType("GAUGE");
        vo.setDetectTimeoutMs(30_000L);

        Map<String, Object> map = toJsonMap(vo);

        // Base fields present
        assertThat(map.get("id")).isEqualTo(2);
        assertThat(map.get("name")).isEqualTo("Temp Sensor");
        assertThat(map.get("kind")).isEqualTo("PROBE");

        // Monitor-specific fields present at top level (flat, no nesting)
        assertThat(map.get("value")).isEqualTo(23.5);
        assertThat(map.get("unit")).isEqualTo("℃");
        assertThat(map.get("detecting")).isEqualTo(false);
        assertThat(map.get("dataType")).isEqualTo("ANALOG");
        assertThat(map.get("viewType")).isEqualTo("GAUGE");
        assertThat(map.get("detectTimeoutMs")).isEqualTo(30000);
    }

    @Test
    @DisplayName("MonitorAssetVO is assignable to AssetVO")
    void monitorAssetVOIsAssetVO() {
        MonitorAssetVO vo = new MonitorAssetVO();
        vo.setId(1);
        vo.setName("test");

        AssetVO base = vo; // compiles only if MonitorAssetVO extends AssetVO
        assertThat(base.getId()).isEqualTo(1);
        assertThat(base.getName()).isEqualTo("test");
    }

    @Test
    @DisplayName("Deserializing Monitor JSON into MonitorAssetVO preserves all fields")
    void deserializeMonitorJsonPreservesFields() throws Exception {
        MonitorAssetVO original = new MonitorAssetVO();
        original.setId(3);
        original.setName("Humidity");
        original.setKind("PROBE");
        original.setValue(65.0);
        original.setUnit("%RH");
        original.setDetecting(true);

        String json = mapper.writeValueAsString(original);

        // Deserialize back to MonitorAssetVO
        MonitorAssetVO deserialized = mapper.readValue(json, MonitorAssetVO.class);
        assertThat(deserialized.getId()).isEqualTo(3);
        assertThat(deserialized.getValue()).isEqualTo(65.0);
        assertThat(deserialized.getUnit()).isEqualTo("%RH");
        assertThat(deserialized.getDetecting()).isTrue();
    }

    @Test
    @DisplayName("AssetNodeVO has no Monitor-specific fields")
    void assetNodeVOHasNoMonitorFields() throws Exception {
        AssetNodeVO node = new AssetNodeVO();
        node.setId(10);
        node.setName("Floor 1");
        node.setKind("SPACE");

        Map<String, Object> map = toJsonMap(node);

        assertThat(map).containsKey("id");
        assertThat(map).containsKey("name");
        assertThat(map).containsKey("kind");
        assertThat(map).doesNotContainKey("dataType");
        assertThat(map).doesNotContainKey("viewType");
    }

    @Test
    @DisplayName("MonitorAssetNodeVO includes dataType and viewType in flat JSON")
    void monitorAssetNodeVOFlatSerialization() throws Exception {
        MonitorAssetNodeVO node = new MonitorAssetNodeVO();
        node.setId(11);
        node.setName("Temperature");
        node.setKind("PROBE");
        node.setDataType("ANALOG");
        node.setViewType("GAUGE");

        Map<String, Object> map = toJsonMap(node);

        assertThat(map.get("id")).isEqualTo(11);
        assertThat(map.get("dataType")).isEqualTo("ANALOG");
        assertThat(map.get("viewType")).isEqualTo("GAUGE");
    }
}