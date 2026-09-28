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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 预约管理与退号（T13）：J31 退号三件套、J32 已就诊不可退，外加列表/详情的归属与筛选。
 *
 * <p>单独一个测试类（T12 的 {@code AppointmentIntegrationTest} 已经 950 行）：
 * 本卡的写路径换了 {@code refund_record} 这张财务表，收尾要多清一张表、
 * 基线计数也要多一项，混在 T12 那个类里会让两边的自净口径互相牵制。
 *
 * <h2>探针数据怎么标记</h2>
 * 沿用 T11/T12 的口径：探针排班一律"今天 + 5 年"（种子只覆盖 ±7 天），
 * 收尾按 {@code date >= CURDATE() + INTERVAL 4 YEAR} 扫。
 * 删除顺序是叶子到根：退款单 → 支付流水 → 预约 → 排班 → 就诊人 → 账号 → 审计，
 * 每一步的 WHERE 都靠上一层认行，SQL 里零中文字面量（mysql/JDBC 的 GBK 老坑）。
 *
 * <p>业务错误一律 HTTP 200 + body.code；401/403 才带 HTTP 状态码。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class AppointmentManageIntegrationTest {

    private static final int PROBE_YEAR_OFFSET = 5;
    private static final List<String> AUDIT_ACTIONS =
            List.of("CREATE_APPOINTMENT", "APPOINTMENT_PAID", "CANCEL_APPOINTMENT");

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
    void cleanupAndAssertNothingLeaks() {
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM refund_record WHERE related_type = 'APPOINTMENT' "
                    + "AND related_id IN (SELECT id FROM appointment WHERE patient_id = ?)", patientId);
            jdbcTemplate.update("DELETE FROM payment_record WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM appointment WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        jdbcTemplate.update("DELETE FROM appointment WHERE schedule_id IN "
                + "(SELECT id FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL 4 YEAR))");
        jdbcTemplate.update("DELETE FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL 4 YEAR)");
        jdbcTemplate.update("DELETE FROM audit_log WHERE target_type = 'appointment' AND action IN ('"
                + String.join("','", AUDIT_ACTIONS) + "')");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(),
                "T13 只动自己造的探针行：七张表的计数必须回到基线");
    }

    // ============================================================
    // J31 退号 → 预约状态 CANCELLED + 号源恢复 + 退款记录
    // ============================================================

    @Test
    void j31_cancelConfirmed_cancelsRestoresSlotAndBooksPendingRefund() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(1), "MORNING", 5);
        long patientId = createPatient("T13-甲", "SELF");
        Map<?, ?> booked = book(patientId, scheduleId);
        long appointmentId = ((Number) booked.get("id")).longValue();
        pay(appointmentId);
        assertEquals(4, remainingSlotsOf(scheduleId), "挂号扣掉一个号");

        Map<?, ?> data = cancel(appointmentId, "临时有事");

        assertEquals("CANCELLED", data.get("status"));
        assertEquals(true, data.get("refundRequired"), "已付过钱的单退号必须挂退款申请");
        assertEquals(5000L, ((Number) data.get("refundFen")).longValue());
        assertEquals("PENDING", data.get("refundStatus"),
                "卡片 479 行「退款需审核」——只挂单，不是已退款");
        assertTrue(String.valueOf(data.get("refundNo")).matches("^TK\\d{8}-\\d{4}$"),
                "退款单号前缀出处 SerialType.TK，实际：" + data.get("refundNo"));

        Map<String, Object> row = rawAppointment(appointmentId);
        assertEquals("CANCELLED", row.get("status"));
        assertEquals(5, remainingSlotsOf(scheduleId), "号源回到退号前");

        List<Map<String, Object>> refunds = jdbcTemplate.queryForList(
                "SELECT * FROM refund_record WHERE related_type = 'APPOINTMENT' AND related_id = ?",
                appointmentId);
        assertEquals(1, refunds.size());
        Map<String, Object> refund = refunds.get(0);
        assertEquals(5000L, ((Number) refund.get("amount_fen")).longValue(),
                "金额取 appointment.fee_fen，不接受任何外部传入");
        assertEquals("PENDING", refund.get("status"));
        assertNull(refund.get("reviewer_id"), "没人审过，reviewer_id 必须留空");
        assertEquals("临时有事", refund.get("reason"));
    }

    @Test
    void j31_cancelWritesAuditWithTargetAndReason() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(2), "MORNING", 5);
        long patientId = createPatient("T13-乙", "SELF");
        Map<?, ?> booked = book(patientId, scheduleId);
        long appointmentId = ((Number) booked.get("id")).longValue();
        pay(appointmentId);

        cancel(appointmentId, "医生换班");

        Map<String, Object> audit = latestAudit("CANCEL_APPOINTMENT");
        assertEquals("PATIENT", audit.get("operator_type"));
        assertEquals(appointmentId, ((Number) audit.get("target_id")).longValue(),
                "@AuditTarget 标在 appointmentId 上");
        assertEquals("appointment", audit.get("target_type"));
        assertEquals("医生换班", audit.get("reason"));
    }

    @Test
    void j31_secondCancelRejectedAndWritesNoSecondRefundOrSlot() throws Exception {
        // 退号是"改状态 + 还号 + 挂退款单"三件套，重复执行任何一件都是错账：
        // 号还两次 = 凭空多一个名额；退款单两张 = 审核时会重复退钱。
        long scheduleId = createSchedule(1L, probeDate(3), "MORNING", 5);
        long patientId = createPatient("T13-丙", "SELF");
        Map<?, ?> booked = book(patientId, scheduleId);
        long appointmentId = ((Number) booked.get("id")).longValue();
        pay(appointmentId);
        cancel(appointmentId, "第一次");
        int slotAfterFirst = remainingSlotsOf(scheduleId);
        int refundAfterFirst = count("SELECT COUNT(*) FROM refund_record WHERE related_id = ?", appointmentId);

        mockMvc.perform(post("/user/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2006));

        assertEquals(slotAfterFirst, remainingSlotsOf(scheduleId), "号源不能被还两次");
        assertEquals(refundAfterFirst,
                count("SELECT COUNT(*) FROM refund_record WHERE related_id = ?", appointmentId),
                "退款单不能出现第二条");
    }

    @Test
    void j31_cancelPendingPayment_writesNoRefund() throws Exception {
        // 待支付的单从没收到过钱。卡片 477 行的"退还挂号费"对它不适用，
        // 挂一张假退款单会直接污染 T26 的对账（refund_record 无 deleted 列，只增不删）。
        long scheduleId = createSchedule(1L, probeDate(4), "MORNING", 5);
        long patientId = createPatient("T13-丁", "SELF");
        Map<?, ?> booked = book(patientId, scheduleId);
        long appointmentId = ((Number) booked.get("id")).longValue();

        Map<?, ?> data = cancel(appointmentId, null);
        assertEquals(false, data.get("refundRequired"));
        assertNull(data.get("refundNo"));
        assertEquals(0, count("SELECT COUNT(*) FROM refund_record WHERE related_id = ?", appointmentId));
        assertEquals(5, remainingSlotsOf(scheduleId));
    }

    // ============================================================
    // J32 已就诊预约退号 → 被拒
    // ============================================================

    @Test
    void j32_cancelCompletedAppointment_rejectedAndNothingChanges() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(5), "MORNING", 5);
        long patientId = createPatient("T13-戊", "SELF");
        Map<?, ?> booked = book(patientId, scheduleId);
        long appointmentId = ((Number) booked.get("id")).longValue();
        pay(appointmentId);
        // COMPLETED 由后续卡（就诊完成）写入，本卡没有任何接口能产生它，只能裸 SQL 造。
        jdbcTemplate.update("UPDATE appointment SET status = 'COMPLETED' WHERE id = ?", appointmentId);

        mockMvc.perform(post("/user/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "看完想退钱"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2006))
                .andExpect(jsonPath("$.message").value("已就诊的预约不可退号"));

        assertEquals("COMPLETED", rawAppointment(appointmentId).get("status"));
        assertEquals(4, remainingSlotsOf(scheduleId), "被拒的退号不许动号源");
        assertEquals(0, count("SELECT COUNT(*) FROM refund_record WHERE related_id = ?", appointmentId));
    }

    // ============================================================
    // 归属与角色隔离
    // ============================================================

    @Test
    void cancel_someoneElsesAppointment_returns2004AndStaysConfirmed() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(7), "MORNING", 5);
        long patientId = createPatient("T13-己", "SELF");
        long appointmentId = ((Number) book(patientId, scheduleId).get("id")).longValue();

        String intruder = newPatientToken("t13-intruder");
        mockMvc.perform(post("/user/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2004));

        assertEquals("PENDING_PAYMENT", rawAppointment(appointmentId).get("status"));
        assertEquals(4, remainingSlotsOf(scheduleId));
    }

    @Test
    void staffAndAnonymousCannotReachCancelListOrDetail() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(8), "MORNING", 5);
        long patientId = createPatient("T13-庚", "SELF");
        long appointmentId = ((Number) book(patientId, scheduleId).get("id")).longValue();

        String staff = jwtUtil.generateToken(3L, "doctor", "doctor",
                List.of("dashboard", "schedule", "appointment"), List.of());
        for (String path : List.of("/user/appointments", "/user/appointments/" + appointmentId)) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + staff))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(4001));
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/user/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/user/appointments/" + appointmentId + "/cancel"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 列表与详情（卡片 475–476 行）
    // ============================================================

    @Test
    void list_returnsOwnAppointmentsNewestFirstAndGroupsNothing() throws Exception {
        long patientId = createPatient("T13-辛", "SELF");
        long near = createSchedule(1L, probeDate(9), "MORNING", 5);
        long far = createSchedule(1L, probeDate(30), "AFTERNOON", 5);
        Map<?, ?> a = book(patientId, near);
        Map<?, ?> b = book(patientId, far);

        List<?> rows = (List<?>) expectRoot(getJson("/user/appointments", patientToken)).get("data");
        assertEquals(2, rows.size(), "只回自己的两条");
        List<String> times = rows.stream().map(r -> String.valueOf(((Map<?, ?>) r).get("appointmentTime"))).toList();
        assertEquals(true, times.get(0).compareTo(times.get(1)) > 0,
                "按预约时间倒序（最近发生的在前），实际：" + times);
        Map<?, ?> first = (Map<?, ?>) rows.get(0);
        assertEquals("T13-辛", first.get("patientName"));
        assertEquals("张伟", first.get("doctorName"));
        assertEquals("消化内科", first.get("departmentName"), "列表也要能看出科室（PRD 80 行同款展示项）");
        assertEquals("AFTERNOON", first.get("timeSlot"), "回码值不回中文，与 T10/T12 一致");
        assertEquals("PENDING_PAYMENT", first.get("status"),
                "后端不做分组归类，只回原码；待就诊/已完成/已取消的划分在前端");
        assertTrue(List.of(a.get("id"), b.get("id")).contains(first.get("id")));
    }

    @Test
    void list_filtersByStatus_andEmptyForUserWithoutPatients() throws Exception {
        long patientId = createPatient("T13-壬", "SELF");
        long scheduleId = createSchedule(1L, probeDate(10), "MORNING", 5);
        long appointmentId = ((Number) book(patientId, scheduleId).get("id")).longValue();
        pay(appointmentId);

        List<?> confirmed = (List<?>) expectRoot(getJson(
                "/user/appointments?status=CONFIRMED", patientToken)).get("data");
        assertEquals(1, confirmed.size());
        assertEquals("CONFIRMED", ((Map<?, ?>) confirmed.get(0)).get("status"));

        List<?> pending = (List<?>) expectRoot(getJson(
                "/user/appointments?status=PENDING_PAYMENT", patientToken)).get("data");
        assertEquals(0, pending.size(), "已付掉的那条不该出现在待支付里");

        String lonely = newPatientToken("t13-lonely");
        Object empty = expectRoot(getJson("/user/appointments", lonely)).get("data");
        assertEquals(List.of(), empty, "一个就诊人也没有 → 空数组不是 null");
    }

    @Test
    void detail_returnsOwnAndRejectsOthers() throws Exception {
        long patientId = createPatient("T13-癸", "SELF");
        long scheduleId = createSchedule(1L, probeDate(11), "MORNING", 5);
        Map<?, ?> booked = book(patientId, scheduleId);
        long appointmentId = ((Number) booked.get("id")).longValue();

        Map<?, ?> data = expectData(getJson("/user/appointments/" + appointmentId, patientToken));
        assertEquals(booked.get("orderNo"), data.get("orderNo"));
        assertEquals("T13-癸", data.get("patientName"));

        String intruder = newPatientToken("t13-intruder2");
        mockMvc.perform(get("/user/appointments/" + appointmentId)
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2004));   // 不是你的和没这条同码，不给枚举机会

        mockMvc.perform(get("/user/appointments/9999999")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(jsonPath("$.code").value(2004));
    }

    // ============================================================
    // 辅助
    // ============================================================

    private LocalDate probeDate(int plusDays) {
        return LocalDate.now().plusYears(PROBE_YEAR_OFFSET).plusDays(plusDays);
    }

    private long createSchedule(long doctorId, LocalDate date, String timeSlot, int totalSlots) throws Exception {
        Map<?, ?> data = expectData(postJson("/admin/schedules", adminToken, Map.of(
                "doctorId", doctorId, "date", date.toString(), "timeSlot", timeSlot, "totalSlots", totalSlots)));
        return ((Number) data.get("id")).longValue();
    }

    private long createPatient(String name, String relation) throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t13-owner");
        }
        Map<?, ?> data = expectData(postJson("/user/patients", patientToken, Map.of(
                "name", name, "cardNo", randomCardNo(), "idCard", "110101199003071234",
                "phone", "13900001234", "relation", relation)));
        long id = ((Number) data.get("id")).longValue();
        createdPatientIds.add(id);
        return id;
    }

    private Map<?, ?> book(long patientId, long scheduleId) throws Exception {
        return expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientId, "scheduleId", scheduleId)));
    }

    private void pay(long appointmentId) throws Exception {
        expectData(postJson("/user/appointments/" + appointmentId + "/pay", patientToken, Map.of()));
    }

    /**
     * 退号请求。
     *
     * <p>{@code reason} 用 {@code .param()} 传而不是拼进 URL：<b>MockMvc 不会对 URL 模板里的
     * 百分号编码做解码</b>，拼进去就会把 {@code %E4%B8%B4...} 原样当成参数值落库
     * （第一版就是这么红了两条）。真实 HTTP 里 Tomcat 会正常解码——
     * T11 的真 HTTP 验收用 {@code SELECT HEX(reason)} 比对过中文字节，那边是对的。
     * 所以这是测试通道的差异，不是业务缺陷。
     */
    private Map<?, ?> cancel(long appointmentId, String reason) throws Exception {
        var request = post("/user/appointments/" + appointmentId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of()));
        if (patientToken != null) {
            request.header("Authorization", "Bearer " + patientToken);
        }
        if (reason != null) {
            request.param("reason", reason);
        }
        return expectData(mockMvc.perform(request).andReturn());
    }

    private String newPatientToken(String tag) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", tag + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        createdUserIds.add(((Number) data.get("userId")).longValue());
        return String.valueOf(data.get("token"));
    }

    private MvcResult postJson(String path, String token, Map<?, ?> body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult getJson(String path, String token) throws Exception {
        var request = get(path);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private Map<?, ?> expectRoot(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    private Map<?, ?> expectData(MvcResult result) throws Exception {
        Map<?, ?> root = expectRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }

    private Map<String, Object> rawAppointment(long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM appointment WHERE id = ?", id);
        row.put("deleted", Boolean.TRUE.equals(row.get("deleted")) ? 1 : 0);
        return row;
    }

    private Map<String, Object> latestAudit(String action) {
        return jdbcTemplate.queryForMap(
                "SELECT operator_id, operator_type, target_type, target_id, reason FROM audit_log "
                        + "WHERE action = ? ORDER BY id DESC LIMIT 1", action);
    }

    private int remainingSlotsOf(long scheduleId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT remaining_slots FROM schedule WHERE id = ?", Integer.class, scheduleId);
        return value == null ? -1 : value;
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("schedule", count("SELECT COUNT(*) FROM schedule"));
        snapshot.put("appointment", count("SELECT COUNT(*) FROM appointment"));
        snapshot.put("payment_record", count("SELECT COUNT(*) FROM payment_record"));
        snapshot.put("refund_record", count("SELECT COUNT(*) FROM refund_record"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("audit_appointment", count(
                "SELECT COUNT(*) FROM audit_log WHERE target_type = 'appointment'"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String randomCardNo() {
        return "T13" + String.format("%07d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
    }

    private String json(Map<?, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
