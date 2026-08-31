package com.systar.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.system.entity.SysUserEntity;
import com.systar.system.test.SystemTestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemTestApplication.class)
@Transactional
class SysUserServiceTest {

    @Autowired
    private SysUserService sysUserService;

    @Test
    @DisplayName("创建用户时密码应该被BCrypt编码")
    void shouldCreateUserWithEncodedPassword() {
        SysUserEntity user = new SysUserEntity();
        user.setUsername("testuser");
        user.setPassword("plainpassword");
        user.setNickname("Test User");
        user.setStatus(0);

        sysUserService.createUser(user);

        assertThat(user.getId()).isNotNull();

        SysUserEntity created = sysUserService.getByUsername("testuser");
        assertThat(created).isNotNull();
        // Password should NOT be plaintext
        assertThat(created.getPassword()).isNotEqualTo("plainpassword");
        // BCrypt hashes start with $2a$
        assertThat(created.getPassword()).startsWith("$2a$");
    }

    @Test
    @DisplayName("查询用户列表时密码字段应为null")
    void shouldNotExposePasswordInQuery() {
        Page<SysUserEntity> page = sysUserService.listUsers(1, 10, null, null, null);

        assertThat(page.getRecords()).isNotEmpty();
        for (SysUserEntity user : page.getRecords()) {
            assertThat(user.getPassword()).isNull();
        }
    }

    @Test
    @DisplayName("通过用户名查询时应返回密码（用于认证）")
    void shouldGetByUsernameWithPassword() {
        SysUserEntity user = sysUserService.getByUsername("admin");

        assertThat(user).isNotNull();
        assertThat(user.getPassword()).isNotNull();
        assertThat(user.getPassword()).isNotEmpty();
        assertThat(user.getPassword()).startsWith("$2a$");
    }
}
