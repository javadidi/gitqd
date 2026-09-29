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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 候诊查询（T16）：J37 候诊查询 → 数据正确、J38 实时更新 → 状态刷新。
 *
 * <h2>排队行只能裸插，这是本卡的结构性事实</h2>
 * 全仓没有任何一张卡负责写 {@code queue_status}（PRD 的 §4 后台章节、§6.2 页面清单、§9.2
 * 接口清单都没有叫号管理；{@code seed.sql} 里也零行）。真实系统由院内叫号系统写（PRD 662 行）。
 * 所以"给这条预约挂一个排队状态"只能由测试用 {@code jdbcTemplate} 裸插，并登记进
 * {@link #createdQueueAppointmentIds} 收尾删掉。
 *
 * <h2>但预约本身走真实链路，不裸插</h2>
 * 挂号与支付是 T12 已有的能力，用 {@code POST /user/appointments} + {@code /pay} 造数据
 * 有两个好处：一是候诊页读到的就是真实形态的预约（含 {@code appointment_time} 由排班算出来），
 * 二是每张卡都顺手回归一次 T12 的写路径（T13 已确立这个做法）。
 * 只有"改状态/改时间"这类产品代码被红线禁掉的动作（卡片 458 行：除支付方法外禁止任何地方
 * 更新预约状态）才由测试直接写库，且逐条注明。
 *
 * <h2>探针排班用「今天 + 5 年」</h2>
 * 沿用 T11/T12 的口径：种子只覆盖 ±7 天，把探针放到五年后既能满足本卡「appointment_time >=
 * 今天零点」的过滤，又能靠 {@code date >= CURDATE() + 4 YEAR} 一条 DELETE 干净收场，
 * 不会碰到任何种子行。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class QueueIntegrationTest {

    private static final int PROBE_YEAR_OFFSET = 5;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private final List<Long> createdAppointmentIds = new ArrayList<>();
    private final List<Long> createdQueueAppointmentIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    private String patientToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
        adminToken = jwtUtil.generateToken(1L, "admin", "admin",
                List.of("dashboard", "schedule", "appointment"), List.of());
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        for (Long appointmentId : createdQueueAppointmentIds) {
            jdbcTemplate.update("DELETE FROM queue_status WHERE appointment_id = ?", appointmentId);
        }
        for (Long appointmentId : createdAppointmentIds) {
            // 注意列名：要按预约的 <b>patient_id</b> 去删流水，写成 SELECT id 就是拿预约主键
            // 去比 payment_record.patient_id，永远匹配不上（第一版就是这么漏了 12 行）。
            jdbcTemplate.update("DELETE FROM payment_record WHERE patient_id IN "
                    + "(SELECT patient_id FROM appointment WHERE id = ?)", appointmentId);
            jdbcTemplate.update("DELETE FROM appointment WHERE id = ?", appointmentId);
            // 审计要删干净，否则会污染别的类的基线：T12 的清理是按 action 整批删
            // （AppointmentIntegrationTest:122），只要我这儿漏了行，它就会在自检时
            // 发现"基线 15 → 跑完 0"这种根本不是我干的账。第一版就踩了这个。
            // APPOINTMENT_PAID 与 CANCEL_APPOINTMENT 都带 target_id（AppointmentPaymentService:222），
            // 这里按 id 删；CREATE_APPOINTMENT 的 target_id 是 NULL，由下面按 uid 那一条兜住。
            jdbcTemplate.update("DELETE FROM audit_log WHERE target_type = 'appointment' "
                    + "AND target_id = ?", appointmentId);
        }
        // CREATE_APPOINTMENT 的 target_id 是 NULL（切面只认 @AuditTarget 标的 Long 入参，
        // 创建时还没有 id，与 T14 的 CREATE_RECHARGE 同形），只能按主体身份捞回来。
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM audit_log WHERE target_type = 'appointment' "
                    + "AND operator_type = 'PATIENT' AND operator_id = ?", userId);
        }
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        jdbcTemplate.update("DELETE FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL 4 YEAR)");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(), "T16 只动自己造的探针行：六项计数必须回到基线");
    }

    // ============================================================
    // J37 候诊查询 → 数据正确
    // ============================================================

    @Test
    void j37_queueRowIsReturnedWrappedInItsAppointment() throws Exception {
        long patientId = createPatient("T16-甲");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));
        insertQueue(appointmentId, 12, 3, "WAITING");

        Map<?, ?> item = onlyItem(getJson("/user/queues"));

        assertEquals(appointmentId, ((Number) item.get("appointmentId")).longValue(),
                "列表以预约为骨架：id 就是那次挂号的 id");
        assertEquals("T16-甲", item.get("patientName"));
        assertEquals("张伟", item.get("doctorName"), "医生名要能出来，否则患者不知道在等谁");
        assertEquals("消化内科", item.get("departmentName"));
        assertEquals("MORNING", item.get("timeSlot"), "后端只回码，中文标签在前端（T10 起的同一取舍）");
        assertEquals("CONFIRMED", item.get("appointmentStatus"));
        assertEquals("WAITING", item.get("queueStatus"));
        assertEquals(12, ((Number) item.get("currentNumber")).intValue(), "PRD 108 行「当前叫号进度」");
        assertEquals(3, ((Number) item.get("waitingCount")).intValue(), "PRD 106 行「当前排队人数」");
        assertNotNull(item.get("queueUpdatedAt"), "队列行的更新时间，前端拿它显示「更新于」");
        assertTrue(String.valueOf(item.get("orderNo")).startsWith("YY"),
                "单号来自 T12 的预约单号，实际：" + item.get("orderNo"));
    }

    @Test
    void j37_appointmentWithoutQueueRow_isStillListedWithNullQueue() throws Exception {
        // 首版这张表可能一行都没有（没有生产者）。此时页面必须还能看见自己的预约，
        // 否则患者会以为挂号也消失了 —— 这是本卡以预约为骨架的全部理由。
        long patientId = createPatient("T16-乙");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));

        Map<?, ?> item = onlyItem(getJson("/user/queues"));

        assertEquals(appointmentId, ((Number) item.get("appointmentId")).longValue());
        assertNull(item.get("queueStatus"), "还没进队列就是 null，前端显示「暂未进入叫号队列」");
        assertNull(item.get("currentNumber"));
        assertNull(item.get("waitingCount"));
        assertNull(item.get("queueUpdatedAt"));
        assertEquals("CONFIRMED", item.get("appointmentStatus"), "预约本身照旧可见");
    }

    @Test
    void j37_unpaidAndCancelledAppointmentsAreExcluded() throws Exception {
        long patientId = createPatient("T16-丙");
        long unpaid = book(patientId, createSchedule(1L, 12));
        long cancelled = paidAppointment(patientId, createSchedule(1L, 13));
        // 退号走 T13 的真实端点（不是写库），顺带回归一次
        mockMvc.perform(post("/user/appointments/" + cancelled + "/cancel")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        List<?> items = expectDataList(getJson("/user/queues"));
        List<Long> listed = items.stream().map(i -> ((Number) asMap(i).get("appointmentId")).longValue()).toList();
        assertFalse(listed.contains(unpaid),
                "卡片 453 行第 ⑧ 步「支付成功 → 预约状态 CONFIRMED」：没付钱还没挂上号，不该出现在叫号队列里"
                        + "（注意：它已经在 ⑤⑥ 扣了号源，所以排除是业务口径，不是数据缺失）");
        assertFalse(listed.contains(cancelled), "退了号还显示排队，就是叫一个不存在的人");
    }

    @Test
    void j37_completedAppointmentStaysListed() throws Exception {
        // PRD 107 行「防止过号」的意义就在于患者能看见"已经叫过了"；只看未完成会让叫错过的号凭空消失
        long patientId = createPatient("T16-丁");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));
        insertQueue(appointmentId, 1, 0, "DONE");
        // 状态改成 COMPLETED 是测试夹具动作：产品代码里只有 T12 那个支付方法许改预约状态
        jdbcTemplate.update("UPDATE appointment SET status = 'COMPLETED' WHERE id = ?", appointmentId);

        Map<?, ?> item = onlyItem(getJson("/user/queues"));
        assertEquals("COMPLETED", item.get("appointmentStatus"));
        assertEquals("DONE", item.get("queueStatus"), "队列已完成也要能查见，这是过号补看的依据");
    }

    @Test
    void j37_pastAppointmentIsExcluded() throws Exception {
        long patientId = createPatient("T16-戊");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));
        long probeId = appointmentId;
        // 把时间挪到昨天：PRD 103 行的词是「当前」，旧预约的队列早已无意义
        jdbcTemplate.update("UPDATE appointment SET appointment_time = DATE_SUB(NOW(), INTERVAL 2 DAY) "
                + "WHERE id = ?", probeId);

        List<?> items = expectDataList(getJson("/user/queues"));
        assertEquals(0, items.size(), "今天零点以前的预约不进候诊列表，实际：" + items);
    }

    @Test
    void j37_listIsAscendingByAppointmentTime() throws Exception {
        // 与 T13 预约记录列表的倒序刻意相反：这边患者问的是「下一个该我了吗」
        long patientId = createPatient("T16-己");
        long later = paidAppointment(patientId, createSchedule(1L, 12));
        long sooner = paidAppointment(patientId, createSchedule(1L, 10));

        List<?> items = expectDataList(getJson("/user/queues"));
        assertEquals(2, items.size());
        assertEquals(sooner, ((Number) asMap(items.get(0)).get("appointmentId")).longValue(),
                "就诊时间近的排最前");
        assertEquals(later, ((Number) asMap(items.get(1)).get("appointmentId")).longValue());
    }

    @Test
    void j37_responseShapeCarriesNoMoneyAndNoQueueId() throws Exception {
        long patientId = createPatient("T16-庚");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));
        insertQueue(appointmentId, 5, 2, "CALLING");

        Map<?, ?> item = onlyItem(getJson("/user/queues"));
        assertFalse(item.containsKey("feeFen"),
                "候诊页与费用无关，放上挂号费只会让人以为这里要付钱");
        assertFalse(item.containsKey("queueId"),
                "queue_status.id 不外放：客户端只用 appointmentId 认队列，外传流水表主键就是 T14 那个坑");
        assertEquals(List.of("appointmentId", "appointmentStatus", "appointmentTime", "currentNumber",
                "departmentName", "doctorName", "orderNo", "patientName", "queueStatus", "queueUpdatedAt",
                "timeSlot", "waitingCount"),
                new ArrayList<>(new java.util.TreeSet<>(item.keySet())),
                "字段清单唯一出处就是 DTO 的注释，多一个少一个都要改文档（这条断言当场逮到我漏数了 "
                        + "appointmentTime）");
    }

    // ============================================================
    // J38 实时更新 → 状态刷新
    // ============================================================

    @Test
    void j38_queueUpdateIsReflectedOnNextPoll() throws Exception {
        long patientId = createPatient("T16-辛");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));
        insertQueue(appointmentId, 0, 15, "WAITING");

        Map<?, ?> first = onlyItem(getJson("/user/queues"));
        assertEquals(0, ((Number) first.get("currentNumber")).intValue());
        assertEquals("WAITING", first.get("queueStatus"));

        // 模拟叫号系统推进队列（本卡没有生产者，这一步就是"外部写了这张表"的替身）
        jdbcTemplate.update("UPDATE queue_status SET current_number = 14, waiting_count = 1, "
                + "status = 'CALLING', updated_at = NOW(3) WHERE appointment_id = ?", appointmentId);

        Map<?, ?> second = onlyItem(getJson("/user/queues"));
        assertEquals(14, ((Number) second.get("currentNumber")).intValue(),
                "下一次轮询必须读到新号，否则 ≤10 秒刷新频率（PRD 477 行）毫无意义");
        assertEquals(1, ((Number) second.get("waitingCount")).intValue());
        assertEquals("CALLING", second.get("queueStatus"));
        assertNotEqualsText(first.get("queueUpdatedAt"), second.get("queueUpdatedAt"),
                "更新时间要一起变，前端才能显示「更新于 x 秒前」");
    }

    @Test
    void j38_noCacheBetweenReads_andEndpointWritesNothing() throws Exception {
        long patientId = createPatient("T16-壬");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));
        insertQueue(appointmentId, 3, 8, "WAITING");

        Map<String, Object> queueBefore = jdbcTemplate.queryForMap(
                "SELECT current_number, waiting_count, status FROM queue_status WHERE appointment_id = ?",
                appointmentId);
        Map<?, ?> first = onlyItem(getJson("/user/queues"));
        Map<?, ?> second = onlyItem(getJson("/user/queues"));
        assertEquals(first, second, "同一份数据连读两次必须一致（服务端不掺时间戳之类的随机量）");

        assertEquals(queueBefore, jdbcTemplate.queryForMap(
                        "SELECT current_number, waiting_count, status FROM queue_status WHERE appointment_id = ?",
                        appointmentId),
                "本卡全部只读：GET 一次都不许改动队列行");
        assertEquals(0, count("SELECT COUNT(*) FROM audit_log WHERE target_type = 'queue_status'"),
                "只读接口不写审计（写操作才留痕）");
    }

    // ============================================================
    // 归属与角色
    // ============================================================

    @Test
    void j37_otherUsersSeeNothingOfMine() throws Exception {
        long myPatient = createPatient("T16-癸");
        long myAppointment = paidAppointment(myPatient, createSchedule(1L, 10));
        insertQueue(myAppointment, 9, 2, "SERVING");

        String other = newPatientToken("t16-other");
        List<?> theirs = expectDataList(mockMvc.perform(get("/user/queues")
                .header("Authorization", "Bearer " + other)).andReturn());
        assertEquals(0, theirs.size(), "别人一个就诊人都没有 → 空数组，不是 null 也不是别人的队");

        // 再造一个"有预约有队列"的别人，证明 IN(我的就诊人) 这一跳真的挡住了
        long theirPatient = createPatientFor("T16-丑", other);
        long theirAppointment = paidAppointmentFor(theirPatient, createSchedule(2L, 10), other);
        insertQueue(theirAppointment, 1, 1, "CALLING");

        List<?> mine = expectDataList(getJson("/user/queues"));
        List<Long> mineIds = mine.stream()
                .map(i -> ((Number) asMap(i).get("appointmentId")).longValue()).toList();
        assertEquals(List.of(myAppointment), mineIds, "只回自己的那条，实际：" + mineIds);
    }

    @Test
    void newUserWithoutPatientsGetsEmptyList() throws Exception {
        String fresh = newPatientToken("t16-fresh");
        mockMvc.perform(get("/user/queues").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void staffAndAnonymousCannotReachQueueEndpoint() throws Exception {
        long patientId = createPatient("T16-寅");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));
        insertQueue(appointmentId, 2, 2, "WAITING");

        mockMvc.perform(get("/user/queues").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/queues")).andExpect(status().isUnauthorized());
    }

    @Test
    void queueStatusOfSomeoneElsesAppointmentIsNotLeakedEvenWhenIdsAreGuessed() throws Exception {
        // 排队数字本身是敏感信息：别人排到第几号、前面几个人，改一个 id 就能窥见
        long patientId = createPatient("T16-卯");
        long appointmentId = paidAppointment(patientId, createSchedule(1L, 10));
        insertQueue(appointmentId, 7, 4, "WAITING");

        String other = newPatientToken("t16-prober");
        List<?> theirs = expectDataList(mockMvc.perform(get("/user/queues")
                .header("Authorization", "Bearer " + other)).andReturn());
        assertEquals(0, theirs.size());
        assertTrue(theirs.stream().noneMatch(i -> appointmentId == ((Number) asMap(i).get("appointmentId")).longValue()),
                "猜测 appointment_id 也挤不进别人的列表（归属经 patient.user_id 跳两次）");
    }

    // ============================================================
    // 辅助
    // ============================================================

    private Map<?, ?> asMap(Object item) {
        return (Map<?, ?>) item;
    }

    private void assertNotEqualsText(Object before, Object after, String message) {
        assertFalse(java.util.Objects.equals(String.valueOf(before), String.valueOf(after)),
                message + "，两次读数：" + before + " / " + after);
    }

    private Map<?, ?> onlyItem(MvcResult result) throws Exception {
        List<?> items = expectDataList(result);
        assertEquals(1, items.size(), "期望恰好一条，实际：" + items);
        return asMap(items.get(0));
    }

    private MvcResult getJson(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + patientToken)).andReturn();
    }

    private long createSchedule(long doctorId, int plusDays) throws Exception {
        LocalDate date = LocalDate.now().plusYears(PROBE_YEAR_OFFSET).plusDays(plusDays);
        Map<?, ?> data = expectData(mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", doctorId, "date", date.toString(),
                                "timeSlot", "MORNING", "totalSlots", 5))))
                .andExpect(status().isOk())
                .andReturn());
        return ((Number) data.get("id")).longValue();
    }

    private long book(long patientId, long scheduleId) throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", scheduleId))))
                .andExpect(status().isOk())
                .andReturn());
        long id = ((Number) data.get("id")).longValue();
        createdAppointmentIds.add(id);
        return id;
    }

    /** 走 T12 的真实支付端点把预约推到 CONFIRMED —— 顺带回归一次挂号链路 */
    private long paidAppointment(long patientId, long scheduleId) throws Exception {
        long id = book(patientId, scheduleId);
        expectData(mockMvc.perform(post("/user/appointments/" + id + "/pay")
                .header("Authorization", "Bearer " + patientToken)).andReturn());
        return id;
    }

    private long paidAppointmentFor(long patientId, long scheduleId, String token) throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/user/appointments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "scheduleId", scheduleId))))
                .andReturn());
        long id = ((Number) data.get("id")).longValue();
        createdAppointmentIds.add(id);
        expectData(mockMvc.perform(post("/user/appointments/" + id + "/pay")
                .header("Authorization", "Bearer " + token)).andReturn());
        return id;
    }

    /**
     * 裸插排队行：本卡没有写这张表的产品代码（见类注释），所以"外部叫号系统推进了队列"
     * 只能这么表达。updated_at 交给 DDL 的 CURRENT_TIMESTAMP(3)，与 T06-0 之后
     * 「走自定义 SQL 时 MetaObjectHandler 不生效」的既有结论一致。
     */
    private void insertQueue(long appointmentId, int currentNumber, int waitingCount, String status) {
        jdbcTemplate.update("INSERT INTO queue_status (appointment_id, current_number, waiting_count, status) "
                + "VALUES (?, ?, ?, ?)", appointmentId, currentNumber, waitingCount, status);
        createdQueueAppointmentIds.add(appointmentId);
    }

    private long createPatient(String name) throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t16-owner");
        }
        return createPatientFor(name, patientToken);
    }

    private long createPatientFor(String name, String token) throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "cardNo", randomCardNo(),
                                "idCard", "110101199003071234", "phone", "13900002345",
                                "relation", "SELF"))))
                .andExpect(status().isOk())
                .andReturn());
        long id = ((Number) data.get("id")).longValue();
        createdPatientIds.add(id);
        return id;
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

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put("queue_status", count("SELECT COUNT(*) FROM queue_status"));
        snapshot.put("appointment", count("SELECT COUNT(*) FROM appointment"));
        snapshot.put("payment_record", count("SELECT COUNT(*) FROM payment_record"));
        snapshot.put("schedule", count("SELECT COUNT(*) FROM schedule"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String randomCardNo() {
        return "T16" + String.format("%07d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
    }

    private String json(Map<?, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<?, ?> expectData(MvcResult result) throws Exception {
        Map<?, ?> root = objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }

    private List<?> expectDataList(MvcResult result) throws Exception {
        Map<?, ?> root = objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        return (List<?>) data;
    }
}
