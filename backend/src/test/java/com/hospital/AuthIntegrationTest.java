package com.hospital;

import com.hospital.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * J6: 接口级权限测试 — token 调接口验证 403/200
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String token(String role, List<String> modules, List<String> caps) {
        return jwtUtil.generateToken(1L, role, role, modules, caps);
    }

    @Test
    void nurse_financeRefunds_returns403() throws Exception {
        // nurse 没有 finance 模块权限，但我们用 admin token（有 finance）
        // 这里测试的是：用 doctor token 调 /settings → 应该被 Security 放行（已认证）
        // 但实际模块权限由前端控制，后端 @RequireCap 控制能力权限
        // 所以 J6 重点测试 @RequireCap 注解的接口

        // doctor 没有 APPROVE_REFUND 能力 → 403
        String doctorToken = token("doctor",
                Arrays.asList("dashboard", "schedule", "appointment", "report"),
                Collections.emptyList());

        mockMvc.perform(get("/demo/approve-refund")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void nurse_editSettings_returns403() throws Exception {
        String nurseToken = token("nurse",
                Arrays.asList("dashboard", "schedule", "appointment", "report", "physical", "system"),
                Collections.emptyList());

        mockMvc.perform(get("/demo/edit-settings")
                        .header("Authorization", "Bearer " + nurseToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void admin_approveRefund_returns200() throws Exception {
        String adminToken = token("admin",
                Arrays.asList("dashboard", "schedule", "appointment", "finance", "report", "physical", "settings", "system"),
                Arrays.asList("APPROVE_REFUND", "EDIT_SETTINGS", "MANAGE_DOCTOR"));

        mockMvc.perform(get("/demo/approve-refund")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/demo/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/demo/dashboard")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void admin_manageDoctor_returns200() throws Exception {
        String adminToken = token("admin",
                Arrays.asList("dashboard", "schedule", "appointment", "finance", "report", "physical", "settings", "system"),
                Arrays.asList("APPROVE_REFUND", "EDIT_SETTINGS", "MANAGE_DOCTOR"));

        mockMvc.perform(get("/demo/manage-doctor")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void doctor_manageDoctor_returns403() throws Exception {
        String doctorToken = token("doctor",
                Arrays.asList("dashboard", "schedule", "appointment", "report"),
                Collections.emptyList());

        mockMvc.perform(get("/demo/manage-doctor")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4001));
    }
}
