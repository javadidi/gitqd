package com.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
import com.hospital.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T21 核酸检测（卡片 616–630 行）。J47 检测申请 → 记录创建 / J48 检测报告 → 内容正确。
 *
 * <h2>本卡只当一半的生产者，所以取证分两套</h2>
 * 预约行经真接口产生（J47 要证的就是这个），<b>报告内容却没有任何生产者</b>
 * ——产品代码刻意不写 {@code report}（理由四条，见 {@link com.hospital.dto.NucleicReportResponse}）。
 * 于是 J48「内容正确」只能像 T16/T17/T18 那样借一条<strong>人工取证探针</strong>
 * （裸插一行带 {@code report} 的记录，收尾删干净）来证明读路径没坏。
 * 两套断言合起来才是这一卡的完整证明：<strong>写的不越界，读的不失真。</strong>
 *
 * <h2>三条"有意不做"各钉一条断言</h2>
 * <ul>
 *   <li>不伪造报告 → {@link #j47_reportColumnStaysNullForEverythingTheProductWrites}；</li>
 *   <li>不显示地点（无列、无字典、无表）→ {@link #j47_locationCannotBeSubmittedOrReturned}；</li>
 *   <li>不做详情端点（报告页已含检测信息）→ {@link #nucleicEndpointsAreExactlyTheThreeTheSpecNamed}
 *       把注册表钉成三把，多一把就红。</li>
 * </ul>
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class NucleicAppointmentIntegrationTest {

    /** 人工取证探针的单号前缀，与产品代码生成的 HX 单号一眼分得开。 */
    private static final String PROBE_PREFIX = "T21P";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private final List<String> probeOrderNos = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    private String patientToken;
    private String doctorToken;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
        today = LocalDate.now();
        doctorToken = jwtUtil.generateToken(3L, "doctor", "doctor",
                List.of("dashboard", "schedule", "appointment", "report"), List.of());
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        // 先删探针行（按单号前缀），再按就诊人删本卡产生的预约行
        for (String orderNo : probeOrderNos) {
            jdbcTemplate.update("DELETE FROM nucleic_appointment WHERE order_no = ?", orderNo);
        }
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM nucleic_appointment WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        // 只删本卡自己产生的审计：CREATE_NUCLEIC_APPOINTMENT 这个 action 只有这里会写
        jdbcTemplate.update("DELETE FROM audit_log WHERE action = 'CREATE_NUCLEIC_APPOINTMENT'");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(),
                "T21 只动自己造的探针行与本卡就诊人的预约行：四项计数必须回到基线");
    }

    // ============================================================
    // J47 检测申请 → 记录创建（卡片 627 行）
    // ============================================================

    @Test
    void j47_createAppointmentWritesTheRow() throws Exception {
        long patientId = createPatient("检甲");

        Map<?, ?> created = expectData(postJson(Map.of(
                "patientId", patientId,
                "appointmentDate", today.toString())));

        assertNotNull(created.get("appointmentId"), "预约 id 必须回出来，报告页要它定位");
        assertTrue(String.valueOf(created.get("orderNo")).startsWith("HX"),
                "单号走 T02 就建好的 SerialType.HX（核酸单号），实际：" + created.get("orderNo"));
        assertEquals("PENDING", created.get("status"), "V1:301 的初始态，也是本卡唯一写入的值");
        assertEquals(today.toString(), created.get("appointmentDate"), "DATE 列原样回 ISO 日期");

        long storedId = ((Number) created.get("appointmentId")).longValue();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT patient_id, appointment_date, status, report FROM nucleic_appointment WHERE id = ?",
                storedId);
        assertEquals(patientId, ((Number) row.get("patient_id")).longValue(),
                "J47 的判据是「记录创建」，必须落到库");
        assertEquals("PENDING", row.get("status"));
        assertNull(row.get("report"), "产品代码写出来的行，报告列必须是 NULL");
    }

    @Test
    void j47_reportColumnStaysNullForEverythingTheProductWrites() throws Exception {
        long patientId = createPatient("检乙");
        for (int i = 0; i < 3; i++) {
            expectData(postJson(Map.of("patientId", patientId,
                    "appointmentDate", today.plusDays(i).toString())));
        }

        assertEquals(0, count("SELECT COUNT(*) FROM nucleic_appointment WHERE report IS NOT NULL"),
                "卡片 624 行红线「不做真实检测（二期做）」：首版全表不许有一条伪造的医学结论");
        assertEquals(0, count("SELECT COUNT(*) FROM nucleic_appointment WHERE status <> 'PENDING'"),
                "COMPLETED 的语义是「检测做完、报告出了」，本卡既不采样也不出报告");
    }

    @Test
    void j47_statusAndReportCannotBeDeclaredByTheClient() throws Exception {
        long patientId = createPatient("检丙");

        Map<?, ?> created = expectData(postJson(Map.of(
                "patientId", patientId,
                "appointmentDate", today.toString(),
                "status", "COMPLETED",
                "report", "阴性",
                "id", 999999L)));

        assertFalse(created.containsKey("report"), "响应里不许出现报告键");
        assertEquals("PENDING", created.get("status"));
        assertEquals(0, count("SELECT COUNT(*) FROM nucleic_appointment WHERE patient_id = ? "
                        + "AND (status <> 'PENDING' OR report IS NOT NULL)", patientId),
                "自己声明「我已做完检测、结果是阴性」必须结构上不可能");
    }

    @Test
    void j47_locationCannotBeSubmittedOrReturned() throws Exception {
        // 卡片 621 行「确认检测时间、地点等」点名了地点，但 V1:296-307 没有采样点列、
        // PRD 588 行数据字典也没有、全仓 28 张表没有采样点表 —— 所以不显示也不编。
        long patientId = createPatient("检丁");
        Map<?, ?> created = expectData(postJson(Map.of(
                "patientId", patientId,
                "appointmentDate", today.toString(),
                "location", "门诊楼前广场",
                "address", "示例路 1 号",
                "siteName", "临时采样点")));

        for (String forbidden : List.of("location", "address", "siteName", "site")) {
            assertFalse(created.containsKey(forbidden),
                    "地点无规格落点，响应不许出现 " + forbidden + "（编一个地址就是造一个不存在的采样点）");
        }
    }

    @Test
    void j47_missingOrPastDateIsRejectedByValidation() throws Exception {
        long patientId = createPatient("检戊");

        mockMvc.perform(createRequest(Map.of("appointmentDate", today.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(createRequest(Map.of("patientId", patientId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        // 过去的日期当场拦：一个已经过去的检测日永远不可能被采样，入库就是留死数据
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "appointmentDate", today.minusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        // 今天必须能过：@FutureOrPresent 的下限就是今天，不是明天
        expectData(postJson(Map.of("patientId", patientId, "appointmentDate", today.toString())));
        // 上限刻意不设：规格从没给过"最多约几天内"
        expectData(postJson(Map.of("patientId", patientId,
                "appointmentDate", today.plusYears(3).toString())));

        assertEquals(2, count("SELECT COUNT(*) FROM nucleic_appointment WHERE patient_id = ?", patientId),
                "三次被拒零落库，两次成功各一行");
    }

    @Test
    void j47_foreignOrSoftDeletedPatientIs5001AndCreatesNothing() throws Exception {
        createPatient("检己");
        long foreign = createPatientFor("检庚", otherToken());

        mockMvc.perform(createRequest(Map.of("patientId", foreign,
                        "appointmentDate", today.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(createRequest(Map.of("patientId", 999999999L,
                        "appointmentDate", today.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        long patientId = createPatient("检辛");
        mockMvc.perform(delete("/user/patients/" + patientId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "appointmentDate", today.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        assertEquals(0, count("SELECT COUNT(*) FROM nucleic_appointment"),
                "别人的就诊人、没这个就诊人、软删的就诊人，三种都不许落行");
    }

    @Test
    void j47_eachApplyGetsItsOwnOrderNo() throws Exception {
        long patientId = createPatient("检壬");
        String first = String.valueOf(expectData(postJson(Map.of(
                "patientId", patientId, "appointmentDate", today.toString()))).get("orderNo"));
        String second = String.valueOf(expectData(postJson(Map.of(
                "patientId", patientId, "appointmentDate", today.toString()))).get("orderNo"));

        assertFalse(first.equals(second), "两次申请必须是两个单号，实际：" + first + " / " + second);
        assertEquals(2, count("SELECT COUNT(DISTINCT order_no) FROM nucleic_appointment "
                        + "WHERE patient_id = ?", patientId),
                "两条各带一个不同单号——唯一性由 SerialNumberService 的 Redis 序列保证"
                        + "（与 T12/T14/T15/T19 同一把钥匙）");
    }

    // ============================================================
    // 列表（PRD 304/527 行撑出来的第三个端点）
    // ============================================================

    @Test
    void listShowsMyOwnRowsNewestFirstAndNothingOfOthers() throws Exception {
        // otherToken() 每次调用都会新登录一个用户，所以"别人"必须只取一次、两处共用，
        // 否则探针行的主人和查列表的人不是同一个，断言会假绿或假红。
        String other = otherToken();
        long mine = createPatient("检癸");
        long alsoMine = createPatient("检子");
        long foreign = createPatientFor("检丑", other);
        expectData(postJson(Map.of("patientId", mine, "appointmentDate", today.toString())));
        expectData(postJson(Map.of("patientId", alsoMine, "appointmentDate", today.plusDays(1).toString())));
        insertProbe(foreign, "阴性（人工取证探针）");

        List<?> rows = expectDataList(getJson("/user/nucleic-appointments"));
        assertEquals(2, rows.size(), "一个就诊人名下的也要合并进来（与 T18 两个就诊人同一条断言）");

        Map<?, ?> newest = asMap(rows.get(0));
        Map<?, ?> oldest = asMap(rows.get(1));
        assertTrue(((Number) newest.get("appointmentId")).longValue()
                        > ((Number) oldest.get("appointmentId")).longValue(),
                "按创建时间倒序：最新一次预约在最前");
        assertEquals(List.of("appointmentDate", "appointmentId", "orderNo", "patientName", "status"),
                new ArrayList<>(new TreeSet<>(newest.keySet())),
                "列表五项：报告留给报告页（T15/T17/T18 同一条纪律）");
        assertFalse(newest.containsKey("report"), "列表不携带报告正文");

        List<?> foreignRows = expectDataList(getWith(other, "/user/nucleic-appointments"));
        assertEquals(1, foreignRows.size(), "别人的预约不在我的列表里，探针行只归它自己的就诊人");
    }

    @Test
    void listIsEmptyWhenNoPatientAtAll() throws Exception {
        ownerToken();
        assertEquals(0, expectDataList(getJson("/user/nucleic-appointments")).size(),
                "一个就诊人也没有 → 空列表，而不是把 IN () 空集合交给 MyBatis 去炸");
    }

    // ============================================================
    // J48 检测报告 → 内容正确（卡片 628 行）
    // ============================================================

    @Test
    void j48_reportReturnsWhatIsStoredVerbatim() throws Exception {
        long patientId = createPatient("检寅");
        // 人工取证探针：报告内容的生产者首版不存在（T25 后台只做列表/详情，全仓无人写 report），
        // 所以要证"读路径不失真"，只能自己插一行有内容的。与 T16/T17/T18 同一口径。
        long probeId = insertProbe(patientId, "阴性，检测时间为 " + today + "，采样点为院内流动采样车。");

        Map<?, ?> report = expectData(getJson("/user/nucleic-appointments/" + probeId + "/report"));

        assertEquals(probeId, ((Number) report.get("appointmentId")).longValue());
        assertEquals("检寅", report.get("patientName"));
        assertTrue(String.valueOf(report.get("orderNo")).startsWith(PROBE_PREFIX));
        assertEquals("COMPLETED", report.get("status"), "状态原样回库里的值，不做映射");
        assertEquals(List.of("appointmentDate", "appointmentId", "orderNo", "patientName", "report", "status"),
                new ArrayList<>(new TreeSet<>(report.keySet())),
                "PRD 588 行五项 + 单号，多一个键就是破口");
        assertFalse(report.containsKey("patientId"), "内部主键不外放");
        assertFalse(report.containsKey("location"), "报告页也不凭空长出地点");
    }

    @Test
    void j48_reportKeyIsAbsentUntilSomeoneIssuesTheReport() throws Exception {
        long patientId = createPatient("检卯");
        long appointmentId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId, "appointmentDate", today.toString()))).get("appointmentId"))
                .longValue();

        Map<?, ?> report = expectData(getJson("/user/nucleic-appointments/" + appointmentId + "/report"));

        assertFalse(report.containsKey("report"),
                "NON_NULL 会让没出的报告整个键消失——前端据此显示「报告未出」，"
                        + "而不是拿一个默认值冒充结论");
        assertEquals(5, report.size(), "首版所有预约的报告页都只有五个键");
        assertEquals("PENDING", report.get("status"));
    }

    @Test
    void j48_reportOfForeignOrMissingIsSameCode() throws Exception {
        long patientId = createPatient("检辰");
        long appointmentId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId, "appointmentDate", today.toString()))).get("appointmentId"))
                .longValue();

        mockMvc.perform(get("/user/nucleic-appointments/" + appointmentId + "/report")
                        .header("Authorization", "Bearer " + otherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(get("/user/nucleic-appointments/999999999/report")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void j48_softDeletedPatientHidesItsRowsEverywhere() throws Exception {
        long patientId = createPatient("检巳");
        long appointmentId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId, "appointmentDate", today.toString()))).get("appointmentId"))
                .longValue();
        assertEquals(1, expectDataList(getJson("/user/nucleic-appointments")).size());

        mockMvc.perform(delete("/user/patients/" + patientId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());

        assertEquals(0, expectDataList(getJson("/user/nucleic-appointments")).size(),
                "@TableLogic 软删的就诊人，它的预约不再出现在列表");
        mockMvc.perform(get("/user/nucleic-appointments/" + appointmentId + "/report")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void appointmentIdStaysInsideJsSafeInteger() throws Exception {
        // T14 的跨卡闸门：appointmentId 一定经客户端回传（列表点进报告页）。
        // NucleicAppointment extends BaseEntity，@TableId(AUTO) 在 BaseEntity:13，
        // 所以本卡不需要像 Invoice/QueueStatus 那样自己补注解——这条断言就是那个结论的实测。
        long patientId = createPatient("检午");
        long appointmentId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId, "appointmentDate", today.toString()))).get("appointmentId"))
                .longValue();

        assertTrue(appointmentId < 9007199254740991L,
                "预约 id 必须落在 JS 安全整数内，否则小程序会把末几位改掉、报告页必然 5001，实际："
                        + appointmentId);
        Map<?, ?> report = expectData(getJson("/user/nucleic-appointments/" + appointmentId + "/report"));
        assertEquals(appointmentId, ((Number) report.get("appointmentId")).longValue(),
                "接口回的 id 原样送回能查到，说明 JSON 往返没丢精度");
    }

    // ============================================================
    // 端点数量：§9.1 两个 + PRD 页面名撑起的一个 = 三把，不多不少
    // ============================================================

    @Test
    void nucleicEndpointsAreExactlyTheThreeTheSpecNamed() {
        TreeSet<String> registered = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                if (pattern.startsWith("/user/nucleic-appointments")) {
                    registered.add(verb + " " + pattern);
                }
            }
        });

        assertEquals(3, registered.size(),
                "创建 + 列表 + 报告，三把；「预约详情」（PRD 305 行）刻意并进报告页，实际：" + registered);
        assertEquals("[GET /user/nucleic-appointments, GET /user/nucleic-appointments/{id}/report, "
                        + "POST /user/nucleic-appointments]",
                registered.toString(), "路径与动词都钉住：多出第四把必须先有规格出处");
    }

    // ============================================================
    // 审计、角色与只读纪律
    // ============================================================

    @Test
    void auditIsWrittenInSameTransactionAndRollsBackWithRejection() throws Exception {
        long patientId = createPatient("检未");
        int auditBefore = count("SELECT COUNT(*) FROM audit_log "
                + "WHERE action = 'CREATE_NUCLEIC_APPOINTMENT'");

        expectData(postJson(Map.of("patientId", patientId, "appointmentDate", today.toString())));
        assertEquals(auditBefore + 1, count("SELECT COUNT(*) FROM audit_log "
                + "WHERE action = 'CREATE_NUCLEIC_APPOINTMENT'"), "写操作必须留痕（附录 B 第 3 条）");

        // 被 @FutureOrPresent 拒掉的那一次：审计行与业务写同生共死，一条都不许多。
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "appointmentDate", today.minusDays(2).toString())))
                .andExpect(status().isBadRequest());
        assertEquals(auditBefore + 1, count("SELECT COUNT(*) FROM audit_log "
                        + "WHERE action = 'CREATE_NUCLEIC_APPOINTMENT'"),
                "被拒的申请既不落预约行也不留审计行——这同时证明审计没走 @Async/REQUIRES_NEW/afterCommit");
    }

    @Test
    void staffAndAnonymousCannotReachNucleicEndpoints() throws Exception {
        mockMvc.perform(get("/user/nucleic-appointments").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(post("/user/nucleic-appointments")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", 1L, "appointmentDate", today.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/nucleic-appointments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/nucleic-appointments/1/report"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsWriteNothingIntoTheDatabase() throws Exception {
        long patientId = createPatient("检申");
        long appointmentId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId, "appointmentDate", today.toString()))).get("appointmentId"))
                .longValue();
        int rows = count("SELECT COUNT(*) FROM nucleic_appointment");
        int audit = count("SELECT COUNT(*) FROM audit_log");

        for (int i = 0; i < 5; i++) {
            expectDataList(getJson("/user/nucleic-appointments"));
            expectData(getJson("/user/nucleic-appointments/" + appointmentId + "/report"));
        }

        assertEquals(rows, count("SELECT COUNT(*) FROM nucleic_appointment"), "两个读端点不许多写一行");
        assertEquals(audit, count("SELECT COUNT(*) FROM audit_log"), "读不留痕");
    }

    // ============================================================
    // 探针与助手
    // ============================================================

    /**
     * 裸插一条"报告已出"的预约行。这是<strong>人工取证探针</strong>，不是产品行为：
     * 首版没有任何代码路径会写 {@code report}（红线 624 行），不插这一条就没法证 J48 的读路径。
     */
    private long insertProbe(long patientId, String reportText) {
        String orderNo = PROBE_PREFIX + String.format("%08d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000));
        probeOrderNos.add(orderNo);
        jdbcTemplate.update("INSERT INTO nucleic_appointment (order_no, patient_id, appointment_date, "
                        + "status, report) VALUES (?, ?, ?, 'COMPLETED', ?)",
                orderNo, patientId, today, reportText);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM nucleic_appointment WHERE order_no = ?", Long.class, orderNo);
        if (id == null) {
            throw new IllegalStateException("探针预约行没插进去，order_no=" + orderNo);
        }
        return id;
    }

    private String ownerToken() throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t21-owner");
        }
        return patientToken;
    }

    private String otherToken() throws Exception {
        return newPatientToken("t21-other");
    }

    private long createPatient(String name) throws Exception {
        ownerToken();
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
        snapshot.put("nucleic_appointment", count("SELECT COUNT(*) FROM nucleic_appointment"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("audit_log", count("SELECT COUNT(*) FROM audit_log"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String randomCardNo() {
        return "T21" + String.format("%07d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
    }

    private String json(Map<?, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<?, ?> asMap(Object item) {
        assertTrue(item instanceof Map, "期望对象型元素，实际：" + item);
        return (Map<?, ?>) item;
    }

    private MockHttpServletRequestBuilder createRequest(Map<?, ?> body) throws Exception {
        return post("/user/nucleic-appointments")
                .header("Authorization", "Bearer " + ownerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private MvcResult postJson(Map<?, ?> body) throws Exception {
        return mockMvc.perform(createRequest(body)).andExpect(status().isOk()).andReturn();
    }

    private MvcResult getJson(String path) throws Exception {
        return getWith(patientToken, path);
    }

    private MvcResult getWith(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
    }

    private List<?> expectDataList(MvcResult result) throws Exception {
        Map<?, ?> root = readRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        return (List<?>) data;
    }

    private Map<?, ?> expectData(MvcResult result) throws Exception {
        Map<?, ?> root = readRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }

    private Map<?, ?> readRoot(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }
}
