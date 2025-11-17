package com.github.spud.tinystore.order.infrastructure.tenant;

public class TenantContext {
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    public static void setTenantId(String tenantId) { TENANT_ID.set(tenantId); }
    public static String getTenantId() { return TENANT_ID.get(); }
    public static void clearTenantId() { TENANT_ID.remove(); }

    public static void setUserId(String userId) { USER_ID.set(userId); }
    public static String getUserId() { return USER_ID.get(); }
    public static void clearUserId() { USER_ID.remove(); }

    public static void clearAll() { clearTenantId(); clearUserId(); }
}