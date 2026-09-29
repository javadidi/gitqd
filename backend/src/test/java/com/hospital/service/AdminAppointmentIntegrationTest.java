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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端预约管理（T25）：J55 预约列表 → 数据正确、J56 排班管理 → CRUD 通（批量/停诊/调班）。
 *
 * <h2>本卡替前两张卡兑现的三件事</h2>
 * <ul>
 *   <li>T11 留的 TODO「退号能力落地后，把排班取消改成同事务自动退号」由 T13 明确移交本卡，
 *       于是有了 {@code suspend}：<b>取消</b>仍拒绝有预约的班（2007 原样保留），
 *       <b>停诊</b>负责处理已经有人订的班；</li>
 *   <li>T17/T22 都写着「体检报告的生产者是 T25」——本卡开出
 *       {@code POST /admin/physical-appointments/{id}/report}，并<b>反向验证患者侧立刻读得到</b>
 *       （{@link #recordedPhysicalReportIsImmediatelyReadableByThePatient} 是本卡唯一的跨卡闭环）；</li>
 *   <li>T22 体检须知里那句「本版本不产生报告」至此才真正被后续卡兑现。</li>
 * </ul>
 *
 * <h2>探针数据与清理</h2>
 * 排班统一落在 +4 年的日期（T11 同一条做法，好认好清），预约单号统一 {@code T25A} 前缀裸插——
 * 走完整挂号链路要拉进支付通道，而本卡测的是后台读与停诊级联，
 * 预约行只要"长得像真的"就够（T15 的探针同理）。
 * 清理按标记逐张删，且<b>只删自己造的</b>：第一版我写了 {@code DELETE ... WHERE order_no LIKE 'TK%'}，
 * 那会把别的卡遗留的退款单一并扫掉，快照立刻对不上——教训是"清理语句的谓词必须只覆盖本卡探针"。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class AdminAppointmentIntegrationTest {

    private static final int PROBE_YEAR_OFFSET = 4;
    private static final String ORDER_PREFIX = "T25A";
    private static final String DOCTOR_NAME = "T25 探针医生";
    private static final String PACKAGE_NAME = "后台探针套餐";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    private long auditIdFloor;
    private String adminToken;
    private String doctorToken;
    private String nurseToken;
    private String patientToken;
    private final List<Long> userIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    @BeforeEach
    void setUp() throws Exception {
        countsBefore = snapshotCounts();
        Long maxAuditId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM audit_log", Long.class);
        auditIdFloor = maxAuditId == null ? 0L : maxAuditId;
        adminToken = staffToken(1L, "admin");
        doctorToken = staffToken(3L, "doctor");
        nurseToken = staffToken(4L, "nurse");
        patientToken = newPatientToken("j55");
    }

    @AfterEach
    void cleanupAndAssertSeedUntouched() {
        String patients = "SELECT id FROM patient WHERE user_id IN (" + placeholders() + ")";
        String appointments = "SELECT id FROM appointment WHERE order_no LIKE '" + ORDER_PREFIX + "%'";
        jdbcTemplate.update("DELETE FROM report WHERE patient_id IN (" + patients + ")", userIdArgs());
        jdbcTemplate.update("DELETE FROM refund_record WHERE related_type = 'APPOINTMENT' "
                + "AND related_id IN (" + appointments + ")");
        jdbcTemplate.update("DELETE FROM nucleic_appointment WHERE patient_id IN (" + patients + ")",
                userIdArgs());
        jdbcTemplate.update("DELETE FROM physical_appointment WHERE patient_id IN (" + patients + ")",
                userIdArgs());
        jdbcTemplate.update("DELETE FROM appointment WHERE order_no LIKE '" + ORDER_PREFIX + "%'");
        jdbcTemplate.update("DELETE FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL ? YEAR)",
                PROBE_YEAR_OFFSET);
        jdbcTemplate.update("DELETE FROM physical_package WHERE name = ?", PACKAGE_NAME);
        jdbcTemplate.update("DELETE FROM doctor WHERE name = ?", DOCTOR_NAME);
        jdbcTemplate.update("DELETE FROM patient WHERE user_id IN (" + placeholders() + ")", userIdArgs());
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        // 审计按"进入本类时的水位线"清，不按 action 名删。
        // 第一版写的是 DELETE ... WHERE action = 'SUSPEND_SCHEDULE' 之类，结果漏了
        // 患者端退号产生的 CANCEL_APPOINTMENT——它留在库里，被 T12/T21 自己的全局审计清理
        // 顺手扫掉，于是那两张卡的"前后计数相等"断言炸在我留下的行上。
        // 教训：清理语句要按"我这段时间产生的"划界，而不是按"我以为我会产生的"列清单。
        jdbcTemplate.update("DELETE FROM audit_log WHERE id > ?", auditIdFloor);
        assertEquals(countsBefore, snapshotCounts(),
                "探针行必须清干净：seed 的 13 条预约与 150 条排班一张都不能少，"
                        + "别的卡遗留的行一张也不许被我扫掉");
    }

    // ============================================================
    // J55 预约列表 → 数据正确
    // ============================================================

    @Test
    void j55_adminListShowsSeedAppointmentsWithResolvedNames() throws Exception {
        List<Map<?, ?>> rows = expectList(admin(get("/admin/appointments")));

        assertTrue(rows.size() >= 13, "seed 有 13 条预约，后台列表至少该看到这些，实际 " + rows.size());
        Map<?, ?> first = rows.get(0);
        for (String key : List.of("id", "orderNo", "status", "patientName", "doctorName",
                "departmentName", "createdAt", "feeFen")) {
            assertTrue(first.containsKey(key), "列表行缺字段 " + key + "：" + first.keySet());
        }
        assertNotNull(first.get("patientName"), "就诊人姓名必须解析出来（appointment 表只有 patient_id）");
        assertNotNull(first.get("departmentName"), "科室要经 doctor.department_id 两跳解析");
        assertTrue(String.valueOf(first.get("orderNo")).startsWith("SEED-AP-"),
                "seed 那 13 条用的是 SEED-AP- 前缀（seed.sql:148 起的编号约定），实际：" + first.get("orderNo"));
    }

    @Test
    void j55_listOmitsRefundFieldsButDetailCarriesThem() throws Exception {
        Fixture fixture = seedFixture("已付待退");
        mockMvc.perform(post("/user/appointments/" + fixture.appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "医生停诊前患者自退"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        List<Map<?, ?>> rows = expectList(admin(get("/admin/appointments?status=CANCELLED")));
        Map<?, ?> mine = rows.stream()
                .filter(row -> fixture.appointmentId == ((Number) row.get("id")).longValue())
                .findFirst().orElseThrow();
        assertFalse(mine.containsKey("refundNo"),
                "列表不逐条反查退款单（那是 N+1），NON_NULL 让这三键整个消失");

        Map<?, ?> detail = expectData(admin(get("/admin/appointments/" + fixture.appointmentId)));
        assertEquals("CANCELLED", detail.get("status"));
        assertEquals("PENDING", detail.get("refundStatus"),
                "详情必须让管理员看见退款还在审核中，而不是以为已经退完");
        assertTrue(String.valueOf(detail.get("refundNo")).startsWith("TK"));
    }

    @Test
    void j55_filtersByDoctorDepartmentAndStatus() throws Exception {
        Fixture fixture = seedFixture("筛选甲");

        assertEquals(1, expectList(admin(get("/admin/appointments?doctorId=" + fixture.doctorId))).size(),
                "按医生筛只该出这一条");
        Long departmentId = jdbcTemplate.queryForObject(
                "SELECT department_id FROM doctor WHERE id = ?", Long.class, fixture.doctorId);
        List<Map<?, ?>> byDepartment = expectList(admin(get("/admin/appointments?departmentId=" + departmentId)));
        assertTrue(byDepartment.size() >= 1
                        && byDepartment.stream().anyMatch(row -> fixture.appointmentId == ((Number) row.get("id")).longValue()),
                "科室筛选要经 doctor.department_id 换算成医生集合；seed 的 1 号科室本来也有预约，所以这里断言包含而不是恰好一条");
        assertEquals(1, expectList(admin(get("/admin/appointments?doctorId=" + fixture.doctorId
                + "&departmentId=" + departmentId))).size(), "两个筛选取交集");
        assertEquals(0, expectList(admin(get("/admin/appointments?status=NOPE"))).size(),
                "状态是等值匹配，不认识的码值不该退回全量");
    }

    @Test
    void filterOptionsEndpointIsNotEatenByTheIdPath() throws Exception {
        Map<?, ?> root = expectRoot(mockMvc.perform(admin(get("/admin/appointments/filters")))
                .andExpect(status().isOk()));
        assertEquals(200, ((Number) root.get("code")).intValue(),
                "/admin/appointments/filters 必须命中字面量那把映射。真被 /{id} 吃掉，"
                        + "\"filters\" 转 Long 会抛类型不匹配、兜到 catch-all 变 500，这条就是那把锁");

        Map<?, ?> data = (Map<?, ?>) root.get("data");
        List<?> departments = (List<?>) data.get("departments");
        List<?> doctors = (List<?>) data.get("doctors");
        assertEquals(count("SELECT COUNT(*) FROM department WHERE deleted = 0"), departments.size(),
                "科室下拉 = 未软删的全部科室，服务端分页留给 T28");
        assertEquals(count("SELECT COUNT(*) FROM doctor WHERE deleted = 0"), doctors.size(),
                "医生下拉同理：软删的医生不能出现在后台筛选里");

        Map<?, ?> firstDoctor = (Map<?, ?>) doctors.get(0);
        assertTrue(firstDoctor.containsKey("departmentId"),
                "下拉项带 departmentId，前端选科室时能把该科室的医生排前面，实际键：" + firstDoctor.keySet());
        assertFalse(firstDoctor.containsKey("intro"),
                "筛选项不是医生管理（那是 T27）：只回 id、名字和所属科室，简介/擅长一律不给");
    }

    @Test
    void j55_dateFilterMeansVisitDateNotBookingTime() throws Exception {
        Fixture fixture = seedFixture("日期口径");
        LocalDate visitDate = probeDate(0);

        List<Map<?, ?>> hit = expectList(admin(get(
                "/admin/appointments?dateFrom=" + visitDate + "&dateTo=" + visitDate)));
        assertEquals(1, hit.size(),
                "排班日期在区间内就该命中——筛的是 schedule.date（V1:104）");
        assertEquals(visitDate.toString(), String.valueOf(hit.get(0).get("appointmentDate")),
                "回给前端的 appointmentDate 也必须是排班那天");

        LocalDate far = probeDate(60);
        assertEquals(0, expectList(admin(get(
                "/admin/appointments?dateFrom=" + far + "&dateTo=" + far))).size(),
                "同一条预约的下单时间就是今天：若按 appointment_time 筛，这一步会误命中，"
                        + "而管理员要的是「哪天来看诊」");
    }

    @Test
    void j55_unknownAppointmentUsesTheSameCodeAsPatientSide() throws Exception {
        Map<?, ?> root = expectRoot(admin(get("/admin/appointments/99999999")));
        assertEquals(2004, ((Number) root.get("code")).intValue(),
                "后台也不分辨「没有这条」与「读不到」，与 T13 同码");
    }

    @Test
    void j55_patientAndAnonymousCannotReachAdminReads() throws Exception {
        mockMvc.perform(get("/admin/appointments").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/appointments")).andExpect(status().isUnauthorized());
    }

    // ============================================================
    // 核酸与体检预约
    // ============================================================

    @Test
    void nucleicAdminReadsSeeWhatThePatientCreatedButNeverAReport() throws Exception {
        assertEquals(0, expectList(admin(get("/admin/nucleic-appointments"))).size(),
                "seed 没有核酸预约");

        long patientId = createPatient("核酸后台");
        Map<?, ?> created = expectData(mockMvc.perform(post("/user/nucleic-appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId,
                                "appointmentDate", probeDate(1).toString()))))
                .andExpect(status().isOk())
                .andReturn());
        long id = ((Number) created.get("appointmentId")).longValue();

        List<Map<?, ?>> rows = expectList(admin(get("/admin/nucleic-appointments")));
        assertEquals(1, rows.size());
        assertEquals("核酸后台", rows.get(0).get("patientName"), "后台要认得出是谁约的");
        assertFalse(rows.get(0).containsKey("report"), "列表不回报告列");

        Map<?, ?> detail = expectData(admin(get("/admin/nucleic-appointments/" + id)));
        assertFalse(detail.containsKey("report"),
                "详情也不回内容：T21 的红线仍然成立——产品代码从不写这一列，后台能读到的也只能是空");
    }

    @Test
    void physicalAdminReadsAndReportRoundTrip() throws Exception {
        long patientId = createPatient("体检后台");
        long packageId = seedPackage();
        Map<?, ?> created = expectData(mockMvc.perform(post("/user/physical-appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "packageId", packageId,
                                "appointmentDate", probeDate(2).toString()))))
                .andExpect(status().isOk())
                .andReturn());
        long aptId = ((Number) created.get("appointmentId")).longValue();

        List<Map<?, ?>> rows = expectList(admin(get("/admin/physical-appointments")));
        assertEquals(1, rows.size());
        assertEquals(PACKAGE_NAME, rows.get(0).get("packageName"));
        assertEquals(28800L, ((Number) rows.get(0).get("priceFen")).longValue(),
                "价格读时从套餐带（预约表没有价格列，T22 同一条）");

        Map<?, ?> empty = expectData(admin(get("/admin/physical-appointments/" + aptId + "/report")));
        assertEquals(aptId, ((Number) empty.get("appointmentId")).longValue());
        assertFalse(empty.containsKey("result"), "还没录入：只有 appointmentId，前端据此显示录入表单");

        Map<?, ?> written = expectData(mockMvc.perform(
                        post("/admin/physical-appointments/" + aptId + "/report")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("result", "各项指标在正常范围内。"))))
                .andExpect(status().isOk())
                .andReturn());
        String reportNo = String.valueOf(written.get("reportNo"));
        assertTrue(reportNo.matches("^YJ\\d{8}-\\d{4}$"), "报告编号复用 SerialType.YJ，实际：" + reportNo);
        assertEquals("各项指标在正常范围内。", written.get("result"));
        assertNotNull(written.get("reportTime"));
        assertFalse(written.containsKey("items"), "录入不收 items：V1:257 那列没有键名约定");

        Map<?, ?> again = expectRoot(mockMvc.perform(
                post("/admin/physical-appointments/" + aptId + "/report")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("result", "第二份")))));
        assertEquals(5002, ((Number) again.get("code")).intValue(),
                "同一个体检人不堆两份互相矛盾的报告");

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, operator_type FROM audit_log WHERE action = 'CREATE_PHYSICAL_REPORT' "
                        + "ORDER BY id DESC LIMIT 1");
        assertEquals("ADMIN", audit.get("operator_type"),
                "录入人是后台角色；OperatorType 按角色分列（T12 把它做成多态列），admin 账号就是 ADMIN");
    }

    @Test
    void recordedPhysicalReportIsImmediatelyReadableByThePatient() throws Exception {
        // 跨卡闭环：T17 建读侧、T22 放开 PHYSICAL 白名单，两边都写「生产者在 T25」。
        long patientId = createPatient("跨卡闭环");
        long packageId = seedPackage();
        Map<?, ?> created = expectData(mockMvc.perform(post("/user/physical-appointments")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "packageId", packageId,
                                "appointmentDate", probeDate(3).toString()))))
                .andExpect(status().isOk())
                .andReturn());
        long aptId = ((Number) created.get("appointmentId")).longValue();

        mockMvc.perform(post("/admin/physical-appointments/" + aptId + "/report")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("result", "闭环验证：这份报告患者现在就该看见。"))))
                .andExpect(status().isOk());

        List<Map<?, ?>> patientList = expectList(mockMvc.perform(
                get("/user/reports?type=PHYSICAL").header("Authorization", "Bearer " + patientToken)));
        assertEquals(1, patientList.size(), "T17 的列表立刻读到——不需要改任何读端点");
        long reportId = ((Number) patientList.get(0).get("reportId")).longValue();

        Map<?, ?> detail = expectData(mockMvc.perform(get("/user/reports/" + reportId)
                .header("Authorization", "Bearer " + patientToken)));
        assertEquals("闭环验证：这份报告患者现在就该看见。", detail.get("result"));
    }

    @Test
    void reportWriteRejectsUnknownAppointmentAndBlankResult() throws Exception {
        Map<?, ?> root = expectRoot(mockMvc.perform(
                post("/admin/physical-appointments/99999999/report")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("result", "没有这个班")))));
        assertEquals(5001, ((Number) root.get("code")).intValue());

        mockMvc.perform(post("/admin/physical-appointments/1/report")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("result", "  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================================================
    // J56 排班管理：批量 / 停诊 / 调班
    // ============================================================

    @Test
    void j56_batchSchedulingCreatesEveryCombinationAndIsRepeatable() throws Exception {
        long doctorId = seedDoctor();
        LocalDate from = probeDate(0);
        LocalDate to = probeDate(2);
        Map<?, ?> body = Map.of("doctorId", doctorId, "dateFrom", from.toString(), "dateTo", to.toString(),
                "timeSlots", List.of("MORNING", "AFTERNOON"), "totalSlots", 20);

        Map<?, ?> first = expectData(admin(post("/admin/schedules/batch").contentType(MediaType.APPLICATION_JSON)
                .content(json(body))));
        assertEquals(6, ((Number) first.get("createdCount")).intValue(), "3 天 × 2 段 = 6 条");
        assertEquals(0, ((Number) first.get("skippedCount")).intValue());

        Map<?, ?> second = expectData(admin(post("/admin/schedules/batch").contentType(MediaType.APPLICATION_JSON)
                .content(json(body))));
        assertEquals(0, ((Number) second.get("createdCount")).intValue(),
                "再排一遍不该重复建，也不该整批报错");
        List<?> skipped = (List<?>) second.get("skipped");
        assertEquals(6, skipped.size());
        assertTrue(String.valueOf(skipped.get(0)).contains("/MORNING"),
                "跳过项要写清是哪天哪段，实际：" + skipped.get(0));
    }

    @Test
    void j56_batchRejectsReversedRangeBadSlotAndEmptySlots() throws Exception {
        long doctorId = seedDoctor();

        mockMvc.perform(admin(post("/admin/schedules/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", doctorId, "dateFrom", probeDate(0).toString(),
                                "dateTo", probeDate(-1).toString(),
                                "timeSlots", List.of("MORNING"), "totalSlots", 20)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(admin(post("/admin/schedules/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", doctorId, "dateFrom", probeDate(0).toString(),
                                "dateTo", probeDate(1).toString(),
                                "timeSlots", List.of("NIGHT"), "totalSlots", 20)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(admin(post("/admin/schedules/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("doctorId", doctorId, "dateFrom", probeDate(0).toString(),
                                "dateTo", probeDate(1).toString(),
                                "timeSlots", List.of(), "totalSlots", 20)))))
                .andExpect(status().isBadRequest());

        assertEquals(0, count("SELECT COUNT(*) FROM schedule WHERE doctor_id = ?", doctorId),
                "三次被拒都不留行（日期倒置与时段非法走 400 业务码，空时段走 @NotEmpty 的 400 HTTP）");
    }

    @Test
    void j56_suspendCancelsBookedAppointmentsAndIssuesRefundTickets() throws Exception {
        Fixture fixture = seedBookedSchedule("停诊甲", "停诊乙", "CONFIRMED", "PENDING_PAYMENT");

        Map<?, ?> result = expectData(admin(post("/admin/schedules/" + fixture.scheduleId + "/suspend")
                .param("reason", "医生出差")));
        assertEquals(2, ((Number) result.get("appointmentCount")).intValue());
        assertEquals(1, ((Number) result.get("refundCount")).intValue(),
                "两条里只有已支付那条挂退款单；待支付那条没收过钱，无款可退");
        assertEquals(5000L, ((Number) result.get("refundFen")).longValue(), "金额取账上的 fee_fen");

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM appointment WHERE schedule_id = ?", String.class, fixture.scheduleId);
        assertEquals(2, statuses.size());
        assertTrue(statuses.stream().allMatch("CANCELLED"::equals),
                "两条预约都该变成已取消，实际：" + statuses);
        assertEquals(1, count("SELECT COUNT(*) FROM refund_record WHERE related_type = 'APPOINTMENT' "
                + "AND related_id IN (SELECT id FROM appointment WHERE schedule_id = ?)",
                fixture.scheduleId));
        assertEquals(0, count("SELECT COUNT(*) FROM schedule WHERE id = ? AND deleted = 0",
                fixture.scheduleId), "班本身已软删");
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'SUSPEND_SCHEDULE' "
                + "AND reason = '医生出差'"), "停诊原因进审计（schedule 表没有原因列）");
    }

    @Test
    void j56_suspendDoesNotPutSlotsBackBecauseTheScheduleIsGone() throws Exception {
        Fixture fixture = seedBookedSchedule("不还号甲", "不还号乙", "CONFIRMED", "CONFIRMED");

        expectData(admin(post("/admin/schedules/" + fixture.scheduleId + "/suspend")));

        assertEquals(20, count("SELECT remaining_slots FROM schedule WHERE id = ?",
                fixture.scheduleId),
                "本卡不做逐条 releaseSlot：remaining 回到 20 是 T11 的 cancelById 顺带做的"
                        + "（把剩余抬回总数），而行已软删、任何列表都看不见它。真正不能做的是像患者退号那样"
                        + "逐条 +1——那会把号源账改成一个还在营业的班的样子。");
    }

    /**
     * 浏览器那一轮抓出来的缺陷回归钩子。
     *
     * <p><b>症状</b>：停诊之后，后台挂号详情的「就诊时间」两格变成「— —」，
     * 按就诊日期筛选也把被停那天的预约整批漏掉。
     *
     * <p><b>根因</b>：就诊日期与时段只存在于 schedule 表，而 {@code Schedule} 继承
     * {@code BaseEntity} 的 {@code deleted} 上有 {@code @TableLogic}——
     * MyBatis-Plus 给 {@code selectBatchIds} 自动追加 {@code deleted = 0}，
     * 于是停诊软删的那个班查不出来，日期和时段就一起空了。
     *
     * <p><b>为什么这状态是 T25 才造出来的</b>：T11 的取消有 2007 守卫（班下还有未取消预约就撤不掉），
     * 所以在 T11 的世界里"已软删的班"名下永远没有预约，读不读得到无所谓。
     * 停诊是本项目第一次<b>主动</b>把预约留在一个已撤的班下面——而预约行是历史事实，
     * 患者和后台都还要继续看见它是哪天哪个时段。
     */
    @Test
    void j56_suspendedScheduleStillRendersVisitDateAndSlotOnBothSides() throws Exception {
        Fixture fixture = seedBookedSchedule("停诊显示甲", "停诊显示乙", "CONFIRMED", "PENDING_PAYMENT");
        LocalDate day = probeDate(6);

        expectData(admin(post("/admin/schedules/" + fixture.scheduleId + "/suspend")));
        assertEquals(0, count("SELECT COUNT(*) FROM schedule WHERE id = ? AND deleted = 0",
                fixture.scheduleId), "先确认班真的软删了——这是下面三段断言的前提");

        List<Map<?, ?>> byDate = expectList(admin(get(
                "/admin/appointments?dateFrom=" + day + "&dateTo=" + day)));
        List<Map<?, ?>> mine = byDate.stream()
                .filter(row -> DOCTOR_NAME.equals(row.get("doctorName"))).toList();
        assertEquals(2, mine.size(),
                "被停那天的两条预约必须还能按就诊日期筛出来——筛不到就等于把它们从历史里抹掉了");
        for (Map<?, ?> row : mine) {
            assertEquals(day.toString(), String.valueOf(row.get("appointmentDate")),
                    "列表的就诊日期不能因为班软删就空掉");
            assertEquals("AFTERNOON", row.get("timeSlot"), "时段同理");
        }

        Map<?, ?> detail = expectData(admin(get("/admin/appointments/" + mine.get(0).get("id"))));
        assertEquals(day.toString(), String.valueOf(detail.get("appointmentDate")),
                "详情页那两格不能是「— —」（浏览器验收抓到的就是这个）");
        assertEquals("AFTERNOON", detail.get("timeSlot"));

        List<Map<?, ?>> patientRows = expectList(mockMvc.perform(get("/user/appointments")
                        .header("Authorization", "Bearer " + patientToken)))
                .stream().filter(row -> String.valueOf(row.get("orderNo")).startsWith(ORDER_PREFIX)).toList();
        assertEquals(2, patientRows.size(), "患者自己也还能看见这两条退掉的记录");
        for (Map<?, ?> row : patientRows) {
            assertEquals("AFTERNOON", row.get("timeSlot"),
                    "患者侧列表的时段同样是读 schedule 得来的，同一个坑");
        }
    }

    @Test
    void j56_suspendOnEmptyScheduleWorksWhilePlainCancelStillRefuses() throws Exception {
        long emptySchedule = seedSchedule();
        Map<?, ?> result = expectData(admin(post("/admin/schedules/" + emptySchedule + "/suspend")));
        assertEquals(0, ((Number) result.get("appointmentCount")).intValue());
        assertEquals(0, ((Number) result.get("refundCount")).intValue(), "空班停诊不挂任何退款单");

        Fixture booked = seedBookedSchedule("守卫甲", "守卫乙", "CONFIRMED", "CONFIRMED");
        Map<?, ?> root = expectRoot(admin(delete("/admin/schedules/" + booked.scheduleId)
                .param("reason", "试取消")));
        assertEquals(2007, ((Number) root.get("code")).intValue(),
                "T11 的取消守卫原样保留：它管的是还没人订的班，有人订就该走停诊。"
                        + "两条路径的分工就是本卡给这两个动词的答案。");
    }

    @Test
    void j56_rescheduleMovesAnEmptySlotButRefusesABookedOne() throws Exception {
        long scheduleId = seedSchedule();
        LocalDate target = probeDate(9);

        Map<?, ?> moved = expectData(admin(post("/admin/schedules/" + scheduleId + "/reschedule")
                .param("reason", "换到周五")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("date", target.toString(), "timeSlot", "EVENING")))));
        assertEquals(target.toString(), String.valueOf(moved.get("date")));
        assertEquals("EVENING", moved.get("timeSlot"));

        Fixture booked = seedBookedSchedule("调班甲", "调班乙", "CONFIRMED", "CONFIRMED");
        Map<?, ?> root = expectRoot(admin(post("/admin/schedules/" + booked.scheduleId + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("date", probeDate(10).toString(), "timeSlot", "MORNING")))));
        assertEquals(2007, ((Number) root.get("code")).intValue());
        assertTrue(String.valueOf(root.get("message")).contains("停诊"),
                "文案必须给出下一步（先停诊），实际：" + root.get("message"));
    }

    @Test
    void j56_rescheduleIntoAnExistingSlotIsAConflict() throws Exception {
        long doctorId = seedDoctor();
        long first = seedScheduleOn(doctorId, probeDate(4), "MORNING");
        seedScheduleOn(doctorId, probeDate(5), "AFTERNOON");

        Map<?, ?> root = expectRoot(admin(post("/admin/schedules/" + first + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("date", probeDate(5).toString(), "timeSlot", "AFTERNOON")))));
        assertEquals(2002, ((Number) root.get("code")).intValue(),
                "R2 唯一索引在调班这条路上同样生效（V1:111 不看状态列）");
    }

    @Test
    void scheduleWritesStayBehindManageDoctorCap() throws Exception {
        long scheduleId = seedSchedule();

        for (String token : List.of(doctorToken, nurseToken)) {
            mockMvc.perform(post("/admin/schedules/" + scheduleId + "/suspend")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(4001));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM schedule WHERE id = ? AND deleted = 0",
                scheduleId), "被权限挡住的停诊一行都不该改（RequireCap 切面在事务之前）");
    }

    @Test
    void t25EndpointsAreExactlyWhatTheCardNamed() {
        TreeSet<String> found = new TreeSet<>();
        TreeSet<String> forbidden = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                if (pattern.startsWith("/admin/appointments")
                        || pattern.startsWith("/admin/nucleic-appointments")
                        || pattern.startsWith("/admin/physical-appointments")
                        || pattern.startsWith("/admin/schedules")) {
                    found.add(verb + " " + pattern);
                }
                if (pattern.startsWith("/admin/appointments/") && pattern.endsWith("/cancel")) {
                    forbidden.add(verb + " " + pattern);
                }
            }
        });

        assertEquals("[DELETE /admin/schedules/{id}, GET /admin/appointments, GET /admin/appointments/filters, "
                        + "GET /admin/appointments/{id}, "
                        + "GET /admin/nucleic-appointments, GET /admin/nucleic-appointments/{id}, "
                        + "GET /admin/physical-appointments, GET /admin/physical-appointments/{id}, "
                        + "GET /admin/physical-appointments/{id}/report, GET /admin/schedules, "
                        + "POST /admin/physical-appointments/{id}/report, POST /admin/schedules, "
                        + "POST /admin/schedules/batch, POST /admin/schedules/{id}/reschedule, "
                        + "POST /admin/schedules/{id}/suspend, PUT /admin/schedules/{id}]",
                found.toString(), "预约 3（列表 / 筛选项 / 详情）+ 核酸 2 + 体检 4 + 排班 7（T11 的四把 + 本卡三把）");
        assertEquals("[]", forbidden.toString(),
                "卡片 697-698 行只要「列表」与「详情」，没给后台单点退号的能力；"
                        + "后台侧唯一会退号的路径是停诊");
    }

    // ============================================================
    // 夹具
    // ============================================================

    /** 一条探针链：医生 → 排班 → 就诊人 → 预约。id 全部留着给断言用，清理由 @AfterEach 按标记扫。 */
    private static final class Fixture {
        private long doctorId;
        private long scheduleId;
        private long appointmentId;
    }

    private LocalDate probeDate(int plusDays) {
        return LocalDate.now().plusYears(PROBE_YEAR_OFFSET).plusDays(plusDays);
    }

    private long seedDoctor() {
        jdbcTemplate.update("INSERT INTO doctor (name, department_id, title_id, intro, specialty, "
                + "created_at, updated_at, deleted) VALUES (?, 1, 1, '探针简介', '探针方向', "
                + "NOW(3), NOW(3), 0)", DOCTOR_NAME);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM doctor", Long.class);
        return id == null ? 0L : id;
    }

    private long seedScheduleOn(long doctorId, LocalDate date, String slot) {
        jdbcTemplate.update("INSERT INTO schedule (doctor_id, `date`, time_slot, total_slots, "
                        + "remaining_slots, created_at, updated_at, deleted) VALUES (?, ?, ?, 20, 18, "
                        + "NOW(3), NOW(3), 0)", doctorId, date, slot);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM schedule", Long.class);
        return id == null ? 0L : id;
    }

    private long seedSchedule() {
        return seedScheduleOn(seedDoctor(), probeDate(1), "MORNING");
    }

    private long insertAppointment(String suffix, long patientId, long doctorId, long scheduleId,
                                   String status, long feeFen) {
        jdbcTemplate.update("INSERT INTO appointment (order_no, patient_id, doctor_id, schedule_id, "
                        + "status, appointment_time, fee_fen, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(3), ?, NOW(3), NOW(3), 0)",
                ORDER_PREFIX + suffix, patientId, doctorId, scheduleId, status, feeFen);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM appointment WHERE order_no = ?", Long.class, ORDER_PREFIX + suffix);
        return id == null ? 0L : id;
    }

    /** 医生 + 排班 + 就诊人 + 一条 CONFIRMED 预约（可直接退号，用于退款与列表断言）。 */
    private Fixture seedFixture(String name) throws Exception {
        Fixture fixture = new Fixture();
        fixture.doctorId = seedDoctor();
        fixture.scheduleId = seedScheduleOn(fixture.doctorId, probeDate(0), "MORNING");
        long patientId = createPatient(name);
        fixture.appointmentId = insertAppointment("-" + name, patientId, fixture.doctorId,
                fixture.scheduleId, "CONFIRMED", 5000L);
        return fixture;
    }

    /** 一个班 + 两条预约（状态各自指定），用于停诊与调班守卫。 */
    private Fixture seedBookedSchedule(String firstName, String secondName,
                                       String firstStatus, String secondStatus) throws Exception {
        Fixture fixture = new Fixture();
        fixture.doctorId = seedDoctor();
        fixture.scheduleId = seedScheduleOn(fixture.doctorId, probeDate(6), "AFTERNOON");
        insertAppointment("-" + firstName, createPatient(firstName), fixture.doctorId,
                fixture.scheduleId, firstStatus, 5000L);
        insertAppointment("-" + secondName, createPatient(secondName), fixture.doctorId,
                fixture.scheduleId, secondStatus, 5000L);
        return fixture;
    }

    private long seedPackage() {
        jdbcTemplate.update("INSERT INTO physical_package (name, type_id, price_fen, target_audience, "
                + "items, created_at, updated_at, deleted) VALUES (?, NULL, 28800, '探针', "
                + "'[\"身高\"]', NOW(3), NOW(3), 0)", PACKAGE_NAME);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM physical_package", Long.class);
        return id == null ? 0L : id;
    }

    private long createPatient(String name) throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "cardNo",
                                "T25" + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100000000L),
                                "idCard", "110101199003071234", "phone", "13900002345",
                                "relation", "SELF"))))
                .andExpect(status().isOk())
                .andReturn());
        return ((Number) data.get("id")).longValue();
    }

    // ============================================================
    // 请求与断言助手
    // ============================================================

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + adminToken);
    }

    /** 三个断言助手都接得住"还没发出去"的请求构造器，省掉每个调用点手写 perform().andReturn()。 */
    private Map<?, ?> expectRoot(MockHttpServletRequestBuilder builder) throws Exception {
        return expectRoot(mockMvc.perform(builder).andReturn());
    }

    private Map<?, ?> expectData(MockHttpServletRequestBuilder builder) throws Exception {
        return expectData(mockMvc.perform(builder).andReturn());
    }

    private List<Map<?, ?>> expectList(MockHttpServletRequestBuilder builder) throws Exception {
        return expectList(mockMvc.perform(builder).andReturn());
    }

    private Map<?, ?> expectRoot(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return expectRoot(actions.andReturn());
    }

    private Map<?, ?> expectData(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return expectData(actions.andReturn());
    }

    private List<Map<?, ?>> expectList(org.springframework.test.web.servlet.ResultActions actions)
            throws Exception {
        return expectList(actions.andReturn());
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String placeholders() {
        return userIds.stream().map(id -> "?").collect(Collectors.joining(","));
    }

    private Object[] userIdArgs() {
        return userIds.toArray();
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("appointment", count("SELECT COUNT(*) FROM appointment"));
        snapshot.put("schedule", count("SELECT COUNT(*) FROM schedule"));
        snapshot.put("doctor", count("SELECT COUNT(*) FROM doctor"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("physical_package", count("SELECT COUNT(*) FROM physical_package"));
        snapshot.put("physical_appointment", count("SELECT COUNT(*) FROM physical_appointment"));
        snapshot.put("nucleic_appointment", count("SELECT COUNT(*) FROM nucleic_appointment"));
        snapshot.put("report", count("SELECT COUNT(*) FROM report"));
        snapshot.put("refund_record", count("SELECT COUNT(*) FROM refund_record"));
        return snapshot;
    }

    private String staffToken(Long adminId, String role) {
        List<String> modules = List.of("dashboard", "schedule", "appointment", "report", "physical");
        return jwtUtil.generateToken(adminId, role, role, modules, List.of());
    }

    private String newPatientToken(String tag) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", tag + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        userIds.add(((Number) data.get("userId")).longValue());
        return String.valueOf(data.get("token"));
    }

    private String json(Map<?, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
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

    private List<Map<?, ?>> expectList(MvcResult result) throws Exception {
        Map<?, ?> root = expectRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> rows = (List<Map<?, ?>>) data;
        return rows;
    }
}
