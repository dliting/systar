package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.server.security.SystarSecurityContext;
import com.systar.server.security.SystarUser;
import com.systar.server.security.TokenService;
import com.systar.system.entity.SysUserEntity;
import com.systar.system.service.SysMenuService;
import com.systar.system.service.SysRoleService;
import com.systar.system.service.SysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AuthControllerTest {

    private static final String TEST_PASSWORD = "admin123";
    private static final String BCRYPT_HASH;

    static {
        BCRYPT_HASH = new BCryptPasswordEncoder().encode(TEST_PASSWORD);
    }

    private AuthController controller;
    private SysUserService sysUserService;
    private SysRoleService sysRoleService;
    private SysMenuService sysMenuService;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        sysUserService = mock(SysUserService.class);
        sysRoleService = mock(SysRoleService.class);
        sysMenuService = mock(SysMenuService.class);
        tokenService = mock(TokenService.class);
        controller = new AuthController(sysUserService, sysRoleService,
                sysMenuService, tokenService);
    }

    @AfterEach
    void tearDown() {
        SystarSecurityContext.clear();
    }

    @Test
    void shouldLoginSuccessfully() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(BCRYPT_HASH);
        user.setNickname("管理员");
        user.setStatus(0);

        when(sysUserService.getByUsername("admin")).thenReturn(user);
        when(sysRoleService.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(sysRoleService.getRoleKeysByUserId(1L)).thenReturn(List.of("admin"));
        when(sysMenuService.getPermissionsByRoleIds(List.of(1L)))
                .thenReturn(List.of("sys:user:list"));
        when(tokenService.generateToken(1L, "admin", "*"))
                .thenReturn("test-jwt-token");

        Map<String, String> body = Map.of("username", "admin", "password", TEST_PASSWORD);
        Result<Map<String, String>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(Result.CODE_SUCCESS);
        assertThat(result.getData()).containsEntry("token", "test-jwt-token");
        verify(tokenService).generateToken(1L, "admin", "*");
    }

    @Test
    void shouldRejectWrongPassword() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(BCRYPT_HASH);
        user.setStatus(0);
        when(sysUserService.getByUsername("admin")).thenReturn(user);

        Map<String, String> body = Map.of("username", "admin", "password", "wrong");
        Result<Map<String, String>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(Result.CODE_UNAUTHORIZED);
        assertThat(result.getData()).isNull();
        verify(tokenService, never()).generateToken(anyLong(), anyString(), anyString());
    }

    @Test
    void shouldRejectNonexistentUser() {
        when(sysUserService.getByUsername("nobody")).thenReturn(null);

        Map<String, String> body = Map.of("username", "nobody", "password", "any");
        Result<Map<String, String>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(Result.CODE_UNAUTHORIZED);
    }

    @Test
    void shouldRejectDisabledUser() {
        SysUserEntity user = new SysUserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(BCRYPT_HASH);
        user.setStatus(1); // disabled
        when(sysUserService.getByUsername("admin")).thenReturn(user);

        Map<String, String> body = Map.of("username", "admin", "password", TEST_PASSWORD);
        Result<Map<String, String>> result = controller.login(body);

        assertThat(result.getCode()).isEqualTo(Result.CODE_UNAUTHORIZED);
    }

    @Test
    void shouldGetUserInfo() {
        SystarUser currentUser = new SystarUser(1L, "admin", "*");
        SystarSecurityContext.set(currentUser);

        SysUserEntity userEntity = new SysUserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("admin");
        userEntity.setNickname("管理员");
        userEntity.setEmail("admin@example.com");
        userEntity.setPhone("13800138000");
        when(sysUserService.getUserById(1L)).thenReturn(userEntity);
        when(sysRoleService.getRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        when(sysRoleService.getRoleKeysByUserId(1L)).thenReturn(List.of("admin"));

        Result<Map<String, Object>> result = controller.getInfo();

        assertThat(result.getCode()).isEqualTo(Result.CODE_SUCCESS);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.getData().get("user");
        assertThat(user).containsEntry("userName", "admin");
        assertThat(user).containsEntry("nickName", "管理员");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) result.getData().get("roles");
        assertThat(roles).contains("admin");
    }

    @Test
    void shouldRejectGetInfoWhenNotAuthenticated() {
        SystarSecurityContext.clear();

        Result<Map<String, Object>> result = controller.getInfo();

        assertThat(result.getCode()).isEqualTo(Result.CODE_UNAUTHORIZED);
    }

    @Test
    void shouldLogoutSuccessfully() {
        Result<Void> result = controller.logout();

        assertThat(result.getCode()).isEqualTo(Result.CODE_SUCCESS);
    }
}
