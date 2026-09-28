package com.hospital.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T11 排班管理的必做场景：J24（创建排班 → 数据正确）、J25（重复排班 → 被拒）、
 * J26（取消排班 → 剩余号源恢复），外加调整号源、审计同事务、四角色越权，
 * 以及 DoD 里点名的「唯一索引验证」。
 *
 * <p>需要本机 MySQL 与 Redis 都在跑（与 CatalogIntegrationTest / PatientIntegrationTest 同一前提）。
 *
 * <p><b>探针数据怎么打标（schedule 表没有 name 列可用）</b>：排班行改用<b>日期</b>做标记——
 * 一律排在 {@code 今天 + 5 年}，清理条件是 {@code date >= CURDATE() + 4 年}。
 * seed.sql:104-124 只造 CURDATE()-7..+7 共 150 行，四年以外的行只可能是本类造的，
 * 所以这个条件既删得干净又不可能误伤种子。探针预约则用 {@code order_no LIKE 'T11-AP-%'} 打标。
 *
 * <p><b>清理为什么走原生 SQL</b>：{@code BaseEntity.deleted} 上有 {@code @TableLogic}，
 * {@code mapper.deleteById} 是逻辑删，行还在库里，{@code COUNT(*)} 断言就会红
 * （T06 AuditFieldFillTest 曾这样往 title 表漏了 11 行，T10 才清掉）。
 *
 * <p><b>审计行的清理与 T04 的决定不同，理由记在这里</b>：WORK_LOG 里 T05 的收尾写过
 * 「审计表按不可抹除对待，保持现状不补删」，那是针对 T04 留下的<b>一条</b> APPROVE_REFUND——
 * 数量有界。本类每跑一轮会写十几条 CREATE/UPDATE/CANCEL_SCHEDULE，跑十次就是一百多条，
 * 无界增长会把开发库的审计表变成测试垃圾场，也会让后续卡片"audit_log 总数"类的核查失去基线。
 * 所以这里按 T05 的做法删掉<b>本卡自己那三个 action</b>（精确到 target_type='schedule'），
 * T04/T05 留下的行一律不动。
 *
 * <p><b>断言口径</b>：期望值来自 seed.sql 的字面数据（医生 1=张伟 / 2=李慧敏 / 3=王建国，
 * 预约 SEED-AP-0006 落在医生 1 的 +1 天上午）或本类自己造的探针行，不调 ScheduleService 反算。
 * 业务错误一律 HTTP 200 + body.code（GlobalExceptionHandler 对 BizException 不设状态码），
 * 401/403 才带 HTTP 状态码（Spring Security 的两个处理器）。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class ScheduleIntegrationTest {

    /** 探针排班的日期偏移：五年后。清理条件按四年切，中间留一年余量 */
    private static final int PROBE_YEAR_OFFSET = 5;
    private static final String PROBE_AP_PREFIX = "T11-AP-";
    private static final List<String> AUDIT_ACTIONS =
            List.of("CREATE_SCHEDULE", "UPDATE_SCHEDULE", "CANCEL_SCHEDULE");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    private String adminToken;
    private String systemToken;
    private String doctorToken;
    private String nurseToken;
    private String patientToken;

    @BeforeEach
    void setUp() throws Exception {
        countsBefore = snapshotCounts();
        // adminId 与 V2__init_admin.sql 的 4 行对齐：1=admin 2=system 3=doctor 4=nurse。
        // caps 声明成什么并不影响 @RequireCap——RequireCapAspect 是按 roleName 去
        // PermissionService 查的，token 里的 caps 只给前端用。
        adminToken = staffToken(1L, "admin");
        systemToken = staffToken(2L, "system");
        doctorToken = staffToken(3L, "doctor");
        nurseToken = staffToken(4L, "nurse");
        patientToken = newPatientToken("j24");
    }

    @AfterEach
    void cleanupAndAssertSeedUntouched() {
        jdbcTemplate.update("DELETE FROM appointment WHERE order_no LIKE ?", PROBE_AP_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL 4 YEAR)");
        jdbcTemplate.update("DELETE FROM audit_log WHERE target_type = 'schedule' AND action IN ('"
                + String.join("','", AUDIT_ACTIONS) + "')");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        createdUserIds.clear();
        assertEquals(countsBefore, snapshotCounts(),
                "T11 只动自己造的探针行：schedule/appointment/doctor/department 的行数必须回到基线");
    }

    // ============================================================
    // J24 创建排班 → 数据正确
    // ============================================================

    /** 卡片 431 行「创建排班：选择医生/日期/时段/号源数量」四项全部落库，剩余号源 = 总号源 */
    @Test
    void j24_createSchedule_persistsAllFourFields() throws Exception {
        LocalDate date = probeDate(1);

        Map<?, ?> data = expectData(mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 1, "date", date.toString(),
                                "timeSlot", "MORNING", "totalSlots", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());

        assertNotNull(data.get("id"), "创建成功后必须回 id，后台页面要靠它做后续修改/取消");
        assertEquals(1, ((Number) data.get("doctorId")).intValue());
        assertEquals("张伟", data.get("doctorName"), "医生姓名由服务端 join 出来，不靠客户端上报");
        assertEquals(date.toString(), data.get("date"), "LocalDate 出参是 yyyy-MM-dd（JSR-310，不受 spring.jackson.date-format 影响）");
        assertEquals("MORNING", data.get("timeSlot"), "回码值不回中文，与 ScheduleItemResponse 一致");
        assertEquals(20, ((Number) data.get("totalSlots")).intValue());
        assertEquals(20, ((Number) data.get("remainingSlots")).intValue(), "新排班没人约过，剩余 = 总号源");
        assertNull(data.get("deleted"), "内部存储细节不出参");
        assertNull(data.get("createdAt"), "内部存储细节不出参");

        Map<String, Object> row = rawSchedule(((Number) data.get("id")).longValue());
        assertEquals(0, ((Number) row.get("deleted")).intValue(), "库里是活行");
        assertEquals(20, ((Number) row.get("total_slots")).intValue());
        assertEquals(20, ((Number) row.get("remaining_slots")).intValue());
        assertEquals(date.toString(), String.valueOf(row.get("date")));
    }

    /** V1:105 的三个码值都要能建，EVENING 是种子里没有的那个（seed 只造 MORNING/AFTERNOON） */
    @Test
    void j24_createSchedule_acceptsEveningSlot() throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 2, "date", probeDate(2).toString(),
                                "timeSlot", "EVENING", "totalSlots", 8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());

        assertEquals("EVENING", data.get("timeSlot"));
        assertEquals("李慧敏", data.get("doctorName"));
        assertEquals(8, ((Number) data.get("remainingSlots")).intValue());
    }

    /** system 角色同样有 MANAGE_DOCTOR 能力（PermissionService.ROLE_CAPS 给 system/admin 全部能力） */
    @Test
    void j24_createSchedule_systemRoleCanWrite() throws Exception {
        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + systemToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 3, "date", probeDate(3).toString(),
                                "timeSlot", "AFTERNOON", "totalSlots", 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.doctorName").value("王建国"));
    }

    /** 卡片 430 行「排班列表：展示医生排班（日期/时段/总号源/剩余号源）」+ 医生维度筛选 */
    @Test
    void j24_listSchedules_filtersByDoctorAndDateRange() throws Exception {
        LocalDate day1 = probeDate(4);
        LocalDate day2 = probeDate(5);
        long morningId = createSchedule(1, day2, "MORNING", 20);
        long eveningId = createSchedule(1, day2, "EVENING", 5);
        long afternoonId = createSchedule(1, day1, "AFTERNOON", 15);
        createSchedule(2, day1, "MORNING", 9);

        Map<?, ?> root = expectRoot(mockMvc.perform(get("/admin/schedules")
                        .param("doctorId", "1")
                        .param("dateFrom", day1.toString())
                        .param("dateTo", day2.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());

        List<?> rows = (List<?>) root.get("data");
        assertEquals(3, rows.size(), "医生 2 的那条被 doctorId 筛掉");
        // 日期升序，同日按 上午→下午→晚上（字母序会把 AFTERNOON 排到 MORNING 前，所以必须显式权重）
        assertEquals(day1.toString(), at(rows, 0).get("date"));
        assertEquals("AFTERNOON", at(rows, 0).get("timeSlot"));
        assertEquals(afternoonId, ((Number) at(rows, 0).get("id")).longValue());
        assertEquals(day2.toString(), at(rows, 1).get("date"));
        assertEquals("MORNING", at(rows, 1).get("timeSlot"));
        assertEquals(morningId, ((Number) at(rows, 1).get("id")).longValue());
        assertEquals("EVENING", at(rows, 2).get("timeSlot"));
        assertEquals(eveningId, ((Number) at(rows, 2).get("id")).longValue());
        assertEquals("张伟", at(rows, 0).get("doctorName"), "跨医生的列表必须自带医生姓名");
        assertNull(at(rows, 0).get("deleted"));
    }

    /** 列表只返回活排班：取消掉的那条不再出现（@TableLogic 自动补 deleted = 0） */
    @Test
    void j24_listSchedules_excludesCancelled() throws Exception {
        LocalDate date = probeDate(6);
        long kept = createSchedule(4, date, "MORNING", 10);
        long cancelled = createSchedule(4, date, "AFTERNOON", 10);
        cancelSchedule(cancelled, null);

        List<?> rows = (List<?>) expectRoot(mockMvc.perform(get("/admin/schedules")
                        .param("doctorId", "4")
                        .param("dateFrom", date.toString())
                        .param("dateTo", date.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("data");

        assertEquals(1, rows.size());
        assertEquals(kept, ((Number) at(rows, 0).get("id")).longValue());
    }

    /** 时段码值不在 V1:105 那三个里 → 400，且不落库 */
    @Test
    void j24_createSchedule_invalidTimeSlotRejected() throws Exception {
        LocalDate date = probeDate(7);
        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 1, "date", date.toString(),
                                "timeSlot", "NOON", "totalSlots", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("MORNING/AFTERNOON/EVENING")));

        assertEquals(0, countSchedules(1, date, "NOON"));
    }

    /** schedule.doctor_id 没有外键约束，不校验就会静默造出指向不存在医生的排班 */
    @Test
    void j24_createSchedule_unknownDoctorRejected() throws Exception {
        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 999999, "date", probeDate(8).toString(),
                                "timeSlot", "MORNING", "totalSlots", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    /** 参数校验：号源数量缺失或非正数都进不去 service */
    @Test
    void j24_createSchedule_totalSlotsMustBePositive() throws Exception {
        LocalDate date = probeDate(9);
        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 1, "date", date.toString(),
                                "timeSlot", "MORNING", "totalSlots", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("号源数量必须大于 0")));

        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":1,\"date\":\"" + date + "\",\"timeSlot\":\"MORNING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("号源数量不能为空")));

        assertEquals(0, countSchedules(1, date, "MORNING"));
    }

    /** PRD 485 行「管理后台操作需记录审计日志」；创建时 id 还不存在，所以 target_id 为 NULL */
    @Test
    void j24_createSchedule_writesAuditRow() throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 5, "date", probeDate(10).toString(),
                                "timeSlot", "MORNING", "totalSlots", 30))))
                .andExpect(status().isOk())
                .andReturn());

        Map<String, Object> audit = latestAudit("CREATE_SCHEDULE", null);
        assertNotNull(audit, "后台写操作必须留下审计行");
        assertEquals(1, ((Number) audit.get("operator_id")).intValue(), "operatorId 取自 token 的 adminId");
        assertEquals("ADMIN", audit.get("operator_type"), "admin 角色映射为 ADMIN（OperatorType.fromRole）");
        assertEquals("schedule", audit.get("target_type"));
        assertNull(audit.get("target_id"), "创建时还没有 id，切面只认 @AuditTarget 标的 Long 入参");
        // detail 落的是 JSON 列（V1:409），MySQL 存完再吐出来时会重新规范化格式（键值之间带空格），
        // 所以不能按字面量子串比对，解析后按路径取。
        // 切面按「参数名 → 入参」组装（AuditLogAspect.buildDetail），create 的入参名叫 request，因此多一层嵌套。
        JsonNode detail = objectMapper.readTree(String.valueOf(audit.get("detail")));
        assertEquals(30, detail.path("request").path("totalSlots").asInt(),
                "detail 应含请求参数，实际：" + audit.get("detail"));
        assertEquals("MORNING", detail.path("request").path("timeSlot").asText());
        assertEquals(5, detail.path("request").path("doctorId").asInt());
        assertNotNull(data.get("id"));
    }

    // ============================================================
    // J25 重复排班 → 被拒（R2 硬约束，卡片 432 行）
    // ============================================================

    /** R2 第一层：后端前置查。同医生同日期同时段已有活排班 → 2002，且不新增行 */
    @Test
    void j25_duplicateLiveScheduleRejected() throws Exception {
        LocalDate date = probeDate(11);
        long first = createSchedule(1, date, "MORNING", 20);

        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 1, "date", date.toString(),
                                "timeSlot", "MORNING", "totalSlots", 99))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2002))
                .andExpect(jsonPath("$.message").value("排班冲突"));

        assertEquals(1, countSchedules(1, date, "MORNING"), "被拒的创建不得留下第二行");
        assertEquals(20, ((Number) rawSchedule(first).get("total_slots")).intValue(), "原有排班不被覆盖");
    }

    /** R2 的唯一键是 (doctor_id, date, time_slot) 三元组：换时段就不算重复 */
    @Test
    void j25_sameDoctorSameDayDifferentSlotAllowed() throws Exception {
        LocalDate date = probeDate(12);
        createSchedule(1, date, "MORNING", 20);

        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 1, "date", date.toString(),
                                "timeSlot", "AFTERNOON", "totalSlots", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(2, countSchedulesOfDoctorOnDate(1, date));
    }

    /** seed.sql:106 明写「同一 date + time_slot 下有多名医生」是要求的场景，不能误拦 */
    @Test
    void j25_sameSlotDifferentDoctorAllowed() throws Exception {
        LocalDate date = probeDate(13);
        createSchedule(1, date, "MORNING", 20);

        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 2, "date", date.toString(),
                                "timeSlot", "MORNING", "totalSlots", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1, countSchedules(1, date, "MORNING"));
        assertEquals(1, countSchedules(2, date, "MORNING"));
    }

    /** 拿 seed 的真实排班撞一次：医生 1 在 CURDATE()+1 的上午已经有班（seed.sql:108-124） */
    @Test
    void j25_conflictWithSeedScheduleRejected() throws Exception {
        String date = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '%Y-%m-%d')", String.class);
        int before = countSchedules(1, LocalDate.parse(date), "MORNING");
        assertEquals(1, before, "前提：种子里这一天这个时段确实已有一条排班");

        mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 1, "date", date,
                                "timeSlot", "MORNING", "totalSlots", 50))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2002));

        assertEquals(before, countSchedules(1, LocalDate.parse(date), "MORNING"), "种子数据一行不变");
    }

    /**
     * DoD「唯一索引验证」：绕开 service 的前置查，用原生 SQL 插第二条，
     * 必须由 {@code uk_doctor_date_slot}（V1:111）挡下来。
     * 这一层是并发下唯一可靠的保障，前置查只是把常见情况提前变成友好错误码。
     */
    @Test
    void j25_uniqueIndexRejectsDuplicateAtDatabaseLevel() throws Exception {
        LocalDate date = probeDate(14);
        createSchedule(3, date, "AFTERNOON", 10);

        DuplicateKeyException ex = assertThrows(DuplicateKeyException.class, () ->
                jdbcTemplate.update("INSERT INTO schedule (doctor_id, `date`, time_slot, total_slots, remaining_slots) "
                        + "VALUES (?, ?, ?, ?, ?)", 3, java.sql.Date.valueOf(date), "AFTERNOON", 10, 10));

        assertTrue(String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uk_doctor_date_slot"),
                "撞的必须是那个唯一索引，实际：" + ex.getMostSpecificCause().getMessage());
        assertEquals(1, countSchedules(3, date, "AFTERNOON"));
    }

    /**
     * R2 第二层的端到端证明：8 个请求同时创建同一个槽位，
     * 前置查可能全部通过（彼此都还没提交），此时唯一索引必须保证只有一行落地。
     *
     * <p>断言是确定性的（不许出现 500、只许有一个 200、库里只有一行）；
     * 具体有几个请求走到 DuplicateKeyException 分支取决于线程调度，
     * 所以不把它写成断言——但只要出现 2002 而不是 500，就说明 controller 的翻译生效了。
     */
    @Test
    void j25_concurrentCreates_onlyOneRowSurvives() throws Exception {
        int threads = 8;
        LocalDate date = probeDate(15);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await();
                    MvcResult result = mockMvc.perform(post("/admin/schedules")
                                    .header("Authorization", "Bearer " + adminToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("doctorId", 5, "date", date.toString(),
                                            "timeSlot", "MORNING", "totalSlots", 10))))
                            .andReturn();
                    Map<?, ?> root = objectMapper.readValue(
                            result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
                    return ((Number) root.get("code")).intValue();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "8 个线程都应就位");
            fire.countDown();

            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                codes.add(future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(1, Collections.frequency(codes, 200), "只允许一个请求创建成功，实际返回码：" + codes);
            assertEquals(threads - 1, Collections.frequency(codes, 2002),
                    "其余请求必须拿到 2002 而不是 500，实际返回码：" + codes);
            assertEquals(1, countSchedules(5, date, "MORNING"));
        } finally {
            pool.shutdownNow();
        }
    }

    // ============================================================
    // J26 取消排班 → 剩余号源恢复
    // ============================================================

    /**
     * 取消 = 软删 + 剩余号源归位到总号源（一条 UPDATE 完成，见 ScheduleMapper.cancelById）。
     * 号源"恢复"的完整含义还包括：患者端立刻看不到这个时段、且该槽位可以重新排班（下一个用例）。
     */
    @Test
    void j26_cancelSchedule_softDeletesAndRestoresSlots() throws Exception {
        LocalDate date = probeDate(16);
        long id = createSchedule(1, date, "MORNING", 20);
        // 造出"已被占掉 14 个号"的状态：直接改号源账本，不造假预约（预约是 T12 的口径）
        jdbcTemplate.update("UPDATE schedule SET remaining_slots = 6 WHERE id = ?", id);
        int availableWithProbe = availableCountOfDoctor1();

        cancelSchedule(id, "医生临时会诊");

        Map<String, Object> row = rawSchedule(id);
        assertEquals(1, ((Number) row.get("deleted")).intValue(), "取消是逻辑删，行还在（历史预约要引用它）");
        assertEquals(20, ((Number) row.get("remaining_slots")).intValue(), "剩余号源恢复到总号源");
        assertEquals(20, ((Number) row.get("total_slots")).intValue(), "总号源不动");
        assertEquals(availableWithProbe - 1, availableCountOfDoctor1(),
                "患者端「可约时段数」立刻少一条 —— 取消必须传导到小程序看到的号源");
    }

    /**
     * 卡片 437 行「排班取消时，已预约的记录需处理」在本卡的落点：<b>拒绝</b>。
     * 用种子里真实的一笔 CONFIRMED 预约（SEED-AP-0006：就诊人 1 / 医生 1 / CURDATE()+1 / 上午）验证，
     * 不造假数据，也不动种子一行。
     */
    @Test
    void j26_cancelScheduleWithActiveAppointmentRejected() throws Exception {
        Map<String, Object> seed = jdbcTemplate.queryForMap(
                "SELECT s.id, s.remaining_slots FROM schedule s "
                        + "JOIN appointment a ON a.schedule_id = s.id "
                        + "WHERE a.order_no = 'SEED-AP-0006' AND s.deleted = 0");
        long id = ((Number) seed.get("id")).longValue();
        int remainingBefore = ((Number) seed.get("remaining_slots")).intValue();
        long auditBefore = countAudit("CANCEL_SCHEDULE", id);

        mockMvc.perform(delete("/admin/schedules/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2007))
                .andExpect(jsonPath("$.message").value("该排班已有预约，请先退号后再取消"));

        Map<String, Object> after = rawSchedule(id);
        assertEquals(0, ((Number) after.get("deleted")).intValue(), "被拒的取消不得改动排班");
        assertEquals(remainingBefore, ((Number) after.get("remaining_slots")).intValue(), "号源也不得改动");
        assertEquals(auditBefore, countAudit("CANCEL_SCHEDULE", id),
                "业务被拒 → 审计一并回滚（附录 B「新写操作有没有写 audit_log？在同一事务内吗？」）");
    }

    /** 守卫口径与 seed.sql:131 一致：status = CANCELLED 的预约不占号源，因此不该拦住取消 */
    @Test
    void j26_cancelScheduleWithOnlyCancelledAppointmentSucceeds() throws Exception {
        LocalDate date = probeDate(17);
        long id = createSchedule(1, date, "MORNING", 20);
        insertProbeAppointment(PROBE_AP_PREFIX + "CANCELLED", id, 1, "CANCELLED");

        cancelSchedule(id, "已退号后停诊");

        assertEquals(1, ((Number) rawSchedule(id).get("deleted")).intValue());
    }

    /**
     * 号源"恢复"的另一半：取消过的槽位必须能重新排班。
     * {@code uk_doctor_date_slot} 不含 deleted 列，软删行仍占着索引位，
     * 所以创建时会复活原来那一行（id 不变），历史预约的 schedule_id 依然有效——
     * 与 T08-G 就诊人「本人同卡号可复活」同一个模式。
     */
    @Test
    void j26_cancelledSlotCanBeRescheduledAndRevivesRow() throws Exception {
        LocalDate date = probeDate(18);
        long id = createSchedule(2, date, "MORNING", 20);
        cancelSchedule(id, "停诊");

        Map<?, ?> data = expectData(mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 2, "date", date.toString(),
                                "timeSlot", "MORNING", "totalSlots", 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());

        assertEquals(id, ((Number) data.get("id")).longValue(), "复活的是原来那一行，id 不变");
        assertEquals(12, ((Number) data.get("totalSlots")).intValue(), "号源按新填的覆盖");
        assertEquals(12, ((Number) data.get("remainingSlots")).intValue(), "重新排班后剩余 = 总号源");
        Map<String, Object> row = rawSchedule(id);
        assertEquals(0, ((Number) row.get("deleted")).intValue(), "行已复活");
        assertEquals(1, countSchedules(2, date, "MORNING"), "全过程只有一行，唯一索引没被绕过");
    }

    @Test
    void j26_cancelNonexistentScheduleReturns2001() throws Exception {
        mockMvc.perform(delete("/admin/schedules/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
    }

    /** 取消过的排班再取消一次 = 2001，而不是"取消成功"的假象 */
    @Test
    void j26_cancelTwiceSecondTimeIs2001() throws Exception {
        long id = createSchedule(3, probeDate(19), "MORNING", 10);
        cancelSchedule(id, null);

        mockMvc.perform(delete("/admin/schedules/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
    }

    /** 取消原因是审计备注：落 audit_log.reason，不落 schedule 表（该表没有原因列） */
    @Test
    void j26_cancelSchedule_writesAuditWithTargetAndReason() throws Exception {
        long id = createSchedule(4, probeDate(20), "MORNING", 10);
        cancelSchedule(id, "医生出差");

        Map<String, Object> audit = latestAudit("CANCEL_SCHEDULE", id);
        assertNotNull(audit, "取消必须留痕");
        assertEquals(1, ((Number) audit.get("operator_id")).intValue());
        assertEquals("ADMIN", audit.get("operator_type"));
        assertEquals(id, ((Number) audit.get("target_id")).longValue(), "targetId 来自 @AuditTarget 标注的入参");
        assertEquals("医生出差", audit.get("reason"), "reason 来自 @AuditReason 标注的入参");
        assertFalse(rawSchedule(id).containsKey("reason"),
                "取消原因只进审计：schedule 表没有原因列，不该被凭空加上（加列属 T25「临时停诊」的规格）");
    }

    // ============================================================
    // 修改排班 = 调整号源（卡片 433 行）
    // ============================================================

    /** 已约数保持不变，剩余号源随总数同增减：20 剩 6（已约 14）→ 总数 30 则剩 16 */
    @Test
    void updateSlots_keepsBookedCountAndShiftsRemaining() throws Exception {
        LocalDate date = probeDate(21);
        long id = createSchedule(1, date, "MORNING", 20);
        jdbcTemplate.update("UPDATE schedule SET remaining_slots = 6 WHERE id = ?", id);

        Map<?, ?> data = expectData(mockMvc.perform(put("/admin/schedules/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("totalSlots", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());

        assertEquals(30, ((Number) data.get("totalSlots")).intValue());
        assertEquals(16, ((Number) data.get("remainingSlots")).intValue(), "已约 14 个号不能被抹掉");
        assertEquals(16, ((Number) rawSchedule(id).get("remaining_slots")).intValue());
    }

    /** 新总数小于已约数 → 400 带明确文案，且库里一行不改 */
    @Test
    void updateSlots_belowBookedCountRejected() throws Exception {
        long id = createSchedule(1, probeDate(22), "MORNING", 20);
        jdbcTemplate.update("UPDATE schedule SET remaining_slots = 5 WHERE id = ?", id);

        mockMvc.perform(put("/admin/schedules/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("totalSlots", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("已约 15")));

        Map<String, Object> row = rawSchedule(id);
        assertEquals(20, ((Number) row.get("total_slots")).intValue());
        assertEquals(5, ((Number) row.get("remaining_slots")).intValue());
    }

    @Test
    void updateSlots_nonexistentScheduleReturns2001() throws Exception {
        mockMvc.perform(put("/admin/schedules/999999")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("totalSlots", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void updateSlots_writesAuditRow() throws Exception {
        long id = createSchedule(5, probeDate(23), "AFTERNOON", 15);

        mockMvc.perform(put("/admin/schedules/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("totalSlots", 25))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> audit = latestAudit("UPDATE_SCHEDULE", id);
        assertNotNull(audit);
        assertEquals("schedule", audit.get("target_type"));
        JsonNode detail = objectMapper.readTree(String.valueOf(audit.get("detail")));
        assertEquals(25, detail.path("request").path("totalSlots").asInt(),
                "detail 应含新的号源数，实际：" + audit.get("detail"));
        assertEquals(id, detail.path("scheduleId").asLong(), "两个入参都记进去了，出问题时能还原是谁改的哪条");
    }

    // ============================================================
    // 角色隔离与能力校验（附录 B 第 804 条：权限不能只写在 UI）
    // ============================================================

    /** 患者 token 打后台接口 → Spring Security 直接 403（SecurityConfig 的 anyRequest 那条规则） */
    @Test
    void patientToken_cannotReachAdminScheduleEndpoints() throws Exception {
        long id = createSchedule(1, probeDate(24), "MORNING", 10);
        String body = json(Map.of("doctorId", 1, "date", probeDate(25).toString(),
                "timeSlot", "MORNING", "totalSlots", 10));

        for (var request : Arrays.asList(
                get("/admin/schedules").header("Authorization", "Bearer " + patientToken),
                post("/admin/schedules").header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body),
                put("/admin/schedules/" + id).header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("totalSlots", 99))),
                delete("/admin/schedules/" + id).header("Authorization", "Bearer " + patientToken))) {
            mockMvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(4001));
        }

        assertEquals(10, ((Number) rawSchedule(id).get("total_slots")).intValue(), "越权请求不得改动数据");
        assertEquals(0, ((Number) rawSchedule(id).get("deleted")).intValue());
    }

    /**
     * PRD 41 行「医生 | <b>查看</b>排班信息」+ PermissionService 里 doctor/nurse 的 caps 为空：
     * 能读列表，写操作一律 4001（RequireCapAspect 在 service 之前拦，所以也不会写审计）。
     */
    @Test
    void doctorAndNurse_canReadButCannotWrite() throws Exception {
        long id = createSchedule(1, probeDate(26), "MORNING", 10);

        for (String token : Arrays.asList(doctorToken, nurseToken)) {
            mockMvc.perform(get("/admin/schedules").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(post("/admin/schedules")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("doctorId", 1, "date", probeDate(27).toString(),
                                    "timeSlot", "EVENING", "totalSlots", 5))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(4001));

            mockMvc.perform(put("/admin/schedules/" + id)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("totalSlots", 99))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(4001));

            mockMvc.perform(delete("/admin/schedules/" + id)
                            .header("Authorization", "Bearer " + token).param("reason", "越权尝试"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(4001));
        }

        assertEquals(10, ((Number) rawSchedule(id).get("total_slots")).intValue());
        assertEquals(0, ((Number) rawSchedule(id).get("deleted")).intValue());
        assertEquals(0, countAudit("CANCEL_SCHEDULE", id), "被能力校验拦下的请求不得留审计行");
    }

    @Test
    void anonymous_gets401OnEveryEndpoint() throws Exception {
        long id = createSchedule(1, probeDate(28), "MORNING", 10);

        mockMvc.perform(get("/admin/schedules"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(post("/admin/schedules").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", 1, "date", probeDate(29).toString(),
                                "timeSlot", "MORNING", "totalSlots", 5))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(put("/admin/schedules/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("totalSlots", 99))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/admin/schedules/" + id))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));

        assertEquals(0, ((Number) rawSchedule(id).get("deleted")).intValue());
    }

    // ============================================================
    // 辅助
    // ============================================================

    private String staffToken(Long adminId, String role) {
        List<String> modules = List.of("dashboard", "schedule", "appointment");
        return jwtUtil.generateToken(adminId, role, role, modules, List.of());
    }

    private String newPatientToken(String tag) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", tag + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        createdUserIds.add(((Number) data.get("userId")).longValue());
        return String.valueOf(data.get("token"));
    }

    private LocalDate probeDate(int plusDays) {
        return LocalDate.now().plusYears(PROBE_YEAR_OFFSET).plusDays(plusDays);
    }

    /** 走 HTTP 建一条探针排班，返回 id */
    private long createSchedule(long doctorId, LocalDate date, String timeSlot, int totalSlots) throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/admin/schedules")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", doctorId, "date", date.toString(),
                                "timeSlot", timeSlot, "totalSlots", totalSlots))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
        return ((Number) data.get("id")).longValue();
    }

    private void cancelSchedule(long id, String reason) throws Exception {
        var builder = delete("/admin/schedules/" + id).header("Authorization", "Bearer " + adminToken);
        if (reason != null) {
            builder = builder.param("reason", reason);
        }
        mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void insertProbeAppointment(String orderNo, long scheduleId, long patientId, String status) {
        jdbcTemplate.update("INSERT INTO appointment (order_no, patient_id, doctor_id, schedule_id, status, "
                        + "appointment_time, fee_fen) VALUES (?, ?, ?, ?, ?, NOW(3), 0)",
                orderNo, patientId, 1L, scheduleId, status);
    }

    private Map<String, Object> rawSchedule(long id) {
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM schedule WHERE id = ?", id);
        // schedule.deleted 是 TINYINT(1)，MySQL 驱动默认 tinyInt1isBit=true，SELECT * 拿回来是 Boolean 而不是数字。
        // 在这里统一归一成 0/1，断言就不必为同一列写两套取值逻辑。
        row.put("deleted", Boolean.TRUE.equals(row.get("deleted")) ? 1 : 0);
        return row;
    }

    private int countSchedules(long doctorId, LocalDate date, String timeSlot) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule WHERE doctor_id = ? AND `date` = ? AND time_slot = ? AND deleted = 0",
                Integer.class, doctorId, java.sql.Date.valueOf(date), timeSlot);
        return count == null ? 0 : count;
    }

    private int countSchedulesOfDoctorOnDate(long doctorId, LocalDate date) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule WHERE doctor_id = ? AND `date` = ? AND deleted = 0",
                Integer.class, doctorId, java.sql.Date.valueOf(date));
        return count == null ? 0 : count;
    }

    /** 患者端「可约时段数」（T10 的 DoctorSummaryResponse.availableCount），用来证明取消会传导过去 */
    private int availableCountOfDoctor1() throws Exception {
        String body = mockMvc.perform(get("/user/doctors").param("departmentId", "1")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<?> doctors = (List<?>) objectMapper.readValue(body, Map.class).get("data");
        for (Object item : doctors) {
            Map<?, ?> doctor = (Map<?, ?>) item;
            if (((Number) doctor.get("id")).intValue() == 1) {
                return ((Number) doctor.get("availableCount")).intValue();
            }
        }
        throw new AssertionError("科室 1 的医生列表里找不到 id=1（张伟）");
    }

    private long countAudit(String action, Long targetId) {
        if (targetId == null) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE action = ? AND target_type = 'schedule' AND target_id IS NULL",
                    Long.class, action);
            return count == null ? 0L : count;
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = ? AND target_type = 'schedule' AND target_id = ?",
                Long.class, action, targetId);
        return count == null ? 0L : count;
    }

    private Map<String, Object> latestAudit(String action, Long targetId) {
        List<Map<String, Object>> rows = targetId == null
                ? jdbcTemplate.queryForList("SELECT * FROM audit_log WHERE action = ? AND target_type = 'schedule' "
                        + "AND target_id IS NULL ORDER BY id DESC LIMIT 1", action)
                : jdbcTemplate.queryForList("SELECT * FROM audit_log WHERE action = ? AND target_type = 'schedule' "
                        + "AND target_id = ? ORDER BY id DESC LIMIT 1", action, targetId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("schedule_total", count("SELECT COUNT(*) FROM schedule"));
        counts.put("schedule_deleted", count("SELECT COUNT(*) FROM schedule WHERE deleted = 1"));
        counts.put("appointment_total", count("SELECT COUNT(*) FROM appointment"));
        counts.put("appointment_deleted", count("SELECT COUNT(*) FROM appointment WHERE deleted = 1"));
        counts.put("doctor_total", count("SELECT COUNT(*) FROM doctor"));
        counts.put("department_total", count("SELECT COUNT(*) FROM department"));
        counts.put("title_total", count("SELECT COUNT(*) FROM title"));
        return counts;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private Map<?, ?> expectRoot(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    private Map<?, ?> expectData(MvcResult result) throws Exception {
        Map<?, ?> data = (Map<?, ?>) expectRoot(result).get("data");
        assertNotNull(data, "响应里必须有 data");
        return data;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> at(List<?> rows, int index) {
        return ((List<Map<?, ?>>) rows).get(index);
    }
}
