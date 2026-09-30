package com.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.enums.Capability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * J5: 权限矩阵测试 — 4 角色 × 8 模块
 *
 * <p>T28 在本文件末尾补了「模块列表改从 role.permissions 读」的四条断言
 * （{@code resolveModules}）：那四行内置角色的 JSON 与下面这张静态矩阵必须永远等价，
 * 等价性就是靠这四条钉住的，不是靠人对着 V2 数一遍。
 */
class PermissionServiceTest {

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(new ObjectMapper());
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

        System.out.println("\n=== 能力矩阵 (4 角色 × " + Capability.values().length + " 能力) ===");
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

    // ========== T28：模块列表的真值来源改成 role.permissions ==========

    /**
     * V2:7 那一行 {@code '["*"]'} 必须与静态矩阵里 system 的全开等价。
     * 这条一旦红，说明有人改了 V2 的 JSON 却没同步 ROLE_MODULES（或反过来）。
     */
    @Test
    void resolveModules_wildcardEqualsAllModules() {
        assertEquals(PermissionService.ALL_MODULES,
                permissionService.resolveModules("system", "[\"*\"]"));
    }

    /**
     * 四个内置角色逐条核对：库里写的 JSON 与代码里的静态表必须一字不差等价。
     * 这四行是 V2__init_admin.sql 的原文，抄在这里当锚点。
     */
    @Test
    void resolveModules_coreRolesMatchStaticMatrix() {
        assertEquals(permissionService.getModules("admin"), permissionService.resolveModules("admin",
                "[\"dashboard\",\"schedule\",\"appointment\",\"finance\",\"report\",\"physical\",\"settings\",\"system\"]"));
        assertEquals(permissionService.getModules("doctor"), permissionService.resolveModules("doctor",
                "[\"dashboard\",\"schedule\",\"appointment\",\"report\"]"));
        assertEquals(permissionService.getModules("nurse"), permissionService.resolveModules("nurse",
                "[\"dashboard\",\"schedule\",\"appointment\",\"report\",\"physical\",\"system\"]"));
    }

    /** 自定义角色（'挂号员'）走库：静态表里根本没有它，读库才有模块，这是本卡「权限配置」的全部意义。 */
    @Test
    void resolveModules_customRoleComesFromJson() {
        List<String> modules = permissionService.resolveModules("registration-clerk",
                "[\"dashboard\",\"appointment\"]");
        assertEquals(List.of("dashboard", "appointment"), modules);
        assertTrue(permissionService.getModules("registration-clerk").isEmpty(),
                "静态表里没有自定义角色，回退路径必须给空，否则配了个寂寞");
    }

    /**
     * 脏数据的两条退路：<b>未知键丢弃</b>（不发明模块）、<b>整串解析不了退回静态表</b>
     * （不把一个人关在门外）。返回值断言写的是"退回后他本该有的模块"，不是"空列表"。
     */
    @Test
    void resolveModules_dropsUnknownKeys_andFallsBackOnBrokenJson() {
        assertEquals(List.of("dashboard"),
                permissionService.resolveModules("nurse", "[\"dashboard\",\"not_a_module\"]"));
        assertEquals(permissionService.getModules("nurse"),
                permissionService.resolveModules("nurse", "这不是 JSON"));
        assertEquals(permissionService.getModules("nurse"),
                permissionService.resolveModules("nurse", null));
    }
}
