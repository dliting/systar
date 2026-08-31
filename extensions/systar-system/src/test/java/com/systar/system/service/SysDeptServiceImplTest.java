package com.systar.system.service;

import com.systar.system.entity.SysDeptEntity;
import com.systar.system.test.SystemTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SysDeptServiceImplTest {

    @Autowired
    private SysDeptService deptService;

    private SysDeptEntity buildDept(String name, Long parentId) {
        SysDeptEntity dept = new SysDeptEntity();
        dept.setDeptName(name);
        dept.setParentId(parentId);
        dept.setOrderNum(1);
        dept.setStatus(0);
        return dept;
    }

    @Test
    void createDept_shouldSetAncestorsForTopLevel() {
        SysDeptEntity dept = buildDept("Top Dept", 0L);
        deptService.createDept(dept);

        assertThat(dept.getId()).isNotNull();
        assertThat(dept.getAncestors()).isEqualTo("0");
    }

    @Test
    void createDept_shouldSetAncestorsForChild() {
        SysDeptEntity parent = buildDept("Parent Dept", 0L);
        deptService.createDept(parent);

        SysDeptEntity child = buildDept("Child Dept", parent.getId());
        deptService.createDept(child);

        assertThat(child.getAncestors()).isEqualTo("0," + parent.getId());
    }

    @Test
    void getDeptById_shouldReturnCreated() {
        SysDeptEntity dept = buildDept("Find Dept", 0L);
        deptService.createDept(dept);

        SysDeptEntity found = deptService.getDeptById(dept.getId());
        assertThat(found).isNotNull();
        assertThat(found.getDeptName()).isEqualTo("Find Dept");
    }

    @Test
    void updateDept_shouldChangeName() {
        SysDeptEntity dept = buildDept("Original", 0L);
        deptService.createDept(dept);

        dept.setDeptName("Updated");
        deptService.updateDept(dept);

        assertThat(deptService.getDeptById(dept.getId()).getDeptName()).isEqualTo("Updated");
    }

    @Test
    void deleteDept_shouldRemoveWithChildren() {
        SysDeptEntity parent = buildDept("Parent", 0L);
        deptService.createDept(parent);

        SysDeptEntity child = buildDept("Child", parent.getId());
        deptService.createDept(child);

        deptService.deleteDept(parent.getId());

        assertThat(deptService.getDeptById(parent.getId())).isNull();
        assertThat(deptService.getDeptById(child.getId())).isNull();
    }

    @Test
    void createDept_shouldDefaultAncestorsWhenParentNotFound() {
        SysDeptEntity dept = buildDept("Orphan Dept", 9999L);
        deptService.createDept(dept);

        assertThat(dept.getAncestors()).isEqualTo("0");
    }

    @Test
    void getDeptTree_shouldPopulateChildren() {
        SysDeptEntity parent = buildDept("Parent", 0L);
        deptService.createDept(parent);

        SysDeptEntity child = buildDept("Child", parent.getId());
        deptService.createDept(child);

        List<SysDeptEntity> tree = deptService.getDeptTree();
        SysDeptEntity root = tree.stream()
                .filter(d -> d.getId().equals(parent.getId()))
                .findFirst()
                .orElse(null);
        assertThat(root).isNotNull();
        assertThat(root.getChildren()).isNotEmpty();
    }

    @Test
    void getDeptTree_shouldReturnRootDepts() {
        deptService.createDept(buildDept("Root1", 0L));
        deptService.createDept(buildDept("Root2", 0L));

        List<SysDeptEntity> tree = deptService.getDeptTree();
        assertThat(tree).isNotEmpty();
        assertThat(tree.stream().allMatch(d -> d.getParentId() == 0L)).isTrue();
    }
}
