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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T17 报告查询（卡片 545–558 行）。J39 报告列表 / J40 报告详情，各带一组边界例。
 *
 * <h2>这张表没有生产者，所以报告行只能裸插</h2>
 * 与 T16 的 {@code queue_status} 同构：{@code seed.sql} 零行、28 张卡里没有任何一张写它
 * （体检报告归 T22 卡片 642 行、核酸报告归 T21 卡片 622 行）。所以本类里
 * {@code INSERT INTO report …} 是<strong>人工取证探针</strong>，不是产品写路径；
 * 产品侧本卡一律只读，这条区别在 WORK_LOG 里也写一遍。
 *
 * <p>就诊人仍走 T08 的真实端点创建（不裸插），这样归属链
 * {@code report.patient_id → patient.user_id → user} 是被真实数据验证过的。
 *
 * <h2>清理口径（T16 刚在这一点上翻过两次车）</h2>
 * 自己插的行按主键删，自己建的人按 id 删，最后比对六项计数回到基线。本卡不写
 * {@code audit_log}（只读），所以额外断言它一条没多——这是"只读"的机械证明，
 * 不是自述。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class ReportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private final List<Long> createdReportIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    private String patientToken;
    private String otherToken;
    private String doctorToken;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
        // 医生 token：T03 起的固定写法，role=doctor，用来证明角色隔离仍然生效
        doctorToken = jwtUtil.generateToken(3L, "doctor", "doctor",
                List.of("dashboard", "schedule", "appointment", "report"), List.of());
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        for (Long reportId : createdReportIds) {
            // 物理删：这张表本卡只读，探针行留着会污染 T18/T19 之后的计数基线
            jdbcTemplate.update("DELETE FROM report WHERE id = ?", reportId);
        }
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(),
                "T17 只动自己造的探针行：六项计数必须回到基线");
    }

    // ============================================================
    // J39 报告列表 → 数据正确（卡片 555 行）
    // ============================================================

    @Test
    void j39_labListReturnsOnlyMyLabReportsNewestFirst() throws Exception {
        long patientId = createPatient("报甲");
        long older = insertReport(patientId, "LAB", "[\"血常规\"]", "未见异常",
                LocalDateTime.now().minusDays(9));
        long newer = insertReport(patientId, "LAB", "[\"肝功能\",\"肾功能\"]", "指标正常",
                LocalDateTime.now().minusDays(1));
        insertReport(patientId, "IMAGING", "[\"腹部超声\"]", "未见结石", LocalDateTime.now());

        List<?> items = expectDataList(getJson("/user/reports?type=LAB"));

        assertEquals(2, items.size(), "IMAGING 那条不能混进 LAB 列表（卡片 548 行的类型选择是真筛选）");
        List<Long> ids = items.stream().map(i -> ((Number) asMap(i).get("reportId")).longValue()).toList();
        assertEquals(List.of(newer, older), ids, "按报告时间倒序：最新那份在前");

        Map<?, ?> first = asMap(items.get(0));
        assertEquals("报甲", first.get("patientName"));
        assertEquals("LAB", first.get("type"), "后端只回码值，中文标签在前端（T08 relation/T10 timeSlot 同取舍）");
        assertNotNull(first.get("reportTime"));
        assertTrue(String.valueOf(first.get("reportNo")).startsWith("T17LAB"),
                "报告编号原样带回，实际：" + first.get("reportNo"));
    }

    @Test
    void j39_imagingIsASeparateList() throws Exception {
        long patientId = createPatient("报乙");
        insertReport(patientId, "LAB", "[\"血常规\"]", "ok", LocalDateTime.now());
        long imaging = insertReport(patientId, "IMAGING", "[\"胸部CT\"]", "未见明显异常", LocalDateTime.now());

        Map<?, ?> item = onlyItem(getJson("/user/reports?type=IMAGING"));

        assertEquals(imaging, ((Number) item.get("reportId")).longValue());
        assertEquals("报乙", item.get("patientName"));
    }

    @Test
    void j39_listRowShapeIsExactlyFiveFields() throws Exception {
        // 白名单断言：多一个键就是破口（结果原文、检查项目明细都只该出现在详情里）
        long patientId = createPatient("报丙");
        insertReport(patientId, "LAB", "[\"血常规\"]", "结果很长很长", LocalDateTime.now());

        Map<?, ?> item = onlyItem(getJson("/user/reports?type=LAB"));

        assertEquals(List.of("reportId", "reportNo", "type", "patientName", "reportTime"),
                new ArrayList<>(item.keySet()),
                "列表字段就是 PRD 589 行数据字典去掉 TEXT 型的「结果」，再加报告编号");
        assertFalse(item.containsKey("items"), "检查项目留给详情（与 T15 记录列表不带 items 同一条纪律）");
        assertFalse(item.containsKey("result"), "结果原文留给详情");
        assertFalse(item.containsKey("patientId"), "内部主键不外放（附录 B：响应不夹带无关标识）");
    }

    @Test
    void j39_missingTypeIsBusinessCode400_notHttp500() throws Exception {
        // 这一例钉住一个具体决定：type 用 required=false + 服务层校验。
        // 若改成 Spring 的必填参数，缺参会落到兜底 @ExceptionHandler(Exception) → HTTP 500。
        mockMvc.perform(get("/user/reports").header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void j39_unknownTypeIsRejected() throws Exception {
        ownerToken();
        // 不认识的码值一律 400，而不是"返回空列表"——空列表会被患者读成「你没有这类报告」
        mockMvc.perform(get("/user/reports?type=BLOOD").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void j39_physicalReportsBecomeReadableInT22AndStayTypeIsolated() throws Exception {
        // 这一条原本是 T17 写的「PHYSICAL 两处都进不来」（方法名旧为
        // j39_physicalReportsAreNotReadableByThisCard）。T22 卡片 642 行 / J50 承接体检报告，
        // 按 ReportType 类注释预留的钩子放开了白名单，所以断言整个反过来——
        // 这不是把测试改松了，而是被改卡的契约确实变了，改的同时把"类型之间仍然互斥"钉上：
        // 放开 PHYSICAL 不等于让 LAB 列表里混进体检报告。
        long patientId = createPatient("报丁");
        long physical = insertReport(patientId, "PHYSICAL", "[\"身高\",\"血压\"]", "体检结论",
                LocalDateTime.now());
        long lab = insertReport(patientId, "LAB", "[\"血常规\"]", "ok", LocalDateTime.now());

        List<?> physicalRows = expectDataList(getJson("/user/reports?type=PHYSICAL"));
        assertEquals(1, physicalRows.size(), "体检报告列表只该有那一行");
        assertEquals(physical, ((Number) asMap(physicalRows.get(0)).get("reportId")).longValue(),
                "T22 之后 PHYSICAL 进得来，且只回体检行");
        assertEquals("PHYSICAL", asMap(physicalRows.get(0)).get("type"), "类型原样回，不做翻译");

        List<?> labRows = expectDataList(getJson("/user/reports?type=LAB"));
        assertEquals(1, labRows.size(), "检验报告列表仍只有那一行——放开一类不等于混排");
        assertEquals(lab, ((Number) asMap(labRows.get(0)).get("reportId")).longValue());

        Map<?, ?> detail = expectData(getJson("/user/reports/" + physical));
        assertEquals("体检结论", detail.get("result"), "J50「体检报告 → 内容正确」的读路径就是这一条");
        assertEquals("PHYSICAL", detail.get("type"));

        // 闸还在：类型不认识仍然 400，放开 PHYSICAL 没有把白名单变成来者不拒
        mockMvc.perform(get("/user/reports?type=BLOOD").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void j39_softDeletedReportIsInvisibleEverywhere() throws Exception {
        // Report extends BaseEntity + deleted 上有 @TableLogic ⇒ MP 自己追加 deleted=0，
        // 所以这里不需要手写条件；探针就是插一行 deleted=1 进来。
        long patientId = createPatient("报戊");
        long reportId = insertReport(patientId, "LAB", "[\"血常规\"]", "ok",
                LocalDateTime.now(), 1);

        List<?> items = expectDataList(getJson("/user/reports?type=LAB"));
        assertEquals(0, items.size(), "软删行不该出现在列表里");

        mockMvc.perform(get("/user/reports/" + reportId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void j39_anotherPatientsListCannotSeeMineAndGuessingIdsGives5001() throws Exception {
        long mine = createPatient("报己");
        long reportId = insertReport(mine, "LAB", "[\"血常规\"]", "ok", LocalDateTime.now());

        // 第二个账号：自己一个就诊人、零报告
        otherToken = newPatientToken("t17-intruder");
        assertEquals(0, expectDataList(getWith(otherToken, "/user/reports?type=LAB")).size(),
                "别人的报告一条都不该出现在我的列表里（归属要经 patient.user_id 跳一次）");

        // 猜到了真实 id 也读不出来，且与"根本没这条"同码（403 会确认存在性 → 可枚举）
        mockMvc.perform(get("/user/reports/" + reportId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        Map<?, ?> item = onlyItem(getJson("/user/reports?type=LAB"));
        assertEquals(reportId, ((Number) item.get("reportId")).longValue(), "我这条当然还在");
    }

    // ============================================================
    // J40 报告详情 → 内容正确（卡片 556 行）
    // ============================================================

    @Test
    void j40_detailCarriesItemsAndResultVerbatim() throws Exception {
        long patientId = createPatient("报庚");
        long reportId = insertReport(patientId, "IMAGING", "[\"胸部CT\",\"腹部超声\"]",
                "两肺纹理清晰，未见实质性病变。", LocalDateTime.now().minusHours(6));

        Map<?, ?> detail = expectData(getJson("/user/reports/" + reportId));

        assertEquals(reportId, ((Number) detail.get("reportId")).longValue());
        assertEquals("IMAGING", detail.get("type"));
        assertEquals("报庚", detail.get("patientName"));
        assertEquals("两肺纹理清晰，未见实质性病变。", detail.get("result"), "结果原文一字不改（PRD 589 行的「结果」）");
        assertEquals(List.of("胸部CT", "腹部超声"), detail.get("items"),
                "检查项目原样透传：库里是什么数组，接口就是什么数组");
    }

    @Test
    void j40_itemsArePassedThroughUninterpreted_evenWithKeysWeNeverHeardOf() throws Exception {
        // 这一例是本卡最重要的一个"不做"：items 的 JSON 形状没有任何规格出处
        // （V1:207 只有一句列注释，seed 零行可抄）。既然后端假装不认识它，
        // 那无论生产者放什么键，都必须一个不落地原样回来。
        long patientId = createPatient("报辛");
        String exotic = "[{\"name\":\"上腹部MRI\",\"hounsfield\":42,\"impression\":[\"脂肪肝\",\"疑似囊肿\"]}]";
        long reportId = insertReport(patientId, "IMAGING", exotic, "见印象", LocalDateTime.now());

        Map<?, ?> detail = expectData(getJson("/user/reports/" + reportId));

        List<?> items = (List<?>) detail.get("items");
        Map<?, ?> one = asMap(items.get(0));
        assertEquals("上腹部MRI", one.get("name"));
        assertEquals(42, ((Number) one.get("hounsfield")).intValue(), "后端没见过的键也要原样带出来");
        assertEquals(List.of("脂肪肝", "疑似囊肿"), one.get("impression"), "嵌套结构同样不解释");
    }

    @Test
    void j40_absentItemsAndResultBecomeAbsentKeys() throws Exception {
        // application.yml 的 default-property-inclusion: non_null ⇒ null 字段整个键消失，
        // 前端必须写成 value || '—'。这一例把这件事钉在报告详情上（T09/T14 已各钉过一次）。
        // 报告未出时 result 与 report_time 本来就是空的（V1:208/209 都可空）。
        long patientId = createPatient("报壬");
        long reportId = insertReport(patientId, "LAB", null, null, null);

        Map<?, ?> detail = expectData(getWith(patientToken, "/user/reports/" + reportId));

        assertFalse(detail.containsKey("items"), "没写检查项目就没有这个键，而不是 items: null");
        assertFalse(detail.containsKey("result"), "同上，结果也可能为空（报告未出时）");
        assertFalse(detail.containsKey("reportTime"), "V1:209 的 report_time 可空，键一并消失");
        assertEquals("报壬", detail.get("patientName"), "该在的字段仍在，别把断言写成整页皆空");
    }

    @Test
    void j40_detailOfNonexistentReportIsSameCodeAsNotMine() throws Exception {
        ownerToken();
        mockMvc.perform(get("/user/reports/999999999").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void reportIdStaysInsideJsSafeInteger() throws Exception {
        // T14 的跨卡教训：会被客户端回传的 id 必须自增且落在 2^53 以内。
        // Report extends BaseEntity（带 @TableId(AUTO)），这条本该天然成立——但"本该"不是证据。
        long patientId = createPatient("报癸");
        long reportId = insertReport(patientId, "LAB", "[\"血常规\"]", "ok", LocalDateTime.now());

        Map<?, ?> item = onlyItem(getJson("/user/reports?type=LAB"));

        assertTrue(((Number) item.get("reportId")).longValue() < 9007199254740991L,
                "报告 id 必须在 JS Number.MAX_SAFE_INTEGER 以内，否则小程序会静默改掉末位，实际：" + reportId);
    }

    // ============================================================
    // 角色隔离与只读证明
    // ============================================================

    @Test
    void staffAndAnonymousCannotReachReportEndpoints() throws Exception {
        mockMvc.perform(get("/user/reports").param("type", "LAB")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/reports"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/reports/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsWriteNothingIntoTheDatabase() throws Exception {
        long patientId = createPatient("报子");
        long reportId = insertReport(patientId, "LAB", "[\"血常规\"]", "ok", LocalDateTime.now());
        String before = fingerprint(reportId);
        // 基线要在探针插完之后再采：与 @BeforeEach 的 countsBefore 比会把自己那条算成"多出来的"
        // （第一版就是这么红的：expected <0> but was <1>，那 1 行正是本例自己插的）。
        int reportRows = count("SELECT COUNT(*) FROM report");
        int auditRows = count("SELECT COUNT(*) FROM audit_log");

        for (int i = 0; i < 5; i++) {
            expectDataList(getWith(patientToken, "/user/reports?type=LAB"));
            expectData(getWith(patientToken, "/user/reports/" + reportId));
        }

        assertEquals(before, fingerprint(reportId),
                "连打十次读，报告行本身（类型/项目/结果/时间）一个字都不许变");
        assertEquals(reportRows, count("SELECT COUNT(*) FROM report"), "既没多行也没少行");
        assertEquals(auditRows, count("SELECT COUNT(*) FROM audit_log"),
                "只读接口不留审计（PRD 485 行把审计限定在管理后台操作）");
    }

    // ============================================================
    // 探针与助手
    // ============================================================

    /** 插一行报告；deleted 走 0。返回自增主键，登记待删。 */
    private long insertReport(long patientId, String type, String items, String result,
                              LocalDateTime reportTime) {
        return insertReport(patientId, type, items, result, reportTime, 0);
    }

    private long insertReport(long patientId, String type, String items, String result,
                              LocalDateTime reportTime, int deleted) {
        String reportNo = "T17" + type.substring(0, 3) + String.format("%06d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000));
        jdbcTemplate.update("INSERT INTO report (report_no, patient_id, type, items, result, "
                + "report_time, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                reportNo, patientId, type, items, result, reportTime, deleted);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM report WHERE report_no = ?",
                Long.class, reportNo);
        long reportId = id == null ? -1L : id;
        createdReportIds.add(reportId);
        return reportId;
    }

    /** 报告行的内容指纹：四个业务列拼起来，用来证明"读过之后一个字节都没变"。 */
    private String fingerprint(long reportId) {
        return jdbcTemplate.queryForObject(
                "SELECT CONCAT(type, '|', IFNULL(items, ''), '|', IFNULL(result, ''), '|', "
                        + "IFNULL(DATE_FORMAT(report_time, '%Y-%m-%d %H:%i:%s'), '')) FROM report WHERE id = ?",
                String.class, reportId);
    }

    private String ownerToken() throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t17-owner");
        }
        return patientToken;
    }

    private long createPatient(String name) throws Exception {
        ownerToken();
        Map<?, ?> data = expectData(mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + patientToken)
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
        snapshot.put("report", count("SELECT COUNT(*) FROM report"));
        snapshot.put("medical_record", count("SELECT COUNT(*) FROM medical_record"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("appointment", count("SELECT COUNT(*) FROM appointment"));
        snapshot.put("audit_log", count("SELECT COUNT(*) FROM audit_log"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String randomCardNo() {
        return "T17" + String.format("%07d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
    }

    private String json(Map<?, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<?, ?> asMap(Object item) {
        assertTrue(item instanceof Map, "期望对象型元素，实际：" + item);
        return (Map<?, ?>) item;
    }

    private Map<?, ?> onlyItem(MvcResult result) throws Exception {
        List<?> items = expectDataList(result);
        assertEquals(1, items.size(), "期望恰好一行，实际：" + items);
        return asMap(items.get(0));
    }

    private MvcResult getJson(String path) throws Exception {
        return getWith(patientToken, path);
    }

    /** 用指定 token 发一次 GET。越权例需要以"第二个人"的身份打。 */
    private MvcResult getWith(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
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
