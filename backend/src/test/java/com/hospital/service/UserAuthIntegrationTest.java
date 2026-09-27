package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
import com.hospital.entity.User;
import com.hospital.mapper.UserMapper;
import com.hospital.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T07 微信登录 + 用户管理的必做场景：J14 / J15 / J16。
 *
 * <p>需要本机 MySQL 与 Redis 都在跑（与 AuthIntegrationTest、CaptchaIntegrationTest 同一前提）。
 * 微信走 mock 模式（application.yml 里 wechat.appid 为空），openid 由 code 稳定派生，
 * 所以「同一 openid 重复登录」可以用同一个 code 复现出来。
 *
 * <p>本类建的 user 行在 {@link #cleanup()} 里**物理删除**：MyBatis-Plus 的 deleteById 是逻辑删除，
 * 只把 deleted 置 1，行还在，uk_openid 还占着，会在开发库里攒垃圾。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class UserAuthIntegrationTest {

    private static final Random RANDOM = new Random();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private WechatService wechatService;
    @Autowired private CryptoService cryptoService;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", id);
        }
        createdUserIds.clear();
    }

    // ============================================================
    // J14 微信授权登录 → user 创建 / openid 绑定 / token 签发
    // ============================================================

    @Test
    void j14_wechatLogin_createsUserBindsOpenidAndIssuesToken() throws Exception {
        assertTrue(wechatService.isMock(), "本测试假定 wechat.appid 未配置，走 mock 派生 openid");

        String code = "j14-" + UUID.randomUUID();
        Map<?, ?> data = wechatLogin(code);

        Long userId = ((Number) data.get("userId")).longValue();
        assertEquals(Boolean.TRUE, data.get("newUser"), "首次授权应新建 user");
        assertEquals(Boolean.FALSE, data.get("hasPhone"), "微信只给 openid，此时不该有手机号");

        String token = String.valueOf(data.get("token"));
        assertFalse(token.isBlank(), "应签发小程序 token");
        assertTrue(jwtUtil.validateToken(token), "签发的 token 必须能被自己验签通过");

        Claims claims = jwtUtil.parseToken(token);
        assertEquals(JwtUtil.PRINCIPAL_USER, claims.get(JwtUtil.CLAIM_PRINCIPAL, String.class),
                "必须是患者主体，否则这个 token 能打进管理后台");
        assertEquals(String.valueOf(userId), claims.getSubject());

        User row = userMapper.selectById(userId);
        assertNotNull(row, "user 行应已落库");
        assertEquals(wechatService.code2openid(code), row.getWechatOpenid(), "openid 应绑定到该 user");
        assertNull(row.getPhone(), "phone 必须是 NULL（V3 迁移把它改成可空就是为了这一刻）");
        assertNull(row.getNickname(), "code2session 不返回昵称，不该编一个假昵称塞进去");
    }

    // ============================================================
    // J15 同一 openid 重复登录 → 不重复创建 user
    // ============================================================

    @Test
    void j15_sameOpenidRepeatLogin_doesNotCreateSecondUser() throws Exception {
        String code = "j15-" + UUID.randomUUID();
        String openid = wechatService.code2openid(code);

        Long firstUserId = ((Number) wechatLogin(code).get("userId")).longValue();
        Map<?, ?> second = wechatLogin(code);
        Long secondUserId = ((Number) second.get("userId")).longValue();

        assertEquals(firstUserId, secondUserId, "同一 openid 第二次登录必须拿到同一个 user");
        assertEquals(Boolean.FALSE, second.get("newUser"), "第二次不是新用户");
        assertEquals(1, countByOpenid(openid), "库里这个 openid 只能有一行");
    }

    /** 反过来验：不同 openid 必须是不同的人，否则 J15 只是"永远返回同一行"的假通过 */
    @Test
    void j15_differentOpenid_createsSeparateUsers() throws Exception {
        String codeA = "j15a-" + UUID.randomUUID();
        String codeB = "j15b-" + UUID.randomUUID();

        Long userIdA = ((Number) wechatLogin(codeA).get("userId")).longValue();
        Long userIdB = ((Number) wechatLogin(codeB).get("userId")).longValue();

        assertNotEquals(userIdA, userIdB);
        assertEquals(1, countByOpenid(wechatService.code2openid(codeA)));
        assertEquals(1, countByOpenid(wechatService.code2openid(codeB)));
    }

    // ============================================================
    // J16 绑定手机号 → user.phone 更新（且必须密文落库）
    // ============================================================

    @Test
    void j16_bindPhone_updatesUserPhoneAsCiphertext() throws Exception {
        String token = String.valueOf(wechatLogin("j16-" + UUID.randomUUID()).get("token"));
        Long userId = userIdOf(token);
        String phone = randomPhone();

        mockMvc.perform(post("/user/sms-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 从 Redis 侧读回真实验证码，避免测试自己"猜"一个而变成空验证
        String code = redisTemplate.opsForValue().get(SmsCodeService.redisKey(phone));
        assertNotNull(code, "验证码应写进 Redis");
        assertEquals(6, code.length(), "应是 6 位数字码");

        mockMvc.perform(post("/user/phone")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasPhone").value(true))
                // 回给前端的手机号必须打码，不能因为"是本人"就给全号
                .andExpect(jsonPath("$.data.phone").value(maskOf(phone)));

        User row = userMapper.selectById(userId);
        assertNotNull(row.getPhone(), "user.phone 应已更新");
        assertNotEquals(phone, row.getPhone(), "不能明文落库");
        assertFalse(row.getPhone().contains(phone), "密文里也不该出现明文片段");
        assertEquals(phone, cryptoService.decrypt(row.getPhone()), "解密后必须还原成原手机号");
    }

    @Test
    void j16_smsCodeIsOneShot_wrongOrReusedCodeRejected() throws Exception {
        String token = String.valueOf(wechatLogin("j16b-" + UUID.randomUUID()).get("token"));
        String phone = randomPhone();

        mockMvc.perform(post("/user/sms-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone))))
                .andExpect(status().isOk());
        String code = redisTemplate.opsForValue().get(SmsCodeService.redisKey(phone));

        // 猜错：4005，并且码被作废
        mockMvc.perform(post("/user/phone")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone, "code", "000000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4005));
        assertNull(redisTemplate.opsForValue().get(SmsCodeService.redisKey(phone)),
                "验错也要删码，否则可以逐位爆破");

        // 再用真码也必须是 4005
        mockMvc.perform(post("/user/phone")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone, "code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(4005));
    }

    @Test
    void j16_sendSmsCodeTwiceWithin60s_isRateLimited() throws Exception {
        String token = String.valueOf(wechatLogin("j16c-" + UUID.randomUUID()).get("token"));
        String phone = randomPhone();

        mockMvc.perform(post("/user/sms-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/user/sms-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("phone", phone))))
                .andExpect(status().isOk())
                // 60 秒内对同一号码重复发码必须被挡，否则这个接口就是免费短信炸弹
                .andExpect(jsonPath("$.code").value(4006));
    }

    // ============================================================
    // 主体隔离：本卡把 SecurityConfig 从 anyRequest().authenticated() 收紧成按角色分流，
    // 两个方向都要有回归网，否则以后谁改回去都不会有人发现。
    // ============================================================

    @Test
    void patientTokenCannotReachAdminEndpoints() throws Exception {
        String token = String.valueOf(wechatLogin("iso-p-" + UUID.randomUUID()).get("token"));

        mockMvc.perform(get("/demo/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void adminTokenCannotReachMiniProgramEndpoints() throws Exception {
        String adminToken = jwtUtil.generateToken(1L, "admin", "admin", List.of("dashboard"), List.of());

        mockMvc.perform(get("/user/profile").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void anonymousCannotReachEitherSide() throws Exception {
        // 未认证仍是 401（不是 403），小程序 utils/request.js 靠 401 触发跳登录页
        mockMvc.perform(get("/user/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/demo/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 助手
    // ============================================================

    private Map<?, ?> wechatLogin(String code) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> root = objectMapper.readValue(body, Map.class);
        Map<?, ?> data = (Map<?, ?>) root.get("data");
        createdUserIds.add(((Number) data.get("userId")).longValue());
        return data;
    }

    private Long userIdOf(String token) {
        return Long.valueOf(jwtUtil.parseToken(token).getSubject());
    }

    private int countByOpenid(String openid) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getWechatOpenid, openid));
        return count == null ? 0 : count.intValue();
    }

    /** 每次跑用新号码：60 秒频控键在 Redis 里，复用固定号码会让重跑变成假失败 */
    private static String randomPhone() {
        return "139" + String.format("%08d", RANDOM.nextInt(100_000_000));
    }

    private static String maskOf(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
    }
}
