package com.systar.common.code;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class CodeCatalogTest {

    private CodeItem makeItem(int id, String name, String caption, Integer parentId) {
        CodeItem item = new CodeItem();
        item.setId(id);
        item.setName(name);
        item.setCaption(caption);
        item.setParentId(parentId);
        return item;
    }

    // ========== Constructors ==========

    @Nested
    @DisplayName("Constructors")
    class Constructors {

        @Test
        @DisplayName("default constructor initializes empty items list")
        void defaultConstructor() {
            CodeCatalog catalog = new CodeCatalog();
            assertThat(catalog.getItems()).isNotNull().isEmpty();
            assertThat(catalog.getId()).isZero();
            assertThat(catalog.getName()).isNull();
        }

        @Test
        @DisplayName("parameterized constructor sets id and name")
        void parameterizedConstructor() {
            CodeCatalog catalog = new CodeCatalog(10, "StatusCatalog");
            assertThat(catalog.getId()).isEqualTo(10);
            assertThat(catalog.getName()).isEqualTo("StatusCatalog");
            assertThat(catalog.getItems()).isNotNull().isEmpty();
        }
    }

    // ========== addItem ==========

    @Nested
    @DisplayName("addItem()")
    class AddItem {

        @Test
        @DisplayName("addItem adds item to the list")
        void addItemAddsItem() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            CodeItem item = makeItem(100, "A", "Alpha", null);
            catalog.addItem(item);
            assertThat(catalog.getItems()).hasSize(1).containsExactly(item);
        }

        @Test
        @DisplayName("addItem accepts multiple items")
        void addItemMultiple() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            catalog.addItem(makeItem(1, "A", "Alpha", null));
            catalog.addItem(makeItem(2, "B", "Beta", null));
            catalog.addItem(makeItem(3, "C", "Gamma", null));
            assertThat(catalog.getItems()).hasSize(3);
        }

        @Test
        @DisplayName("addItem allows duplicate IDs (no validation)")
        void addDuplicateId() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            catalog.addItem(makeItem(1, "A", "First", null));
            catalog.addItem(makeItem(1, "B", "Duplicate", null));
            assertThat(catalog.getItems()).hasSize(2);
        }
    }

    // ========== getItem ==========

    @Nested
    @DisplayName("getItem()")
    class GetItem {

        @Test
        @DisplayName("getItem returns matching item")
        void getItemFound() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            CodeItem target = makeItem(42, "target", "Target", null);
            catalog.addItem(makeItem(1, "other", "Other", null));
            catalog.addItem(target);
            assertThat(catalog.getItem(42)).isSameAs(target);
        }

        @Test
        @DisplayName("getItem returns null when not found")
        void getItemNotFound() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            catalog.addItem(makeItem(1, "A", "Alpha", null));
            assertThat(catalog.getItem(999)).isNull();
        }

        @Test
        @DisplayName("getItem returns first match for duplicate IDs")
        void getItemFirstMatch() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            CodeItem first = makeItem(1, "first", "First", null);
            CodeItem second = makeItem(1, "second", "Second", null);
            catalog.addItem(first);
            catalog.addItem(second);
            assertThat(catalog.getItem(1)).isSameAs(first);
        }

        @Test
        @DisplayName("getItem on empty catalog returns null")
        void getItemEmpty() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            assertThat(catalog.getItem(1)).isNull();
        }
    }

    // ========== getItemsByParentId ==========

    @Nested
    @DisplayName("getItemsByParentId()")
    class GetItemsByParentId {

        @Test
        @DisplayName("returns items matching parentId")
        void matchingParentId() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            CodeItem child1 = makeItem(2, "c1", "Child1", 1);
            CodeItem child2 = makeItem(3, "c2", "Child2", 1);
            CodeItem root = makeItem(1, "root", "Root", null);
            catalog.addItem(root);
            catalog.addItem(child1);
            catalog.addItem(child2);

            List<CodeItem> children = catalog.getItemsByParentId(1);
            assertThat(children).hasSize(2).containsExactlyInAnyOrder(child1, child2);
        }

        @Test
        @DisplayName("returns root items when parentId is null")
        void rootItems() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            CodeItem root1 = makeItem(1, "r1", "Root1", null);
            CodeItem root2 = makeItem(2, "r2", "Root2", null);
            CodeItem child = makeItem(3, "c1", "Child", 1);
            catalog.addItem(root1);
            catalog.addItem(root2);
            catalog.addItem(child);

            List<CodeItem> roots = catalog.getItemsByParentId(null);
            assertThat(roots).hasSize(2).containsExactlyInAnyOrder(root1, root2);
        }

        @Test
        @DisplayName("returns empty list when no matches")
        void noMatches() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            catalog.addItem(makeItem(1, "A", "Alpha", null));
            List<CodeItem> result = catalog.getItemsByParentId(99);
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("returns empty list for empty catalog")
        void emptyCatalog() {
            CodeCatalog catalog = new CodeCatalog(1, "Test");
            List<CodeItem> result = catalog.getItemsByParentId(null);
            assertThat(result).isEmpty();
        }
    }

    // ========== Lombok getters/setters for id/name/items ==========

    @Test
    @DisplayName("setId and getId work")
    void idGetterSetter() {
        CodeCatalog catalog = new CodeCatalog();
        catalog.setId(99);
        assertThat(catalog.getId()).isEqualTo(99);
    }

    @Test
    @DisplayName("setName and getName work")
    void nameGetterSetter() {
        CodeCatalog catalog = new CodeCatalog();
        catalog.setName("MyCatalog");
        assertThat(catalog.getName()).isEqualTo("MyCatalog");
    }

    @Test
    @DisplayName("equals and hashCode based on all fields")
    void equalsHashCode() {
        CodeCatalog a = new CodeCatalog(1, "Test");
        CodeCatalog b = new CodeCatalog(1, "Test");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("toString contains field values")
    void toStringContainsFields() {
        CodeCatalog catalog = new CodeCatalog(5, "Status");
        String str = catalog.toString();
        assertThat(str).contains("5").contains("Status");
    }
}
