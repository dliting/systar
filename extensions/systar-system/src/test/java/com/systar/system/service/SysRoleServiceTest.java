package com.systar.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.system.entity.SysRoleEntity;
import com.systar.system.test.SystemTestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemTestApplication.class)
@Transactional
class SysRoleServiceTest {

    @Autowired
    private SysRoleService sysRoleService;

    @Test
    @DisplayName("创建角色并分配菜单")
    void shouldCreateRoleAndAssignMenus() {
        SysRoleEntity role = new SysRoleEntity();
        role.setRoleName("测试角色");
        role.setRoleKey("test_role");
        role.setStatus(0);

        sysRoleService.createRole(role);
        assertThat(role.getId()).isNotNull();

        sysRoleService.assignMenus(role.getId(), Arrays.asList(100L, 101L, 102L));

        List<Long> menuIds = sysRoleService.getMenuIdsByRoleId(role.getId());
        assertThat(menuIds).containsExactlyInAnyOrder(100L, 101L, 102L);
    }

    @Test
    @DisplayName("重新分配菜单时应该先清空旧的再插入新的")
    void shouldReplaceMenusOnReassign() {
        SysRoleEntity role = new SysRoleEntity();
        role.setRoleName("替换角色");
        role.setRoleKey("replace_role");
        role.setStatus(0);

        sysRoleService.createRole(role);

        sysRoleService.assignMenus(role.getId(), Arrays.asList(100L, 200L));
        assertThat(sysRoleService.getMenuIdsByRoleId(role.getId())).hasSize(2);

        sysRoleService.assignMenus(role.getId(), Collections.singletonList(300L));
        List<Long> menuIds = sysRoleService.getMenuIdsByRoleId(role.getId());
        assertThat(menuIds).hasSize(1);
        assertThat(menuIds.get(0)).isEqualTo(300L);
    }
}
