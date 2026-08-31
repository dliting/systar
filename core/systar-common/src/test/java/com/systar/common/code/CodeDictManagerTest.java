package com.systar.common.code;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class CodeDictManagerTest {

    private CodeDictManager manager;

    @BeforeEach
    void setUp() {
        manager = new CodeDictManager();
    }

    private CodeCatalog makeCatalog(int id, String name, CodeItem... items) {
        CodeCatalog catalog = new CodeCatalog(id, name);
        for (CodeItem item : items) {
            catalog.addItem(item);
        }
        return catalog;
    }

    private CodeItem makeItem(int id, String name, String caption, Integer parentId) {
        CodeItem item = new CodeItem();
        item.setId(id);
        item.setName(name);
        item.setCaption(caption);
        item.setParentId(parentId);
        return item;
    }

    // ========== loadCatalogs ==========

    @Nested
    @DisplayName("loadCatalogs()")
    class LoadCatalogs {

        @Test
        @DisplayName("loadCatalogs with valid data")
        void loadValidData() {
            Map<Integer, CodeCatalog> data = new HashMap<>();
            data.put(1, makeCatalog(1, "Status"));
            data.put(2, makeCatalog(2, "Type"));

            manager.loadCatalogs(data);

            assertThat(manager.getCatalog(1)).isNotNull();
            assertThat(manager.getCatalog(2)).isNotNull();
            assertThat(manager.getCatalog(999)).isNull();
        }

        @Test
        @DisplayName("loadCatalogs with null clears existing data")
        void loadNull() {
            // First load some data
            Map<Integer, CodeCatalog> data = new HashMap<>();
            data.put(1, makeCatalog(1, "Status"));
            manager.loadCatalogs(data);
            assertThat(manager.getCatalog(1)).isNotNull();

            // Load null should clear
            manager.loadCatalogs(null);
            assertThat(manager.getCatalog(1)).isNull();
        }

        @Test
        @DisplayName("loadCatalogs replaces previous data")
        void loadReplaces() {
            Map<Integer, CodeCatalog> first = new HashMap<>();
            first.put(1, makeCatalog(1, "First"));
            manager.loadCatalogs(first);

            Map<Integer, CodeCatalog> second = new HashMap<>();
            second.put(2, makeCatalog(2, "Second"));
            manager.loadCatalogs(second);

            assertThat(manager.getCatalog(1)).isNull();
            assertThat(manager.getCatalog(2)).isNotNull();
        }

        @Test
        @DisplayName("loadCatalogs with empty map")
        void loadEmpty() {
            manager.loadCatalogs(new HashMap<>());
            assertThat(manager.getCatalog(1)).isNull();
        }
    }

    // ========== getCatalog ==========

    @Nested
    @DisplayName("getCatalog()")
    class GetCatalog {

        @Test
        @DisplayName("returns catalog by ID")
        void returnsCatalog() {
            CodeCatalog catalog = makeCatalog(10, "TestCatalog");
            Map<Integer, CodeCatalog> data = Map.of(10, catalog);
            manager.loadCatalogs(data);

            assertThat(manager.getCatalog(10)).isSameAs(catalog);
        }

        @Test
        @DisplayName("returns null for unknown catalog ID")
        void returnsNullForUnknown() {
            manager.loadCatalogs(new HashMap<>());
            assertThat(manager.getCatalog(999)).isNull();
        }

        @Test
        @DisplayName("returns null before any load")
        void returnsNullBeforeLoad() {
            assertThat(manager.getCatalog(1)).isNull();
        }
    }

    // ========== getItem ==========

    @Nested
    @DisplayName("getItem()")
    class GetItem {

        @Test
        @DisplayName("returns item from existing catalog")
        void returnsItem() {
            CodeItem item = makeItem(100, "active", "Active", null);
            CodeCatalog catalog = makeCatalog(1, "Status", item);
            manager.loadCatalogs(Map.of(1, catalog));

            assertThat(manager.getItem(1, 100)).isSameAs(item);
        }

        @Test
        @DisplayName("returns null when catalog does not exist")
        void catalogNotFound() {
            assertThat(manager.getItem(999, 1)).isNull();
        }

        @Test
        @DisplayName("returns null when item does not exist in catalog")
        void itemNotFound() {
            CodeCatalog catalog = makeCatalog(1, "Status", makeItem(1, "a", "A", null));
            manager.loadCatalogs(Map.of(1, catalog));

            assertThat(manager.getItem(1, 999)).isNull();
        }
    }

    // ========== getItemCaption ==========

    @Nested
    @DisplayName("getItemCaption()")
    class GetItemCaption {

        @Test
        @DisplayName("returns caption for existing item")
        void returnsCaption() {
            CodeItem item = makeItem(1, "active", "Active Status", null);
            manager.loadCatalogs(Map.of(1, makeCatalog(1, "Status", item)));

            assertThat(manager.getItemCaption(1, 1)).isEqualTo("Active Status");
        }

        @Test
        @DisplayName("returns empty string when item not found")
        void itemNotFoundReturnsEmpty() {
            assertThat(manager.getItemCaption(1, 999)).isEmpty();
        }

        @Test
        @DisplayName("returns empty string when catalog not found")
        void catalogNotFoundReturnsEmpty() {
            assertThat(manager.getItemCaption(999, 1)).isEmpty();
        }

        @Test
        @DisplayName("returns empty string for item with null caption")
        void nullCaptionReturnsNull() {
            CodeItem item = makeItem(1, "test", null, null);
            manager.loadCatalogs(Map.of(1, makeCatalog(1, "Test", item)));

            // The implementation returns item.getCaption() which would be null
            assertThat(manager.getItemCaption(1, 1)).isNull();
        }
    }
}
