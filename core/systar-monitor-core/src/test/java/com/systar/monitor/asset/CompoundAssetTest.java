package com.systar.monitor.asset;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.Space;
import com.systar.monitor.asset.type.SpaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class CompoundAssetTest {

    private Space root;
    private ProbeType probeType;

    @BeforeEach
    void setUp() {
        root = new Space();
        root.init(new SpaceType("rootType"), 1, "root");
        probeType = new ProbeType("pt");
    }

    // ---- addChild ----

    @Test
    @DisplayName("addChild adds a child and sets parent")
    void addChild() {
        Probe child = new Probe();
        child.init(probeType, 10, "child1");
        root.addChild(child);

        assertThat(root.getChild("child1")).isSameAs(child);
        assertThat(child.getParent()).isSameAs(root);
    }

    @Test
    @DisplayName("addChild rejects null")
    void addChildRejectsNull() {
        assertThatThrownBy(() -> root.addChild(null))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("addChild rejects child that already has a parent")
    void addChildRejectsChildWithParent() {
        Probe child = new Probe();
        child.init(probeType, 10, "child1");
        root.addChild(child);

        Space other = new Space();
        other.init(new SpaceType("other"), 2, "other");
        assertThatThrownBy(() -> other.addChild(child))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("already has a parent");
    }

    @Test
    @DisplayName("addChild rejects duplicate name")
    void addChildRejectsDuplicate() {
        Probe child1 = new Probe();
        child1.init(probeType, 10, "sameName");
        root.addChild(child1);

        Probe child2 = new Probe();
        child2.init(probeType, 11, "sameName");
        assertThatThrownBy(() -> root.addChild(child2))
                .isInstanceOf(AssetException.class)
                .hasMessageContaining("Duplicate child");
    }

    // ---- removeChild ----

    @Test
    @DisplayName("removeChild removes child and clears parent reference")
    void removeChild() {
        Probe child = new Probe();
        child.init(probeType, 10, "child1");
        root.addChild(child);

        Asset<?> removed = root.removeChild("child1");
        assertThat(removed).isSameAs(child);
        assertThat(child.getParent()).isNull();
        assertThat(root.getChild("child1")).isNull();
    }

    @Test
    @DisplayName("removeChild returns null for non-existent child")
    void removeChildNotFound() {
        assertThat(root.removeChild("absent")).isNull();
    }

    // ---- children ----

    @Test
    @DisplayName("children returns unmodifiable collection of all direct children")
    void childrenReturnsAll() {
        Probe c1 = new Probe();
        c1.init(probeType, 10, "a");
        Probe c2 = new Probe();
        c2.init(probeType, 11, "b");
        root.addChild(c1);
        root.addChild(c2);

        Collection<Asset<?>> kids = root.children();
        assertThat(kids).hasSize(2).containsExactlyInAnyOrder(c1, c2);
    }

    @Test
    @DisplayName("children collection is unmodifiable")
    void childrenIsUnmodifiable() {
        Probe c1 = new Probe();
        c1.init(probeType, 10, "a");
        root.addChild(c1);

        Collection<Asset<?>> kids = root.children();
        assertThatThrownBy(kids::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- findChild ----

    @Test
    @DisplayName("findChild finds direct child")
    void findChildDirect() {
        Probe child = new Probe();
        child.init(probeType, 10, "target");
        root.addChild(child);

        assertThat(root.findChild("target")).isSameAs(child);
    }

    @Test
    @DisplayName("findChild finds nested child recursively")
    void findChildRecursive() {
        Space sub = new Space();
        sub.init(new SpaceType("subType"), 20, "subSpace");
        root.addChild(sub);

        Probe deep = new Probe();
        deep.init(probeType, 30, "deepProbe");
        sub.addChild(deep);

        assertThat(root.findChild("deepProbe")).isSameAs(deep);
    }

    @Test
    @DisplayName("findChild returns null when not found")
    void findChildNotFound() {
        assertThat(root.findChild("nonexistent")).isNull();
    }

    // ---- updateStateByChildren ----

    @Test
    @DisplayName("updateStateByChildren sets worst child state")
    void updateStateByChildrenWorst() {
        Probe c1 = new Probe();
        c1.init(probeType, 10, "a");
        Probe c2 = new Probe();
        c2.init(probeType, 11, "b");
        root.addChild(c1);
        root.addChild(c2);

        c1.setState(AssetState.WARNING);
        c2.setState(AssetState.ERROR);

        root.updateStateByChildren();
        assertThat(root.getState()).isEqualTo(AssetState.ERROR);
    }

    @Test
    @DisplayName("updateStateByChildren with no children leaves state unchanged")
    void updateStateByChildrenNoChildren() {
        root.setState(AssetState.WARNING);
        root.updateStateByChildren();
        // No children, so worst stays null -> state unchanged
        assertThat(root.getState()).isEqualTo(AssetState.WARNING);
    }

    // ---- kind flags ----

    @Test
    @DisplayName("Space: kind is SPACE, is compound, not monitor")
    void spaceKindFlags() {
        assertThat(root.getKind()).isEqualTo(AssetKind.SPACE);
        assertThat(root.isCompound()).isTrue();
        assertThat(root.isMonitor()).isFalse();
    }

    // ---- visitor dispatch ----

    @Test
    @DisplayName("Space dispatches visitor correctly")
    void spaceVisitorDispatch() {
        AssetVisitor<String> visitor = mock(AssetVisitor.class);
        when(visitor.visit(any(Space.class))).thenReturn("visited");
        assertThat(root.accept(visitor)).isEqualTo("visited");
        verify(visitor).visit(root);
    }
}
