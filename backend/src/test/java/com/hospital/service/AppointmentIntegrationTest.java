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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 预约挂号 + 支付（T12）：J28 幂等 / J29 支付成功→已确认+扣号 / J30 重复预约被拒，
 * 外加归属、金额不可篡改、角色隔离与审计。J27（支付异常整体回滚）在
 * {@link AppointmentPayFailureTest}，理由见那个类的注释。
 *
 * <h2>测试数据怎么标记得出来、又删得干净</h2>
 *
 * <p>{@code schedule} 与 {@code appointment} 两张表<b>都没有能塞标记的文本列</b>
 * （{@code appointment.order_no} 的格式被 {@code SerialNumberService} 定死为
 * {@code YY<yyyyMMdd>-<序号>}，塞不進 "T12-" 前缀），所以沿用 T11 已经跑通的口径：
 * <b>探针排班一律用"今天 + 5 年"的日期</b>，种子只覆盖 {@code CURDATE()-7..+7}，
 * 两者不可能混淆；清理时按 {@code patient_id} / {@code schedule_id} 精确删，
 * 最后比对若干张表的总行数必须回到基线（见 {@link #cleanupAndAssertNothingLeaks}）。
 *
 * <p>删除一律走 {@code JdbcTemplate} 裸 SQL：{@code BaseEntity.deleted} 上有
 * {@code @TableLogic}，用 mapper 删是逻辑删，行还占着唯一索引，
 * 下一轮或下一张卡的 {@code uk_patient_schedule} 就会撞上一条看不见的死数据
 * （T06 往 {@code title} 表漏 11 行就是这么来的）。
 *
 * <h2>断言的期望值从哪来</h2>
 *
 * <p>全部来自种子字面值或规格原文，<b>不调用被测方法反算</b>：
 * 医生 1 张伟 = 主任医师（{@code seed.sql:51-64}）→ 挂号费 5000 分，
 * 医生 5 刘一鸣 = 主治医师 → 2000 分，这两个数就是种子里那 13 笔预约实际用的值
 * （{@code seed.sql:145-157}），所以费用表若与种子漂移，本类会立刻红。
 *
 * <p>业务错误一律 HTTP 200 + body.code；只有 401/403 带 HTTP 状态码（Spring Security 的两个处理器），
 * 参数校验失败才 HTTP 400。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class AppointmentIntegrationTest {

    private static final int PROBE_YEAR_OFFSET = 5;
    private static final List<String> AUDIT_ACTIONS = List.of("CREATE_APPOINTMENT", "APPOINTMENT_PAID");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    private String adminToken;
    private String patientToken;
    private Long patientOwnerUserId;
    private Long lastLoggedUserId;

    @BeforeEach
    void setUp() throws Exception {
        // 基线必须是一切写操作之前采：本方法之后的 wechat-login 会往 user 表加行，
        // 加完再采就把"我造的行"记成了"基线"，收尾删掉反而少一行（T11 就这么误报过一次）。
        countsBefore = snapshotCounts();
        adminToken = staffToken(1L, "admin");
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        // 顺序有关：payment_record / appointment 都靠 patient_id 认，先把它们删了再删就诊人。
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM payment_record WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM appointment WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        // 探针排班（今天 + 5 年）连着它上面的预约一起删；先删预约再删排班，账目才不留悬空。
        jdbcTemplate.update("DELETE FROM appointment WHERE schedule_id IN "
                + "(SELECT id FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL 4 YEAR) "
                + "OR `date` <= DATE_SUB(CURDATE(), INTERVAL 1 YEAR))");
        jdbcTemplate.update("DELETE FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL 4 YEAR) "
                + "OR `date` <= DATE_SUB(CURDATE(), INTERVAL 1 YEAR)");
        // 后半个条件是给 j29/j30 那条"两年前的过去排班"用的：种子只到 CURDATE()-7，
        // 一年前的边界同样碰不到任何真数据，但能精确捞回我自己插的那一行。
        jdbcTemplate.update("DELETE FROM audit_log WHERE target_type = 'appointment' AND action IN ('"
                + String.join("','", AUDIT_ACTIONS) + "')");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(),
                "T12 只动自己造的探针行：五张表的计数必须回到基线");
    }

    // ============================================================
    // J29 支付成功 → 预约状态 CONFIRMED + 剩余号源扣减
    // ============================================================

    @Test
    void j29_create_persistsPendingPaymentRowAndDeductsOneSlot() throws Exception {
        long doctorId = 1L;                                  // 张伟，主任医师 → 5000 分
        LocalDate date = probeDate(1);
        long scheduleId = createSchedule(doctorId, date, "MORNING", 20);
        long patientId = createPatient("T12-甲", "SELF");
        int remainingBefore = remainingSlotsOf(scheduleId);

        Map<?, ?> data = expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientId, "scheduleId", scheduleId)));

        assertEquals("PENDING_PAYMENT", data.get("status"),
                "卡片 453 行⑤：建出来的单一律是待支付，⑧才由回调推到已确认");
        assertEquals(5000L, ((Number) data.get("feeFen")).longValue(),
                "金额由服务端按职称算，出处是种子的字面值（seed.sql:145-157）");
        assertTrue(String.valueOf(data.get("orderNo")).matches("^YY\\d{8}-\\d{4}$"),
                "单号格式出处 SerialNumberService.format（前缀 YY = SerialType.YY），实际：" + data.get("orderNo"));
        assertEquals(java.time.LocalDateTime.of(date, java.time.LocalTime.of(8, 30)),
                java.time.LocalDateTime.parse(String.valueOf(data.get("appointmentTime"))),
                "预约时间 = 排班日期 + 时段开始时刻（MORNING 08:30 出处 seed.sql:140）。"
                        + "解析后比而不是比字符串：spring.jackson 只声明了 date-format，"
                        + "LocalDateTime 实际走的还是 JSR-310 那套，字符串形状不该写进断言");
        assertEquals("MORNING", data.get("timeSlot"));
        assertNotNull(data.get("id"), "成功页与后续支付都要靠这个 id");
        assertEquals("张伟", data.get("doctorName"));
        assertEquals("消化内科", data.get("departmentName"),
                "PRD 80 行「确认预约信息—展示就诊人、科室、医生、时间、费用」，科室不是 appointment 表的列，靠 join");

        assertEquals(remainingBefore - 1, remainingSlotsOf(scheduleId), "⑥扣减一个号源");
        Map<String, Object> row = rawAppointment((Number) data.get("id"));
        assertEquals(doctorId, ((Number) row.get("doctor_id")).longValue(),
                "doctor_id 取自排班，入参里根本没有 doctorId 这个字段");
        assertEquals(0, intOf(row.get("deleted")), "预约表本卡从不软删");
    }

    @Test
    void j29_eveningSlotUsesTheExtensionClockTime() throws Exception {
        LocalDate date = probeDate(2);
        long scheduleId = createSchedule(1L, date, "EVENING", 5);
        long patientId = createPatient("T12-乙", "SELF");

        Map<?, ?> data = expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientId, "scheduleId", scheduleId)));

        // 18:30 是 T12 补的值（种子里没有晚间排班，那条 SQL 只有 MORNING/ELSE 两支），
        // 单独断言一次是为了让它可见：将来 T25 定义真时段时刻时，这条断言会提醒你改。
        assertEquals(java.time.LocalDateTime.of(date, java.time.LocalTime.of(18, 30)),
                java.time.LocalDateTime.parse(String.valueOf(data.get("appointmentTime"))));
        assertEquals("EVENING", data.get("timeSlot"));
    }

    @Test
    void j29_payByPatient_confirmsAndBooksOnce() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(3), "MORNING", 10);
        long patientId = createPatient("T12-丙", "SELF");
        Map<?, ?> created = expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientId, "scheduleId", scheduleId)));
        long appointmentId = ((Number) created.get("id")).longValue();
        String orderNo = String.valueOf(created.get("orderNo"));

        Map<?, ?> paid = expectData(postJson("/user/appointments/" + appointmentId + "/pay", patientToken,
                Map.of()));

        assertEquals("CONFIRMED", paid.get("status"));
        assertEquals(true, paid.get("processed"), "第一次支付当然是我推进的");
        assertEquals("CONFIRMED", rawAppointment(appointmentId).get("status"));

        List<Map<String, Object>> payments = jdbcTemplate.queryForList(
                "SELECT * FROM payment_record WHERE order_no = ?", orderNo);
        assertEquals(1, payments.size(), "③支付记录只该有一条");
        Map<String, Object> payment = payments.get(0);
        assertEquals(5000L, ((Number) payment.get("amount_fen")).longValue(),
                "金额取自己账上的 fee_fen，不取任何外部传入值");
        assertEquals("SUCCESS", payment.get("status"), "V1:163 的三值之一");
        assertEquals("WECHAT", payment.get("pay_method"));
        assertEquals("T12-丙", jdbcTemplate.queryForObject(
                "SELECT name FROM patient WHERE id = ?", String.class, patientId));

        List<?> items = objectMapper.readValue(String.valueOf(payment.get("items")), List.class);
        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertEquals(5000L, ((Number) item.get("amountFen")).longValue(),
                "V1:160 是 JSON NOT NULL 列，形状照 seed.sql:192 的 [{name,amountFen}]");
        assertEquals("门诊挂号费", item.get("name"));
    }

    @Test
    void j29_slotFullyBooked_fourthRequestGetsNoSlotsAndWritesNothing() throws Exception {
        LocalDate date = probeDate(4);
        long scheduleId = createSchedule(1L, date, "MORNING", 2);
        long patientA = createPatient("T12-丁", "SELF");
        long patientB = createPatient("T12-戊", "SPOUSE");

        expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientA, "scheduleId", scheduleId)));
        expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientB, "scheduleId", scheduleId)));
        assertEquals(0, remainingSlotsOf(scheduleId), "两个号各占一格，剩 0");

        // 第三个就诊人（关系 OTHER，同一个人的名下）来挤同一个已满是 0 的时段
        long patientC = createPatient("T12-己", "OTHER");
        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientC, "scheduleId", scheduleId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2003))
                .andExpect(jsonPath("$.message").value(containsString("号源已满")));

        assertEquals(0, remainingSlotsOf(scheduleId), "被拒的那次不得把号源改成负数");
        assertEquals(2, appointmentCountOfSchedule(scheduleId));
    }

    @Test
    void j29_concurrentBooking_neverOversells() throws Exception {
        // 这条是「先查再改必然超卖」的反证：4 个线程抢 2 个号，只许 2 单成功。
        LocalDate date = probeDate(5);
        long scheduleId = createSchedule(1L, date, "AFTERNOON", 2);
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<Integer>> codes = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                long patientId = createPatient("T12-并" + i, "OTHER");
                codes.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await();
                    MvcResult result = mockMvc.perform(post("/user/appointments")
                                    .header("Authorization", "Bearer " + patientToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("patientId", patientId, "scheduleId", scheduleId))))
                            .andReturn();
                    Map<?, ?> root = objectMapper.readValue(
                            result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
                    return ((Number) root.get("code")).intValue();
                }));
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS), "4 个线程都应就位");
            fire.countDown();
            List<Integer> got = new ArrayList<>();
            for (Future<Integer> future : codes) {
                got.add(future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(2, got.stream().filter(c -> c == 200).count(),
                    "只有两个号，成功数必须等于号源数：" + got);
            assertEquals(2, got.stream().filter(c -> c == 2003).count(),
                    "另两个必须被 occupySlot 的返回值拦成 2003，不许出现 500：" + got);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, remainingSlotsOf(scheduleId));
        assertEquals(2, appointmentCountOfSchedule(scheduleId), "库里也不许多留一行");
    }

    // ============================================================
    // J28 支付回调幂等（卡片 455 行「独立接口，幂等」）
    // ============================================================

    @Test
    void j28_repeatedNotify_processesExactlyOnceAndBooksOnePayment() throws Exception {
        long patientId = createPatient("T12-回", "SELF");
        long scheduleId = createSchedule(1L, probeDate(6), "MORNING", 10);
        Map<?, ?> created = bookOneOn(patientId, scheduleId);
        long appointmentId = ((Number) created.get("id")).longValue();
        String orderNo = String.valueOf(created.get("orderNo"));
        Map<String, Object> notify = notifyBody(orderNo, "SUCCESS", "WX-TXN-001");

        assertEquals(true, expectData(postJson("/payments/wechat/notify", null, notify)).get("processed"),
                "第一次回调真的推进了");
        for (int i = 0; i < 3; i++) {
            Map<?, ?> again = expectData(postJson("/payments/wechat/notify", null, notify));
            assertEquals(false, again.get("processed"), "第 " + (i + 2) + " 次回调必须是幂等空转");
            assertEquals("CONFIRMED", again.get("status"), "状态不回退也不乱跳");
        }

        assertEquals(1, paymentCountOfOrder(orderNo),
                "钱只记一次。幂等靠 confirmIfPending 的受影响行数承载，不是先查状态再改");
        assertEquals(1, appointmentCountOfSchedule(scheduleId),
                "预约也还是那一行");
        assertEquals(1, auditCount("APPOINTMENT_PAID", appointmentId),
                "审计同样只有一条——重复回调不该刷流水");
    }

    @Test
    void j28_concurrentDuplicateNotify_writesOnePaymentRecord() throws Exception {
        // 真并发下的幂等：两次回调同时进来，select-then-update 会写出两条支付记录
        // （payment_record.order_no 在 V1:156-167 没有唯一索引，数据库不会拦），
        // 所以这条测试是那句带条件 UPDATE 的直接回报。
        long patientId = createPatient("T12-竞", "SELF");
        Map<?, ?> created = bookOne(patientId, 7, "MORNING", 10);
        String orderNo = String.valueOf(created.get("orderNo"));
        Map<String, Object> notify = notifyBody(orderNo, "SUCCESS", "WX-TXN-002");

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<Integer>> processed = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                processed.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await();
                    MvcResult result = mockMvc.perform(post("/payments/wechat/notify")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(notify)))
                            .andReturn();
                    Map<?, ?> root = objectMapper.readValue(
                            result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
                    Map<?, ?> data = (Map<?, ?>) root.get("data");
                    return data != null && Boolean.TRUE.equals(data.get("processed")) ? 1 : 0;
                }));
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS), "线程应就位");
            fire.countDown();
            int advanced = 0;
            for (Future<Integer> future : processed) {
                advanced += future.get(30, TimeUnit.SECONDS);
            }
            assertEquals(1, advanced, "五个并发回调里必须恰好一个推进了状态");
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, paymentCountOfOrder(orderNo), "支付流水不许出现第二条");
    }

    @Test
    void j28_notifyWithoutToken_isReachableButOtherPaymentPathsAreNot() throws Exception {
        // permitAll 只放那一个精确路径：/payments/** 前缀下的其余路径仍然要求员工角色。
        // 这条测试就是 SecurityConfig 那行注释的反证——写成 "/payments/**" 就会在这里红。
        long patientId = createPatient("T12-名", "SELF");
        Map<?, ?> created = bookOne(patientId, 8, "AFTERNOON", 6);
        String orderNo = String.valueOf(created.get("orderNo"));

        Map<?, ?> data = expectData(postJson("/payments/wechat/notify", null,
                notifyBody(orderNo, "SUCCESS", "WX-TXN-003")));
        assertEquals("CONFIRMED", data.get("status"), "无 token 也能推进（回调本来就没有我们的 token）");

        // 匿名打 /payments/1 必须是 401：这条是"我只放行了 notify 那一个精确路径，
        // 没有把整片 /payments/** 放开"的反证（403 是"认出来了但不许进"，匿名连认都认不出来）。
        mockMvc.perform(get("/payments/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(post("/payments/wechat/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(notifyBody(orderNo, "SUCCESS", "WX-TXN-004"))))
                .andExpect(status().isOk())
                // 同一笔再推一次必须走幂等分支，不能二次记账
                .andExpect(jsonPath("$.data.processed").value(false));
    }

    @Test
    void j28_notifyFailCode_leavesAppointmentPendingAndBooksNothing() throws Exception {
        long patientId = createPatient("T12-败", "SELF");
        long scheduleId = createSchedule(1L, probeDate(9), "MORNING", 10);
        Map<?, ?> created = bookOneOn(patientId, scheduleId);
        long appointmentId = ((Number) created.get("id")).longValue();
        int remainingAfterBook = remainingSlotsOf(scheduleId);

        // 签名带、结果码是 FAIL：验签通过但这笔没付成 —— 这条分支只有把"验签"和"支付结果"
        // 分成两个轴才表达得出来（第一版混在一起时，这个请求会被当成验签失败整个拒掉）。
        Map<?, ?> data = expectData(postJson("/payments/wechat/notify", null,
                notifyBody(String.valueOf(created.get("orderNo")), "FAIL", "WX-TXN-FAIL")));

        assertEquals("PENDING_PAYMENT", data.get("status"), "没付成就不能推进");
        assertEquals(false, data.get("processed"));
        assertEquals(0, paymentCountOfOrder(String.valueOf(created.get("orderNo"))), "不写流水");
        assertEquals(remainingAfterBook, remainingSlotsOf(scheduleId),
                "号源也不动：待支付的单本来就占着号");
        assertEquals(0, auditCount("APPOINTMENT_PAID", appointmentId));
    }

    @Test
    void j28_notifyUnknownOrderNo_returns2004AndChangesNothing() throws Exception {
        int before = auditCount("APPOINTMENT_PAID", null);
        mockMvc.perform(post("/payments/wechat/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(notifyBody("YY19700101-9999", "SUCCESS", "WX-TXN-404"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2004));
        assertEquals(before, auditCount("APPOINTMENT_PAID", null),
                "查无此单不写审计（同事务：业务失败就一起回滚）");
    }

    @Test
    void j28_notifyThatFailsVerification_isRejectedAndNotTreatedAsDuplicate() throws Exception {
        // 验签与"支付结果"是两件正交的事（这条测试就是为了钉住它们没被混成一个返回值）：
        // 验不过 = 请求没有资格推进任何单据，必须拒绝，而不是回一句"收到"让伪造者换个单号接着试。
        long patientId = createPatient("T12-伪", "SELF");
        long scheduleId = createSchedule(1L, probeDate(30), "MORNING", 10);
        Map<?, ?> created = bookOneOn(patientId, scheduleId);
        long appointmentId = ((Number) created.get("id")).longValue();
        String orderNo = String.valueOf(created.get("orderNo"));

        mockMvc.perform(post("/payments/wechat/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(notifyBody(orderNo, "SUCCESS", null))))   // 没带签名 => 验不过
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001));

        assertEquals("PENDING_PAYMENT", rawAppointment(appointmentId).get("status"),
                "验签失败的请求一个字都不能改");
        assertEquals(0, paymentCountOfOrder(orderNo));
        assertEquals(0, auditCount("APPOINTMENT_PAID", appointmentId));
        assertEquals(9, remainingSlotsOf(scheduleId), "号源也不动");
    }

    @Test
    void j28_notifyForCancelledAppointment_returns2006NotIdempotentAck() throws Exception {
        // CANCELLED 的单收到"支付成功"是真异常（钱可能已经扣了），必须显式报错而不是当成重复回调 ACK 掉。
        long patientId = createPatient("T12-取", "SELF");
        Map<?, ?> created = bookOne(patientId, 10, "MORNING", 10);
        String orderNo = String.valueOf(created.get("orderNo"));
        long appointmentId = ((Number) created.get("id")).longValue();
        jdbcTemplate.update("UPDATE appointment SET status = 'CANCELLED' WHERE id = ?", appointmentId);

        mockMvc.perform(post("/payments/wechat/notify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(notifyBody(orderNo, "SUCCESS", "WX-TXN-005"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2006));

        assertEquals("CANCELLED", rawAppointment(appointmentId).get("status"), "不能被推成 CONFIRMED");
        assertEquals(0, paymentCountOfOrder(orderNo), "也不能补一条流水");
    }

    // ============================================================
    // J30 同一就诊人同一排班重复预约 → 被拒（唯一索引）
    // ============================================================

    @Test
    void j30_samePatientSameSchedule_secondBookingRejectedWithoutTakingAnotherSlot() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(11), "MORNING", 20);
        long patientId = createPatient("T12-重", "SELF");
        bookOneOn(patientId, scheduleId);
        int remaining = remainingSlotsOf(scheduleId);

        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", scheduleId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2005))
                .andExpect(jsonPath("$.message").value(containsString("重复预约")));

        assertEquals(remaining, remainingSlotsOf(scheduleId),
                "被拒的这次一个号都不许多占——这正是把前置查放在扣号之前的原因之一");
        assertEquals(1, appointmentCountOfSchedule(scheduleId));
    }

    @Test
    void j30_differentPatientsOnSameSchedule_areBothAllowed() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(12), "MORNING", 20);
        long patientA = createPatient("T12-AB", "SELF");
        long patientB = createPatient("T12-BA", "CHILD");
        bookOneOn(patientA, scheduleId);
        bookOneOn(patientB, scheduleId);
        assertEquals(18, remainingSlotsOf(scheduleId), "同班不同人可以一起约；唯一索引管的是人与班的组合，不是班次本身");
    }

    @Test
    void j30_samePatientOnDifferentSchedules_isAllowed() throws Exception {
        long patientId = createPatient("T12-多", "SELF");
        long first = createSchedule(1L, probeDate(13), "MORNING", 5);
        long second = createSchedule(1L, probeDate(14), "AFTERNOON", 5);
        bookOneOn(patientId, first);
        bookOneOn(patientId, second);
        assertEquals(2, appointmentCountOfPatient(patientId));
    }

    @Test
    void j30_uniqueIndexRejectsDuplicateAtDatabaseLevel() throws Exception {
        // DoD 式的证明：绕开 service 直插第二条，必须由 uk_patient_schedule（V1:130）拦下来。
        long scheduleId = createSchedule(1L, probeDate(15), "MORNING", 5);
        long patientId = createPatient("T12-裸", "SELF");
        bookOneOn(patientId, scheduleId);

        DuplicateKeyException ex = assertThrows(DuplicateKeyException.class, () ->
                jdbcTemplate.update("INSERT INTO appointment (order_no, patient_id, doctor_id, schedule_id, "
                                + "status, appointment_time, fee_fen) VALUES (?, ?, 1, ?, 'PENDING_PAYMENT', NOW(3), 5000)",
                        "T12-BARE-1", patientId, scheduleId));
        assertTrue(String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_patient_schedule"),
                "撞的必须是那个唯一索引，实际：" + ex.getMostSpecificCause().getMessage());
    }

    @Test
    void j30_cancelledAppointmentStillBlocksRebooking_isT13sProblemNotOurs() throws Exception {
        // 前置查不看 status（只被 @TableLogic 过滤掉软删行），唯一索引也不看 status，
        // 所以已取消的单继续占着这个"人 + 班"。T12 没有任何入口能造出 CANCELLED，
        // 这条是靠裸 SQL 造的，作用是把"退号后能不能重约同一班"这个问题显式钉给 T13。
        long scheduleId = createSchedule(1L, probeDate(16), "MORNING", 5);
        long patientId = createPatient("T12-退", "SELF");
        Map<?, ?> booked = bookOneOn(patientId, scheduleId);
        jdbcTemplate.update("UPDATE appointment SET status = 'CANCELLED' WHERE id = ?",
                ((Number) booked.get("id")).longValue());

        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", scheduleId))))
                .andExpect(jsonPath("$.code").value(2005));
    }

    // ============================================================
    // 归属、越权与参数校验
    // ============================================================

    @Test
    void create_anotherUsersPatient_returns1003AndWritesNothing() throws Exception {
        ensurePatientToken();
        ensureIntruderToken();
        long scheduleId = createSchedule(1L, probeDate(17), "MORNING", 5);
        long otherPatientId = createPatientWithSecondAccount("T12-他", patientToken2);

        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", otherPatientId, "scheduleId", scheduleId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        assertEquals(5, remainingSlotsOf(scheduleId), "越权请求不得动号源");
        assertEquals(0, appointmentCountOfSchedule(scheduleId));
    }

    @Test
    void pay_anotherUsersAppointment_returns2004AndStaysPending() throws Exception {
        long patientId = createPatient("T12-主", "SELF");
        Map<?, ?> created = bookOne(patientId, 18, "MORNING", 5);
        long appointmentId = ((Number) created.get("id")).longValue();

        String intruderToken = newPatientToken("t12-intruder");   // 另一个人，另一次登录
        mockMvc.perform(post("/user/appointments/" + appointmentId + "/pay")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isOk())
                // 不是你的单和根本没有这单同码，不给枚举机会（与 T08/T09 一致）
                .andExpect(jsonPath("$.code").value(2004));
        assertEquals("PENDING_PAYMENT", rawAppointment(appointmentId).get("status"));
    }

    @Test
    void create_ignoresAnyAmountTheClientSends() throws Exception {
        // 卡片 458 行「支付金额禁篡改」：AppointmentCreateRequest 里没有金额字段，
        // 客户端硬塞一个也只能被 Jackson 当未知属性丢掉，账上仍是服务端算的值。
        long scheduleId = createSchedule(1L, probeDate(19), "MORNING", 5);
        long patientId = createPatient("T12-金", "SELF");
        Map<String, Object> body = new HashMap<>();
        body.put("patientId", patientId);
        body.put("scheduleId", scheduleId);
        body.put("feeFen", 1);
        body.put("amountFen", 1);

        Map<?, ?> data = expectData(postJson("/user/appointments", patientToken, body));
        assertEquals(5000L, ((Number) data.get("feeFen")).longValue(), "主任医师的 5000 分，不受入参影响");
        assertEquals(5000L, ((Number) rawAppointment(data.get("id")).get("fee_fen")).longValue());
    }

    @Test
    void create_rejectsMissingFieldsAndBlankBody() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(20), "MORNING", 5);
        long patientId = createPatient("T12-空", "SELF");

        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("scheduleId", scheduleId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("请选择就诊人")));

        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("请选择排班时段")));
        assertEquals(5, remainingSlotsOf(scheduleId));
    }

    @Test
    void create_rejectsUnknownCancelledAndPastSchedules_allAs2001() throws Exception {
        long patientId = createPatient("T12-排", "SELF");

        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", 9999999L))))
                .andExpect(jsonPath("$.code").value(2001));

        // 已被 T11 取消（软删）的排班：@TableLogic 让 selectById 直接查不到 → 也是 2001。
        long cancelled = createSchedule(1L, probeDate(21), "MORNING", 5);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/admin/schedules/" + cancelled)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", cancelled))))
                // 停诊与根本没这条同码：患者端读口径本来就不含已取消排班，不该从写侧漏出去
                .andExpect(jsonPath("$.code").value(2001));

        // 过去的排班：不能用"三天前"——种子覆盖 CURDATE()-7..+7，医生 1 那天真有班，会撞
        // uk_doctor_date_slot（第一次跑就是这么炸的）。取两年前那个日期，种子不可能有。
        long past = insertPastSchedule(1L, LocalDate.now().minusYears(2), "MORNING");
        mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", past))))
                .andExpect(jsonPath("$.code").value(2001));
        assertEquals(0, appointmentCountOfSchedule(past));
    }

    @Test
    void staffAndAnonymousCannotReachUserAppointmentEndpoints() throws Exception {
        long patientId = createPatient("T12-隔", "SELF");
        Map<?, ?> created = bookOne(patientId, 22, "MORNING", 5);
        String body = json(Map.of("patientId", patientId, "scheduleId", 1L));

        for (String token : List.of(adminToken, staffToken(3L, "doctor"))) {
            mockMvc.perform(post("/user/appointments")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden())
                    // 员工 token 进不了患者侧（SecurityConfig 的 /user/** → hasRole(patient)）
                    .andExpect(jsonPath("$.code").value(4001));
            mockMvc.perform(post("/user/appointments/" + ((Number) created.get("id")).longValue() + "/pay")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/user/appointments")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/appointments/" + ((Number) created.get("id")).longValue() + "/pay"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 审计（卡片 453 行⑨ / 456 行④）
    // ============================================================

    @Test
    void audit_create_isAttributedToThePatientAndPaysNoTargetIdYet() throws Exception {
        long scheduleId = createSchedule(1L, probeDate(23), "MORNING", 5);
        long patientId = createPatient("T12-审", "SELF");
        Map<?, ?> data = expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientId, "scheduleId", scheduleId)));

        Map<String, Object> row = latestAudit("CREATE_APPOINTMENT");
        assertEquals("PATIENT", row.get("operator_type"),
                "卡片⑨要求患者侧也留痕，所以审计的操作人类型多了 PATIENT 这一档（T04 只有三个员工值）");
        assertEquals(patientOwnerUserId, ((Number) row.get("operator_id")).longValue(),
                "PATIENT 档的 operator_id 指向 user.id，不是 admin.id");
        assertEquals("appointment", row.get("target_type"));
        assertNull(row.get("target_id"), "创建时还没有 id，切面只认 @AuditTarget 标的 Long 入参");
        assertTrue(String.valueOf(row.get("detail")).contains(String.valueOf(data.get("orderNo")))
                        || String.valueOf(row.get("detail")).contains("request"),
                "detail 要能还原是谁挂的哪个班，实际：" + row.get("detail"));
    }

    @Test
    void audit_payRecordsSystemOperatorWithZeroAsTheNonHumanSentinel() throws Exception {
        long patientId = createPatient("T12-统", "SELF");
        Map<?, ?> created = bookOne(patientId, 24, "MORNING", 5);
        long appointmentId = ((Number) created.get("id")).longValue();
        String orderNo = String.valueOf(created.get("orderNo"));

        postJson("/payments/wechat/notify", null, notifyBody(orderNo, "SUCCESS", "WX-TXN-AUDIT"));

        Map<String, Object> row = latestAudit("APPOINTMENT_PAID");
        assertEquals(appointmentId, ((Number) row.get("target_id")).longValue(),
                "支付审计指向这笔预约（@AuditLog 切面管不到这条路，是 service 里显式写的）");
        assertEquals("SYSTEM", row.get("operator_type"));
        assertEquals(0L, ((Number) row.get("operator_id")).longValue(),
                "回调没有自然人操作人；0 在 admin 与 user 两张表里都不可能真实存在");
        String detail = String.valueOf(row.get("detail"));
        assertTrue(detail.contains("NOTIFY"), "detail 要分得清是微信推的还是患者自己点的，实际：" + detail);
    }

    // ============================================================
    // 挂号费口径（PRD 从未给出数额，见 AppointmentFeeService 类注释）
    // ============================================================

    @Test
    void fee_followsTheDoctorsTitleAndMatchesTheSeedNumbers() throws Exception {
        long chief = createSchedule(1L, probeDate(25), "MORNING", 5);    // 张伟 主任医师
        long attending = createSchedule(5L, probeDate(25), "MORNING", 5); // 刘一鸣 主治医师
        long patientId = createPatient("T12-费", "SELF");

        assertEquals(5000L, ((Number) expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientId, "scheduleId", chief))).get("feeFen")).longValue());
        long patientIdB = createPatient("T12-费B", "CHILD");
        assertEquals(2000L, ((Number) expectData(postJson("/user/appointments", patientToken,
                Map.of("patientId", patientIdB, "scheduleId", attending))).get("feeFen")).longValue());
    }

    @Test
    void fee_isAlsoExposedOnTheScheduleListSoThePatientSeesItBeforeSubmitting() throws Exception {
        // PRD 80 行要求「确认预约信息」页提交前就展示费用，所以价格必须在只读接口里就能拿到。
        long scheduleId = createSchedule(1L, probeDate(26), "MORNING", 5);
        createPatient("T12-价", "SELF");   // 顺便确保患者 token 已就位（这个端点要患者身份）
        String body = mockMvc.perform(get("/user/doctors/1")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> root = objectMapper.readValue(body, Map.class);
        List<?> schedules = (List<?>) ((Map<?, ?>) root.get("data")).get("schedules");
        Map<?, ?> mine = schedules.stream()
                .filter(s -> ((Number) ((Map<?, ?>) s).get("id")).longValue() == scheduleId)
                .map(s -> (Map<?, ?>) s)
                .findFirst().orElseThrow(() -> new AssertionError("刚建的探针排班没出现在医生详情里"));
        assertEquals(5000L, ((Number) mine.get("feeFen")).longValue(),
                "同一个出处（AppointmentFeeService），两个接口不该报出两个价");
    }

    // ============================================================
    // 辅助
    // ============================================================

    /** 第二个患者账号的 token，给"别人的就诊人"这类用例用 */
    private String patientToken2;

    private String staffToken(Long adminId, String role) {
        return jwtUtil.generateToken(adminId, role, role,
                List.of("dashboard", "schedule", "appointment"), List.of());
    }

    private String newPatientToken(String tag) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", tag + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        long userId = ((Number) data.get("userId")).longValue();
        createdUserIds.add(userId);
        lastLoggedUserId = userId;
        return String.valueOf(data.get("token"));
    }

    /**
     * 只有真正要建就诊人的用例才登录，纯负例（匿名 / 员工 token）不建账号——
     * 这样 user 表的计数要么不动，要么与 createdUserIds 严格成对，收尾比对才有意义。
     *
     * <p>{@code patientOwnerUserId} 直接取登录响应里的 userId，
     * 不再绕去 {@code GET /user/profile} 拿：那个端点是 T07 的档案接口，
     * 用它只会让本类的失败点多一个（档案字段形状一改，这里就跟着红）。
     */
    private String ensurePatientToken() throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t12-owner");
            patientOwnerUserId = lastLoggedUserId;
        }
        return patientToken;
    }

    private long createPatient(String name, String relation) throws Exception {
        ensurePatientToken();
        Map<?, ?> data = expectData(postJson("/user/patients", patientToken, Map.of(
                "name", name, "cardNo", randomCardNo(), "idCard", randomIdCard(),
                "phone", randomPhone(), "relation", relation)));
        long id = ((Number) data.get("id")).longValue();
        createdPatientIds.add(id);
        return id;
    }

    private long createPatientWithSecondAccount(String name, String token) throws Exception {
        Map<?, ?> data = expectData(postJson("/user/patients", token, Map.of(
                "name", name, "cardNo", randomCardNo(), "idCard", randomIdCard(),
                "phone", randomPhone(), "relation", "SELF")));
        long id = ((Number) data.get("id")).longValue();
        createdPatientIds.add(id);
        return id;
    }

    private void ensureIntruderToken() throws Exception {
        if (patientToken2 == null) {
            patientToken2 = newPatientToken("t12-other");
        }
    }

    private LocalDate probeDate(int plusDays) {
        return LocalDate.now().plusYears(PROBE_YEAR_OFFSET).plusDays(plusDays);
    }

    private long createSchedule(long doctorId, LocalDate date, String timeSlot, int totalSlots) throws Exception {
        Map<?, ?> data = expectData(postJson("/admin/schedules", adminToken, Map.of(
                "doctorId", doctorId, "date", date.toString(), "timeSlot", timeSlot, "totalSlots", totalSlots)));
        return ((Number) data.get("id")).longValue();
    }

    /** 裸插一条过去的排班：T11 的创建接口不校验过去日期，而本卡要证明它会拒绝这种排班 */
    private long insertPastSchedule(long doctorId, LocalDate date, String timeSlot) {
        jdbcTemplate.update("INSERT INTO schedule (doctor_id, `date`, time_slot, total_slots, remaining_slots) "
                + "VALUES (?, ?, ?, 5, 5)", doctorId, java.sql.Date.valueOf(date), timeSlot);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM schedule WHERE doctor_id = ? AND `date` = ? AND time_slot = ?",
                Long.class, doctorId, java.sql.Date.valueOf(date), timeSlot);
    }

    private Map<?, ?> bookOne(long patientId, int datePlus, String timeSlot, int totalSlots) throws Exception {
        long scheduleId = createSchedule(1L, probeDate(datePlus), timeSlot, totalSlots);
        return bookOneOn(patientId, scheduleId);
    }

    private Map<?, ?> bookOneOn(long patientId, long scheduleId) throws Exception {
        return expectData(postJson("/user/appointments", ensurePatientToken(),
                Map.of("patientId", patientId, "scheduleId", scheduleId)));
    }

    private Map<String, Object> notifyBody(String orderNo, String returnCode, String tradeNo) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", orderNo);
        body.put("returnCode", returnCode);
        body.put("tradeNo", tradeNo);
        body.put("signature", tradeNo == null ? null : "MOCK-SIGN");
        return body;
    }

    private MvcResult postJson(String path, String token, Map<?, ?> body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private Map<String, Object> rawAppointment(Object id) {
        return jdbcTemplate.queryForMap("SELECT * FROM appointment WHERE id = ?", ((Number) id).longValue());
    }

    private int remainingSlotsOf(long scheduleId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT remaining_slots FROM schedule WHERE id = ?", Integer.class, scheduleId);
        return value == null ? -1 : value;
    }

    private int appointmentCountOfSchedule(long scheduleId) {
        return count("SELECT COUNT(*) FROM appointment WHERE schedule_id = ?", scheduleId);
    }

    private int appointmentCountOfPatient(long patientId) {
        return count("SELECT COUNT(*) FROM appointment WHERE patient_id = ?", patientId);
    }

    private int paymentCountOfOrder(String orderNo) {
        return count("SELECT COUNT(*) FROM payment_record WHERE order_no = ?", orderNo);
    }

    private int auditCount(String action, Long targetId) {
        Integer value = targetId == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ?",
                        Integer.class, action)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = ? AND target_id = ?",
                        Integer.class, action, targetId);
        return value == null ? 0 : value;
    }

    private Map<String, Object> latestAudit(String action) {
        return jdbcTemplate.queryForMap(
                "SELECT operator_id, operator_type, target_type, target_id, detail FROM audit_log "
                        + "WHERE action = ? ORDER BY id DESC LIMIT 1", action);
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("schedule", count("SELECT COUNT(*) FROM schedule"));
        snapshot.put("appointment", count("SELECT COUNT(*) FROM appointment"));
        snapshot.put("payment_record", count("SELECT COUNT(*) FROM payment_record"));
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

    private static int intOf(Object value) {
        return value instanceof Boolean ? (((Boolean) value) ? 1 : 0) : ((Number) value).intValue();
    }

    private String randomCardNo() {
        return "T12" + String.format("%07d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
    }

    private String randomPhone() {
        return "13" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000_000));
    }

    /** 18 位纯数字：前 14 位固定 + 4 位随机，满足 PatientCreateRequest 的 {@code \d{17}[\dXx]} */
    private String randomIdCard() {
        return "11010119900307" + String.format("%04d",
                Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000));
    }

    private String json(Map<?, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<?, ?> expectRoot(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    private Map<?, ?> expectData(MvcResult result) throws Exception {
        Map<?, ?> root = expectRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(),
                "期望业务成功，实际响应：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望返回对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }
}
