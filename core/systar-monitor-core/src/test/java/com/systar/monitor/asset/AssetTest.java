package com.systar.monitor.asset;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.Space;
import com.systar.monitor.asset.type.SpaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetTest {

    private ProbeType type;
    private Probe probe;

    @BeforeEach
    void setUp() {
        type = new ProbeType("tempProbe");
        probe = new Probe();
        probe.init(type, 1, "probe1");
    }

    // ---- init validation ----

    @Test
    @DisplayName("init rejects null type")
    void initRejectsNullType() {
        Probe p = new Probe();
        assertThatThrownBy(() -> p.init(null, 1, "name"))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("type must not be null");
    }

    @Test
    @DisplayName("init rejects zero id")
    void initRejectsZeroId() {
        Probe p = new Probe();
        assertThatThrownBy(() -> p.init(type, 0, "name"))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("positive non-zero");
    }

    @Test
    @DisplayName("init rejects blank name")
    void initRejectsBlankName() {
        Probe p = new Probe();
        assertThatThrownBy(() -> p.init(type, 1, "  "))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    @DisplayName("init rejects null name")
    void initRejectsNullName() {
        Probe p = new Probe();
        assertThatThrownBy(() -> p.init(type, 1, null))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    @DisplayName("init sets fields correctly and defaults caption from type")
    void initSetsFields() {
        type.setCaption("Temperature");
        Probe p = new Probe();
        p.init(type, 42, "temp");

        assertThat(p.getId()).isEqualTo(42);
        assertThat(p.getName()).isEqualTo("temp");
        assertThat(p.getType()).isSameAs(type);
        assertThat(p.getCaption()).isEqualTo("Temperature");
        assertThat(p.getState()).isEqualTo(AssetState.NORMAL);
        assertThat(p.isEnabled()).isTrue();
        assertThat(p.isRemoved()).isFalse();
    }

    @Test
    @DisplayName("init does not overwrite caption if already set")
    void initPreservesCaption() {
        type.setCaption("TypeCaption");
        Probe p = new Probe();
        p.setCaption("CustomCaption");
        p.init(type, 1, "p");

        assertThat(p.getCaption()).isEqualTo("CustomCaption");
    }

    // ---- setState with bubbling ----

    @Test
    @DisplayName("setState changes state and notifies context")
    void setStateNotifiesContext() {
        AssetContext ctx = new AssetContext();
        AssetStateListener listener = mock(AssetStateListener.class);
        ctx.addStateListener(listener);
        probe.setContext(ctx);

        probe.setState(AssetState.WARNING);

        assertThat(probe.getState()).isEqualTo(AssetState.WARNING);
        verify(listener).onStateChanged(probe, AssetState.NORMAL, AssetState.WARNING);
    }

    @Test
    @DisplayName("setState ignores null")
    void setStateIgnoresNull() {
        probe.setState(null);
        assertThat(probe.getState()).isEqualTo(AssetState.NORMAL);
    }

    @Test
    @DisplayName("setState ignores same state")
    void setStateIgnoresSameState() {
        probe.setState(AssetState.NORMAL);
        assertThat(probe.getState()).isEqualTo(AssetState.NORMAL);
    }

    @Test
    @DisplayName("setState bubbles more-severe state to parent")
    void setStateBubblesUpToParent() {
        Space parent = new Space();
        parent.init(new SpaceType("root"), 100, "rootSpace");

        probe.setParent(parent);
        probe.setState(AssetState.ERROR);

        assertThat(parent.getState()).isEqualTo(AssetState.ERROR);
    }

    @Test
    @DisplayName("setState triggers recompute when child becomes less severe than parent")
    void setStateTriggersRecomputeOnLessSevere() {
        // Set up parent with two children
        Space parent = new Space();
        parent.init(new SpaceType("root"), 100, "rootSpace");

        Probe child1 = new Probe();
        child1.init(type, 1, "c1");
        parent.addChild(child1);

        Probe child2 = new Probe();
        child2.init(type, 2, "c2");
        parent.addChild(child2);

        // Make both children ERROR -> parent becomes ERROR
        child1.setState(AssetState.ERROR);
        child2.setState(AssetState.ERROR);
        assertThat(parent.getState()).isEqualTo(AssetState.ERROR);

        // Recover child1 to NORMAL -> parent should recompute to ERROR (child2 still ERROR)
        child1.setState(AssetState.NORMAL);
        assertThat(parent.getState()).isEqualTo(AssetState.ERROR);
    }

    // ---- metadata ----

    @Test
    @DisplayName("metadata get and set works")
    void metadataWorks() {
        probe.setMetadata("key1", "value1");
        probe.setMetadata("key2", 42);

        assertThat(probe.<String>getMetadata("key1")).isEqualTo("value1");
        assertThat(probe.<Integer>getMetadata("key2")).isEqualTo(42);
        assertThat(probe.<Object>getMetadata("missing")).isNull();
    }

    // ---- hierarchy ----

    @Test
    @DisplayName("setParent assigns parent")
    void setParentWorks() {
        Space parent = new Space();
        parent.init(new SpaceType("root"), 100, "rootSpace");

        probe.setParent(parent);
        assertThat(probe.getParent()).isSameAs(parent);
    }

    // ---- toString ----

    @Test
    @DisplayName("toString includes kind, id and name")
    void toStringFormat() {
        String str = probe.toString();
        assertThat(str).contains("PROBE").contains("1").contains("probe1");
    }

    // ---- kind flags on Probe ----

    @Test
    @DisplayName("Probe: kind is PROBE, not compound, is monitor")
    void probeKindFlags() {
        assertThat(probe.getKind()).isEqualTo(AssetKind.PROBE);
        assertThat(probe.isCompound()).isFalse();
        assertThat(probe.isMonitor()).isTrue();
    }

    // ---- setters ----

    @Test
    @DisplayName("Basic setters work")
    void basicSetters() {
        probe.setName("newName");
        probe.setCaption("newCaption");
        probe.setId(99);
        probe.setParentId(50);
        probe.setEnabled(false);
        probe.setRemoved(true);
        probe.setHealthIndex(0.85);

        assertThat(probe.getName()).isEqualTo("newName");
        assertThat(probe.getCaption()).isEqualTo("newCaption");
        assertThat(probe.getId()).isEqualTo(99);
        assertThat(probe.getParentId()).isEqualTo(50);
        assertThat(probe.isEnabled()).isFalse();
        assertThat(probe.isRemoved()).isTrue();
        assertThat(probe.getHealthIndex()).isCloseTo(0.85, offset(0.001));
    }
}
