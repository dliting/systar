package com.systar.system.service;

import com.systar.system.entity.SysMenuEntity;
import com.systar.system.test.SystemTestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemTestApplication.class)
@Transactional
class SysMenuServiceTest {

    @Autowired
    private SysMenuService sysMenuService;

    @Test
    @DisplayName("应该返回正确的菜单树结构")
    void shouldReturnMenuTree() {
        List<SysMenuEntity> tree = sysMenuService.getMenuTree();

        assertThat(tree).isNotEmpty();

        // Find "系统管理" root menu (id=1)
        SysMenuEntity sysMgmt = tree.stream()
                .filter(m -> m.getId() == 1L)
                .findFirst()
                .orElse(null);
        assertThat(sysMgmt).isNotNull();
        assertThat(sysMgmt.getMenuName()).isNotNull();
        assertThat(sysMgmt.getChildren()).isNotEmpty();

        // User management (id=100) should be a child of system management
        boolean hasUserMgmt = sysMgmt.getChildren().stream()
                .anyMatch(c -> c.getId() == 100L);
        assertThat(hasUserMgmt).isTrue();
    }

    @Test
    @DisplayName("admin角色(id=1)应该有sys:user:list和iot:asset:list权限（不包含*）")
    void shouldGetPermissionsByRoleId() {
        List<Long> adminRoleId = Arrays.asList(1L);
        List<String> perms = sysMenuService.getPermissionsByRoleIds(adminRoleId);

        assertThat(perms).isNotEmpty();
        assertThat(perms).contains("sys:user:list");
        assertThat(perms).contains("iot:asset:list");
        // Wildcard should NOT be in the database permissions
        assertThat(perms).doesNotContain("*");
    }
}
