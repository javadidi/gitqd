package com.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证码 Redis 往返 + 登录链路（T03 第 2 项「账号密码 + 验证码」，第 6 项角色落地页）。
 * 需要本机 MySQL 与 Redis 都在跑，与 AuthIntegrationTest 的前提一致。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class CaptchaIntegrationTest {

    private static final String SEEDED_PASSWORD = "admin123";

    @Autowired private CaptchaService captchaService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    /** 从 Redis 侧读回真实答案，避免测试自己"猜"出验证码而变成空验证 */
    private String storedCode(String captchaKey) {
        return redisTemplate.opsForValue().get(CaptchaService.KEY_PREFIX + captchaKey);
    }

    @Test
    void generate_storesCodeInRedisWithTtl() {
        CaptchaService.Captcha captcha = captchaService.generate();

        assertNotNull(captcha.captchaKey());
        assertFalse(captcha.imageBase64().isBlank(), "应返回 base64 图片");

        String code = storedCode(captcha.captchaKey());
        assertNotNull(code, "验证码答案应写进 Redis");
        assertEquals(4, code.length());

        // getExpire(K) 返回剩余秒数（Long），不是 Duration
        Long ttlSeconds = redisTemplate.getExpire(CaptchaService.KEY_PREFIX + captcha.captchaKey());
        assertNotNull(ttlSeconds);
        assertTrue(ttlSeconds > 0, "必须带 TTL，否则验证码永久有效，实际=" + ttlSeconds);
        assertTrue(ttlSeconds <= CaptchaService.TTL.toSeconds(),
                "TTL 不应超过设定值，实际=" + ttlSeconds);
    }

    @Test
    void verifyAndConsume_correctCode_passesOnceThenDies() {
        CaptchaService.Captcha captcha = captchaService.generate();
        String code = storedCode(captcha.captchaKey());

        assertTrue(captchaService.verifyAndConsume(captcha.captchaKey(), code));
        assertNull(storedCode(captcha.captchaKey()), "校验后 Redis 键应被删除");
        assertFalse(captchaService.verifyAndConsume(captcha.captchaKey(), code),
                "同一张图第二次必须失效");
    }

    @Test
    void verifyAndConsume_wrongCode_failsAndStillConsumes() {
        CaptchaService.Captcha captcha = captchaService.generate();
        String real = storedCode(captcha.captchaKey());
        String wrong = real.charAt(0) == 'A' ? "BBBB" : "AAAA";
        assertNotEquals(real, wrong);

        assertFalse(captchaService.verifyAndConsume(captcha.captchaKey(), wrong));
        assertNull(storedCode(captcha.captchaKey()), "猜错也要作废，防止逐位爆破");
    }

    @Test
    void verifyAndConsume_unknownOrBlankKey_fails() {
        assertFalse(captchaService.verifyAndConsume("no-such-key", "AAAA"));
        assertFalse(captchaService.verifyAndConsume(null, "AAAA"));
        assertFalse(captchaService.verifyAndConsume("", ""));
    }

    @Test
    void login_wrongCaptcha_returns4003_evenWithCorrectPassword() throws Exception {
        // 密码是对的，验证码是错的：仍应被拦在 4003，不能落到查库那一步
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("admin", SEEDED_PASSWORD, "deadbeefdeadbeefdeadbeefdeadbeef", "ZZZZ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4003))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void login_blankCaptcha_returns400() throws Exception {
        // @NotBlank 走 GlobalExceptionHandler，HTTP 状态与 body code 都是 400
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("admin", SEEDED_PASSWORD, "", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void login_validCaptcha_returnsTokenAndRoleLandingPage() throws Exception {
        // T03 第 6 项：管理员→/dashboard，医生→/schedule，护士→/appointments
        assertLogin("admin", "admin", "/dashboard", List.of("finance", "settings"));
        assertLogin("system", "system", "/dashboard", List.of("finance", "settings"));
        assertLogin("doctor", "doctor", "/schedule", List.of());
        assertLogin("nurse", "nurse", "/appointments", List.of());
    }

    @Test
    void login_nurseHasNoFinanceModule_doctorHasNoSettingsModule() throws Exception {
        // T06 人工验收「护士无收费、医生无设置」的机器版
        assertModulesAbsent("nurse", List.of("finance", "settings"));
        assertModulesAbsent("doctor", List.of("finance", "settings"));
    }

    private void assertLogin(String username, String expectedRole, String expectedLanding,
                             List<String> expectModules) throws Exception {
        Map<?, ?> data = loginData(username, freshCaptcha());

        assertEquals(username, data.get("username"));
        assertEquals(expectedRole, data.get("role"));
        assertEquals(expectedLanding, data.get("landingPage"),
                username + " 的落地页不符合任务卡 T03 第 6 项");
        assertFalse(String.valueOf(data.get("token")).isBlank(), "应签发 token");

        List<?> modules = (List<?>) data.get("modules");
        for (String module : expectModules) {
            assertTrue(modules.contains(module), username + " 的 modules 应包含 " + module);
        }
    }

    private void assertModulesAbsent(String username, List<String> absent) throws Exception {
        List<?> modules = (List<?>) loginData(username, freshCaptcha()).get("modules");
        for (String module : absent) {
            assertFalse(modules.contains(module), username + " 不应看到 " + module + " 模块");
        }
    }

    private record FreshCaptcha(String key, String code) {}

    /** 每次登录都要新取一张图：验证码一次性，复用必失败 */
    private FreshCaptcha freshCaptcha() {
        CaptchaService.Captcha captcha = captchaService.generate();
        return new FreshCaptcha(captcha.captchaKey(), storedCode(captcha.captchaKey()));
    }

    private Map<?, ?> loginData(String username, FreshCaptcha captcha) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, SEEDED_PASSWORD, captcha.key(), captcha.code())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> root = objectMapper.readValue(body, Map.class);
        return (Map<?, ?>) root.get("data");
    }

    private String json(String username, String password, String captchaKey, String captchaCode) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("password", password);
        payload.put("captchaKey", captchaKey);
        payload.put("captchaCode", captchaCode);
        return objectMapper.writeValueAsString(payload);
    }
}
