package com.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
import com.hospital.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * J27「预约事务中让支付抛异常 → appointment/schedule 全部回滚（库中无残留）」（T12）。
 *
 * <h2>为什么单独一个测试类</h2>
 *
 * <p>它需要 {@code wechat.pay.mock-outcome=failure} 这个配置，而同一个 Spring 上下文里
 * 所有测试共享配置——把开关放进 {@link AppointmentIntegrationTest} 会让它的 J28/J29 全炸。
 * JUnit 5 的 {@code @Nested} 类<b>不允许自带上下文配置注解</b>（必须沿用外层配置），
 * 所以只能另开一个顶层类，用 {@code @TestPropertySource} 起第二个 Spring 上下文
 * （上下文缓存会各存一份，代价是这一类多一次启动）。
 *
 * <h2>这条测试到底在证明什么，以及为什么不能靠"读一下断言失败"糊过去</h2>
 *
 * <p>创建预约的写序是「占号 → 建单 → 发起支付」。支付在最后，所以它抛异常时
 * 前面两笔写都已经在同一个事务里发生过——本测试要证明的是它们<b>一起被数据库回滚了</b>，
 * 而不是被应用层补救掉了。因此三条计数必须与事前完全一致：
 * {@code appointment} 不多一行、{@code schedule.remaining_slots} 少的那一格回到原位、
 * {@code audit_log} 里那条先写的 CREATE_APPOINTMENT 也跟着消失
 * （审计切面在事务外层之内、{@code proceed()} 之前写流水，这正是 T04 J8 立起来的语义，
 * 卡片 A⑨ 复用同一条链路，所以这里连审计一起断言）。
 *
 * <p>错误码用 3001 {@code PAYMENT_FAILED}，且必须是 HTTP 200 + body.code
 * （{@code GlobalExceptionHandler} 对 BizException 不设状态码）。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "wechat.pay.mock-outcome=failure")
class AppointmentPayFailureTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;
    private String adminToken;
    private String patientToken;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
        adminToken = jwtUtil.generateToken(1L, "admin", "admin",
                List.of("dashboard", "schedule", "appointment"), List.of());
    }

    @AfterEach
    void cleanup() {
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM payment_record WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM appointment WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        jdbcTemplate.update("DELETE FROM appointment WHERE schedule_id IN "
                + "(SELECT id FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL 4 YEAR))");
        jdbcTemplate.update("DELETE FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL 4 YEAR)");
        jdbcTemplate.update("DELETE FROM audit_log WHERE target_type = 'appointment' "
                + "AND action IN ('CREATE_APPOINTMENT','APPOINTMENT_PAID')");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(), "本类只动自己造的探针行");
    }

    @Test
    void j27_payFailure_rollsBackAppointmentSlotAndAudit() throws Exception {
        LocalDate date = LocalDate.now().plusYears(5).plusDays(1);
        long scheduleId = createSchedule(1L, date, "MORNING", 10);
        long patientId = createPatient();
        int remainingBefore = remainingSlotsOf(scheduleId);
        int auditBefore = count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_APPOINTMENT'");

        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", scheduleId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("模拟支付发起失败")));

        assertEquals(remainingBefore, remainingSlotsOf(scheduleId),
                "⑥已经扣过的那个号必须随事务一起还回来");
        assertEquals(0, count("SELECT COUNT(*) FROM appointment WHERE schedule_id = ?", scheduleId),
                "⑤已经建的单不能留在库里");
        assertEquals(0, count("SELECT COUNT(*) FROM payment_record WHERE patient_id = ?", patientId),
                "支付失败也不该有任何流水（本卡走不到那一步，写在这里是防以后有人挪顺序）");
        assertEquals(auditBefore,
                count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_APPOINTMENT'"),
                "⑨先写的审计也一起回滚——这是同事务红线的直接推论，不是想当然");
    }

    @Test
    void j27_theSlotIsStillBookableAfterTheFailure_becauseNothingWasConsumed() throws Exception {
        // 回滚没做干净的最典型症状就是"号悄悄少了一格"：这里在同一个排班上把 remaining 数到底，
        // 证明失败的那一次真的一格都没占。
        LocalDate date = LocalDate.now().plusYears(5).plusDays(2);
        long scheduleId = createSchedule(1L, date, "MORNING", 1);
        long patientId = createPatient();

        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", scheduleId))))
                .andExpect(jsonPath("$.code").value(3001));

        assertEquals(1, remainingSlotsOf(scheduleId), "只剩的那一格还在原位");
        // 换个说法：如果回滚漏了，这一句会看到 0，而 0 意味着下一个患者明明有号却被拒。
        assertTrue(remainingSlotsOf(scheduleId) > 0);
    }

    // ============================================================
    // 辅助（与 AppointmentIntegrationTest 同一套约定，故意不抽公共父类：
    // 两个类的上下文配置不同，抽出来只会让"哪条测试跑在哪个配置下"更难看清）
    // ============================================================

    private long createSchedule(long doctorId, LocalDate date, String timeSlot, int totalSlots) throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", doctorId, "date", date.toString(),
                                "timeSlot", timeSlot, "totalSlots", totalSlots))))
                .andExpect(status().isOk())
                .andReturn());
        return ((Number) data.get("id")).longValue();
    }

    private long createPatient() throws Exception {
        patientToken = newPatientToken();
        Map<?, ?> data = expectData(mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "T12-回滚", "cardNo", randomCardNo(),
                                "idCard", "110101199003071234", "phone", randomPhone(), "relation", "SELF"))))
                .andExpect(status().isOk())
                .andReturn());
        long id = ((Number) data.get("id")).longValue();
        createdPatientIds.add(id);
        return id;
    }

    private String newPatientToken() throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "t12-j27-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        createdUserIds.add(((Number) data.get("userId")).longValue());
        return String.valueOf(data.get("token"));
    }

    private int remainingSlotsOf(long scheduleId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT remaining_slots FROM schedule WHERE id = ?", Integer.class, scheduleId);
        return value == null ? -1 : value;
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put("schedule", count("SELECT COUNT(*) FROM schedule"));
        snapshot.put("appointment", count("SELECT COUNT(*) FROM appointment"));
        snapshot.put("payment_record", count("SELECT COUNT(*) FROM payment_record"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("audit_appointment", count("SELECT COUNT(*) FROM audit_log WHERE target_type = 'appointment'"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String randomCardNo() {
        return "T12" + String.format("%07d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
    }

    private String randomPhone() {
        return "13" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000));
    }

    private String json(Map<?, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<?, ?> expectData(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        Map<?, ?> root = objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        return (Map<?, ?>) root.get("data");
    }
}
