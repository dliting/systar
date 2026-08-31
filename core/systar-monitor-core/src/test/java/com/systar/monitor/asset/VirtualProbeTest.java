package com.systar.monitor.asset;

import com.systar.monitor.asset.type.VirtualProbeType;
import com.systar.monitor.expression.ProbeRef;
import com.systar.monitor.result.MonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class VirtualProbeTest {

    private AssetStore store;
    private VirtualProbe vp;

    @BeforeEach
    void setUp() {
        store = new AssetStore();
        store.createRoot(new com.systar.monitor.asset.type.SpaceType("root"), "root");
    }

    // ======================== detect() ========================

    @Nested
    @DisplayName("detect")
    class Detect {

        @Test
        @DisplayName("computes value from expression referencing other probes")
        void computesFromDependencies() throws Exception {
            // Setup dependency probes
            Probe dep1 = createProbe(101, "input", 100.0);
            Probe dep2 = createProbe(102, "output", 50.0);
            store.addAsset(dep1);
            store.addAsset(dep2);

            // Create VirtualProbe with expression: output / input * 100
            vp = createVirtualProbe(200, "efficiency", "#probe[101].value / #probe[102].value * 100", "101,102");
            vp.setAssetStore(store);
            vp.parseDependsOn();
            vp.compileExpression();

            MonitorResult result = new MonitorResult(vp);
            vp.detect(result);

            assertThat(result.hasError()).isFalse();
            // 100 / 50 * 100 = 200
            assertThat(result.getValue()).isEqualTo(200.0);
        }

        @Test
        @DisplayName("sets error when no compiled expression")
        void noExpression() throws Exception {
            vp = createVirtualProbe(200, "noExpr", null, null);
            vp.setAssetStore(store);
            vp.parseDependsOn();
            vp.compileExpression();

            MonitorResult result = new MonitorResult(vp);
            vp.detect(result);

            assertThat(result.hasError()).isTrue();
            assertThat(result.getError()).contains("no compiled expression");
        }

        @Test
        @DisplayName("sets error when dependency value is null")
        void dependencyValueNull() throws Exception {
            Probe dep = createProbe(101, "nullProbe", null);
            store.addAsset(dep);

            vp = createVirtualProbe(200, "derived", "#probe[101].value * 2", "101");
            vp.setAssetStore(store);
            vp.parseDependsOn();
            vp.compileExpression();

            MonitorResult result = new MonitorResult(vp);
            vp.detect(result);

            assertThat(result.hasError()).isTrue();
            assertThat(result.getError()).contains("dependency value not available");
        }

        @Test
        @DisplayName("sets error when dependency probe not found")
        void dependencyNotFound() throws Exception {
            vp = createVirtualProbe(200, "derived", "#probe[999].value * 2", "999");
            vp.setAssetStore(store);
            vp.parseDependsOn();
            vp.compileExpression();

            MonitorResult result = new MonitorResult(vp);
            vp.detect(result);

            assertThat(result.hasError()).isTrue();
            assertThat(result.getError()).contains("dependency value not available");
        }

        @Test
        @DisplayName("sets error when assetStore is null")
        void noAssetStore() throws Exception {
            vp = createVirtualProbe(200, "derived", "#probe[101].value * 2", "101");
            vp.setAssetStore(null);
            vp.parseDependsOn();
            vp.compileExpression();

            MonitorResult result = new MonitorResult(vp);
            vp.detect(result);

            assertThat(result.hasError()).isTrue();
            assertThat(result.getError()).contains("dependency value not available");
        }

        @Test
        @DisplayName("sets sampleTime on successful computation")
        void setsSampleTime() throws Exception {
            Probe dep = createProbe(101, "input", 42.0);
            store.addAsset(dep);

            vp = createVirtualProbe(200, "derived", "#probe[101].value", "101");
            vp.setAssetStore(store);
            vp.parseDependsOn();
            vp.compileExpression();

            MonitorResult result = new MonitorResult(vp);
            vp.detect(result);

            assertThat(result.getSampleTime()).isGreaterThan(0);
        }
    }

    // ======================== compileExpression() ========================

    @Nested
    @DisplayName("compileExpression")
    class CompileExpression {

        @Test
        @DisplayName("compiles valid expression")
        void validExpression() {
            vp = createVirtualProbe(200, "valid", "#probe[101].value + 1", "101");
            vp.compileExpression();

            assertThat(vp.getCompiledExpression()).isNotNull();
        }

        @Test
        @DisplayName("does nothing when type is not VirtualProbeType")
        void wrongType() {
            vp = new VirtualProbe();
            vp.init(new com.systar.monitor.asset.type.ProbeType("regular-probe"), 200, "p1");
            vp.compileExpression();

            assertThat(vp.getCompiledExpression()).isNull();
        }

        @Test
        @DisplayName("sets metadata error on invalid expression")
        void invalidExpression() {
            vp = createVirtualProbe(200, "bad", "##invalid##", "101");
            vp.compileExpression();

            assertThat(vp.getCompiledExpression()).isNull();
            assertThat(vp.<String>getMetadata("expressionError")).isNotNull();
        }

        @Test
        @DisplayName("handles null expression gracefully")
        void nullExpression() {
            vp = createVirtualProbe(200, "null", null, "101");
            vp.compileExpression();

            assertThat(vp.getCompiledExpression()).isNull();
        }

        @Test
        @DisplayName("handles blank expression gracefully")
        void blankExpression() {
            vp = createVirtualProbe(200, "blank", "   ", "101");
            vp.compileExpression();

            assertThat(vp.getCompiledExpression()).isNull();
        }
    }

    // ======================== parseDependsOn() ========================

    @Nested
    @DisplayName("parseDependsOn")
    class ParseDependsOn {

        @Test
        @DisplayName("parses comma-separated IDs")
        void parseMultipleIds() {
            vp = createVirtualProbe(200, "multi", "#probe[101].value + #probe[102].value", "101, 102, 103");
            vp.parseDependsOn();

            assertThat(vp.getDependsOn()).containsExactly(101, 102, 103);
        }

        @Test
        @DisplayName("parses single ID")
        void parseSingleId() {
            vp = createVirtualProbe(200, "single", "#probe[101].value", "101");
            vp.parseDependsOn();

            assertThat(vp.getDependsOn()).containsExactly(101);
        }

        @Test
        @DisplayName("returns empty list for null dependsOn")
        void nullDependsOn() {
            vp = createVirtualProbe(200, "null", "#probe[101].value", null);
            vp.parseDependsOn();

            assertThat(vp.getDependsOn()).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for blank dependsOn")
        void blankDependsOn() {
            vp = createVirtualProbe(200, "blank", "#probe[101].value", "  ");
            vp.parseDependsOn();

            assertThat(vp.getDependsOn()).isEmpty();
        }

        @Test
        @DisplayName("trims whitespace from IDs")
        void trimsWhitespace() {
            vp = createVirtualProbe(200, "trim", "#probe[101].value", "  101  ,  202  ");
            vp.parseDependsOn();

            assertThat(vp.getDependsOn()).containsExactly(101, 202);
        }

        @Test
        @DisplayName("ignores empty segments")
        void ignoresEmptySegments() {
            vp = createVirtualProbe(200, "empty", "#probe[101].value", "101,,102");
            vp.parseDependsOn();

            assertThat(vp.getDependsOn()).containsExactly(101, 102);
        }

        @Test
        @DisplayName("throws on non-numeric segment")
        void throwsOnNonNumeric() {
            vp = createVirtualProbe(200, "bad", "#probe[101].value", "101,abc");

            assertThatThrownBy(() -> vp.parseDependsOn())
                    .isInstanceOf(AssetException.class)
                    .hasMessageContaining("Invalid probe ID");
        }

        @Test
        @DisplayName("deduplicates IDs")
        void deduplicatesIds() {
            vp = createVirtualProbe(200, "dup", "#probe[101].value", "101,101,102");
            vp.parseDependsOn();

            assertThat(vp.getDependsOn()).containsExactly(101, 102);
        }
    }

    // ======================== getKind() ========================

    @Test
    @DisplayName("getKind returns PROBE")
    void getKindReturnsProbe() {
        vp = createVirtualProbe(200, "test", "#probe[101].value", "101");
        assertThat(vp.getKind()).isEqualTo(AssetKind.PROBE);
    }

    // ======================== visitor dispatch ========================

    @Test
    @DisplayName("accept dispatches to visitor.visit(VirtualProbe)")
    void visitorDispatch() {
        vp = createVirtualProbe(200, "test", "#probe[101].value", "101");
        AssetVisitor<String> visitor = mock(AssetVisitor.class);
        when(visitor.visit(any(VirtualProbe.class))).thenReturn("visited");

        assertThat(vp.accept(visitor)).isEqualTo("visited");
        verify(visitor).visit(vp);
    }

    // ======================== accessors ========================

    @Test
    @DisplayName("setDependsOn replaces dependency list")
    void setDependsOn() {
        vp = createVirtualProbe(200, "test", "#probe[101].value", "101");
        vp.setDependsOn(List.of(10, 20, 30));
        assertThat(vp.getDependsOn()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("setDependsOn handles null input")
    void setDependsOnNull() {
        vp = createVirtualProbe(200, "test", "#probe[101].value", "101");
        vp.setDependsOn(null);
        assertThat(vp.getDependsOn()).isEmpty();
    }

    // ======================== helpers ========================

    private Probe createProbe(int id, String name, Object value) {
        Probe p = new Probe();
        p.init(new com.systar.monitor.asset.type.ProbeType("test-probe"), id, name);
        p.setValue(value);
        return p;
    }

    private VirtualProbe createVirtualProbe(int id, String name, String expression, String dependsOn) {
        VirtualProbeType type = new VirtualProbeType("vprobe-" + id);
        type.setExpression(expression);
        type.setDependsOn(dependsOn);
        VirtualProbe vp = new VirtualProbe();
        vp.init(type, id, name);
        return vp;
    }
}
