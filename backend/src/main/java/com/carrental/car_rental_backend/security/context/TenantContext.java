package com.carrental.car_rental_backend.security.context;

import java.util.UUID;

public class TenantContext {
  private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
  private static final ThreadLocal<UUID> CURRENT_BRANCH = new ThreadLocal<>();
  private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

  // --- TENANT ID ---
    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    // --- ACTIVE BRANCH ID ---
    public static void setBranchId(UUID branchId) {
        CURRENT_BRANCH.set(branchId);
    }

    public static UUID getBranchId() {
        return CURRENT_BRANCH.get();
    }

    // --- ROLE ---
    public static void setRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

  public static void clear() {
    CURRENT_TENANT.remove();
    CURRENT_BRANCH.remove();
    CURRENT_ROLE.remove();
  }
}
