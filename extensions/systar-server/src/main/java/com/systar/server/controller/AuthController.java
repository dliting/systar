package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.server.security.SystarSecurityContext;
import com.systar.server.security.SystarUser;
import com.systar.server.security.TokenService;
import com.systar.system.entity.SysUserEntity;
import com.systar.system.service.SysMenuService;
import com.systar.system.service.SysRoleService;
import com.systar.system.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final SysMenuService sysMenuService;
    private final TokenService tokenService;

    public AuthController(SysUserService sysUserService, SysRoleService sysRoleService,
                          SysMenuService sysMenuService, TokenService tokenService) {
        this.sysUserService = sysUserService;
        this.sysRoleService = sysRoleService;
        this.sysMenuService = sysMenuService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        SysUserEntity user = sysUserService.getByUsername(username);
        if (user == null || user.getStatus() != 0) {
            return Result.error(Result.CODE_UNAUTHORIZED, "Invalid username or password");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return Result.error(Result.CODE_UNAUTHORIZED, "Invalid username or password");
        }

        List<Long> roleIds = sysRoleService.getRoleIdsByUserId(user.getId());
        List<String> roleKeys = sysRoleService.getRoleKeysByUserId(user.getId());
        List<String> permissions = sysMenuService.getPermissionsByRoleIds(roleIds);
        boolean isAdmin = roleKeys.contains("admin");
        String permsStr = isAdmin ? "*"
                : permissions.stream().filter(Objects::nonNull).collect(Collectors.joining(","));

        String token = tokenService.generateToken(user.getId(), username, permsStr);

        SysUserEntity update = new SysUserEntity();
        update.setId(user.getId());
        update.setLoginTime(LocalDateTime.now());
        sysUserService.updateUser(update);

        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        return Result.success(data);
    }

    @GetMapping("/getInfo")
    public Result<Map<String, Object>> getInfo() {
        SystarUser current = SystarSecurityContext.get();
        if (current == null) {
            return Result.error(Result.CODE_UNAUTHORIZED, "Not authenticated");
        }

        SysUserEntity user = sysUserService.getUserById(current.getUserId());

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("userId", current.getUserId());
        userMap.put("userName", current.getUsername());
        userMap.put("nickName", user != null ? user.getNickname() : "");
        userMap.put("email", user != null ? user.getEmail() : "");
        userMap.put("phonenumber", user != null ? user.getPhone() : "");

        List<Long> roleIds = sysRoleService.getRoleIdsByUserId(current.getUserId());
        List<String> roleKeys = sysRoleService.getRoleKeysByUserId(current.getUserId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userMap);
        data.put("roles", roleKeys);
        data.put("permissions", new ArrayList<>(current.getPermissions()));
        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success();
    }
}
