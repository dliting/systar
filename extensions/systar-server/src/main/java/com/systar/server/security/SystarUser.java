package com.systar.server.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class SystarUser {
    private final Long userId;
    private final String username;
    private final Set<String> permissions;

    public SystarUser(Long userId, String username, String permissionsStr) {
        this.userId = userId;
        this.username = username;
        if (permissionsStr == null || permissionsStr.isEmpty()) {
            this.permissions = Collections.emptySet();
        } else if ("*".equals(permissionsStr)) {
            this.permissions = Collections.singleton("*");
        } else {
            this.permissions = Arrays.stream(permissionsStr.split(","))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Set<String> getPermissions() { return permissions; }
    public boolean isAdmin() { return permissions.contains("*"); }
    public boolean hasPermission(String permission) {
        return isAdmin() || permissions.contains(permission);
    }
}
