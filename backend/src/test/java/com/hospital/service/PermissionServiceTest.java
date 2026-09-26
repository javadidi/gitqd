package com.hospital.service;

import com.hospital.enums.Capability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * J5: 权限矩阵测试 — 4 角色 × 8 模块
 */
class PermissionServiceTest {

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService();
    }

    // ========== 模块可见性矩阵 ==========

    @Test
    void system_canSeeAllModules() {
        for (String module : PermissionService.ALL_MODULES) {
            assertTrue(permissionService.hasModule("system", module),
                    "system 应该能访问模块: " + module);
        }
    }

    @Test
    void admin_canSeeAllModules() {
        for (String module : PermissionService.ALL_MODULES) {
            assertTrue(permissionService.hasModule("admin", module),
                    "admin 应该能访问模块: " + module);
        }
    }

    @Test
    void doctor_canOnlySeeScheduleAppointmentReportDashboard() {
        assertTrue(permissionService.hasModule("doctor", "dashboard"));
        assertTrue(permissionService.hasModule("doctor", "schedule"));
        assertTrue(permissionService.hasModule("doctor", "appointment"));
        assertTrue(permissionService.hasModule("doctor", "report"));

        assertFalse(permissionService.hasModule("doctor", "finance"),
                "doctor 不应访问 finance");
        assertFalse(permissionService.hasModule("doctor", "physical"),
                "doctor 不应访问 physical");
        assertFalse(permissionService.hasModule("doctor", "settings"),
                "doctor 不应访问 settings");
        assertFalse(permissionService.hasModule("doctor", "system"),
                "doctor 不应访问 system");
    }

    @Test
    void nurse_noFinanceAccess() {
        assertFalse(permissionService.hasModule("nurse", "finance"),
                "nurse 不应访问 finance");

        assertTrue(permissionService.hasModule("nurse", "dashboard"));
        assertTrue(permissionService.hasModule("nurse", "schedule"));
        assertTrue(permissionService.hasModule("nurse", "appointment"));
        assertTrue(permissionService.hasModule("nurse", "report"));
        assertTrue(permissionService.hasModule("nurse", "physical"));
        assertTrue(permissionService.hasModule("nurse", "system"));
    }

    // ========== 能力权限矩阵 ==========

    @Test
    void system_hasAllCaps() {
        for (Capability cap : Capability.values()) {
            assertTrue(permissionService.hasCap("system", cap),
                    "system 应该有能力: " + cap);
        }
    }

    @Test
    void admin_hasAllCaps() {
        for (Capability cap : Capability.values()) {
            assertTrue(permissionService.hasCap("admin", cap),
                    "admin 应该有能力: " + cap);
        }
    }

    @Test
    void doctor_hasNoCaps() {
        for (Capability cap : Capability.values()) {
            assertFalse(permissionService.hasCap("doctor", cap),
                    "doctor 不应该有能力: " + cap);
        }
    }

    @Test
    void nurse_hasNoCaps() {
        for (Capability cap : Capability.values()) {
            assertFalse(permissionService.hasCap("nurse", cap),
                    "nurse 不应该有能力: " + cap);
        }
    }

    // ========== 4×8 完整矩阵打印 ==========

    @Test
    void printFullMatrix() {
        String[] roles = {"system", "admin", "doctor", "nurse"};
        System.out.println("\n=== J5 权限矩阵 (4 角色 × 8 模块) ===");
        System.out.printf("%-10s", "角色");
        for (String m : PermissionService.ALL_MODULES) {
            System.out.printf("%-12s", m);
        }
        System.out.println();
        System.out.println("-".repeat(106));

        for (String role : roles) {
            System.out.printf("%-10s", role);
            for (String module : PermissionService.ALL_MODULES) {
                System.out.printf("%-12s", permissionService.hasModule(role, module) ? "Y" : "N");
            }
            System.out.println();
        }

        System.out.println("\n=== 能力矩阵 (4 角色 × 3 能力) ===");
        System.out.printf("%-10s", "角色");
        for (Capability cap : Capability.values()) {
            System.out.printf("%-16s", cap.name());
        }
        System.out.println();
        System.out.println("-".repeat(58));

        for (String role : roles) {
            System.out.printf("%-10s", role);
            for (Capability cap : Capability.values()) {
                System.out.printf("%-16s", permissionService.hasCap(role, cap) ? "Y" : "N");
            }
            System.out.println();
        }
    }
}
