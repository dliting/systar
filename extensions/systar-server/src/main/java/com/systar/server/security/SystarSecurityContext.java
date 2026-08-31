package com.systar.server.security;

public final class SystarSecurityContext {
    private static final ThreadLocal<SystarUser> USER = new ThreadLocal<>();
    private SystarSecurityContext() {}
    public static void set(SystarUser user) { USER.set(user); }
    public static SystarUser get() { return USER.get(); }
    public static void clear() { USER.remove(); }
}
