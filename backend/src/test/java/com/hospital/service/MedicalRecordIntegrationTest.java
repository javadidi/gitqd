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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T18 病历查询（卡片 562–574 行）。J41 病历列表 / J42 病历详情，各带一组边界例。
 *
 * <h2>这张表没有生产者，所以病历行只能裸插</h2>
 * 与 T17 的 {@code report} 同构：{@code seed.sql} 零行、28 张卡里没有任何一张写它。
 * 所以本类里 {@code INSERT INTO medical_record …} 是<strong>人工取证探针</strong>，
 * 不是产品写路径；产品侧本卡一律只读。就诊人仍走 T08 的真实端点创建，
 * 这样归属链 {@code medical_record.patient_id → patient.user_id → user} 是被真实数据验证的。
 *
 * <h2>「医嘱」这一例是本卡最重要的一条断言</h2>
 * 卡片 566 行与 PRD 177 行点名了医嘱，但 PRD 590 行数据字典与 V1:220-232 建表语句都没有这一列。
 * 本类用 {@code j42_detailHasNoAdviceField} 把这个取舍<b>钉成可测的事实</b>：
 * 响应里既没有 {@code advice} 也没有 {@code doctorAdvice}。
 * 这样产品哪天决定支持医嘱时，会有一条测试红着提醒"契约变了"，而不是靠翻日志发现。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class MedicalRecordIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private final List<Long> createdRecordIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    private String patientToken;
    private String doctorToken;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
        doctorToken = jwtUtil.generateToken(3L, "doctor", "doctor",
                List.of("dashboard", "schedule", "appointment", "report"), List.of());
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        for (Long recordId : createdRecordIds) {
            jdbcTemplate.update("DELETE FROM medical_record WHERE id = ?", recordId);
        }
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(),
                "T18 只动自己造的探针行：六项计数必须回到基线");
    }

    // ============================================================
    // J41 病历列表 → 数据正确（卡片 571 行）
    // ============================================================

    @Test
    void j41_listReturnsMyRecordsNewestFirst() throws Exception {
        long patientId = createPatient("病甲");
        long older = insertRecord(patientId, 1L, "急性上呼吸道感染", "阿莫西林胶囊 0.5g × 20 粒",
                LocalDateTime.now().minusDays(30));
        long newer = insertRecord(patientId, 2L, "高血压 1 级（低危）", "苯磺酸氨氯地平片 5mg × 7 片",
                LocalDateTime.now().minusDays(2));

        List<?> items = expectDataList(getJson("/user/medical-records"));

        assertEquals(2, items.size());
        List<Long> ids = items.stream().map(i -> ((Number) asMap(i).get("recordId")).longValue()).toList();
        // record_time 是 NOT NULL（V1:227），所以倒序永远有依据，不存在 T17 那种空时间排最后的情况
        assertEquals(List.of(newer, older), ids, "按就诊时间倒序：最近一次在前");

        Map<?, ?> first = asMap(items.get(0));
        assertEquals("病甲", first.get("patientName"));
        // 倒序后第一条是"最近那次就诊"，本例给它挂的是医生 2（李慧敏），不是 1
        assertEquals("李慧敏", first.get("doctorName"), "医生名来自 doctor 表，与预约记录/候诊页同一个出处");
        assertNotNull(first.get("recordNo"), "病历编号该在（列表与详情都带它）");
        assertNotNull(first.get("recordTime"));
        // 第二条（较早那次）挂医生 1：证明名字是逐行按 doctor_id 解析的，不是整页共用一个
        assertEquals("张伟", asMap(items.get(1)).get("doctorName"));
    }

    @Test
    void j41_listRowShapeIsExactlyFiveFields() throws Exception {
        long patientId = createPatient("病乙");
        insertRecord(patientId, 1L, "诊断很长的一段文字", "处方也很长", LocalDateTime.now().minusDays(1));

        Map<?, ?> item = onlyItem(getJson("/user/medical-records"));

        // 期望值按 DTO 的声明顺序写：Jackson 出的键序就是声明顺序，不是字母序（T17 侥幸对上一次）
        assertEquals(List.of("recordId", "recordNo", "patientName", "doctorName", "recordTime"),
                new ArrayList<>(item.keySet()),
                "列表就是 PRD 590 行里的四个身份/时间字段；正文两列留给详情");
        assertFalse(item.containsKey("diagnosis"), "诊断是 TEXT，列表铺开没法看（T15/T17 同一条纪律）");
        assertFalse(item.containsKey("prescription"), "处方同上");
        assertFalse(item.containsKey("patientId"), "内部主键不外放");
        assertFalse(item.containsKey("doctorId"), "医生只给名字：患者认名字，不认 id");
    }

    @Test
    void j41_thereIsNoFilterBecauseTheSpecNeverAskedForOne() throws Exception {
        // 与 T17 恰好相反：病历表没有分类列（V1:220-232），PRD 176 行只说「展示历史病历列表」。
        // 这一例钉住"没有筛选参数"是有意的而不是漏做 —— 传什么都不改变结果集。
        long patientId = createPatient("病丙");
        long first = insertRecord(patientId, 1L, "a", "b", LocalDateTime.now().minusDays(3));
        long second = insertRecord(patientId, 2L, "c", "d", LocalDateTime.now().minusDays(1));

        List<?> plain = expectDataList(getJson("/user/medical-records"));
        List<?> withParams = expectDataList(getJson("/user/medical-records?type=LAB&doctorId=2&page=2"));

        assertEquals(2, plain.size());
        assertEquals(idsOf(withParams), idsOf(plain),
                "未知参数一律不产生筛选效果：本端点没有筛选语义，也没有分页");
        assertEquals(List.of(second, first), idsOf(plain));
    }

    @Test
    void j41_recordsOfTwoPatientsUnderOneUserAreBothListed() throws Exception {
        long mine = createPatient("病丁");
        long alsoMine = createPatient("病戊");
        long a = insertRecord(mine, 1L, "x", "y", LocalDateTime.now().minusDays(5));
        long b = insertRecord(alsoMine, 1L, "x", "y", LocalDateTime.now().minusDays(1));

        List<Long> ids = idsOf(expectDataList(getJson("/user/medical-records")));

        assertEquals(2, ids.size(), "归属是「我这个账号名下的就诊人」的并集，不是单个就诊人");
        assertTrue(ids.containsAll(List.of(a, b)), "两个就诊人的病历都要在，实际：" + ids);
    }

    @Test
    void j41_softDeletedRecordIsInvisibleEverywhere() throws Exception {
        long patientId = createPatient("病己");
        long gone = insertRecord(patientId, 1L, "不该被读到", "不该被读到",
                LocalDateTime.now(), 1);

        assertEquals(0, idsOf(expectDataList(getJson("/user/medical-records"))).size(),
                "软删行不出现在列表（@TableLogic 自动补 deleted=0）");
        mockMvc.perform(get("/user/medical-records/" + gone)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void j41_anotherUserSeesNothingAndGuessingIdsGives5001() throws Exception {
        long mine = createPatient("病庚");
        long recordId = insertRecord(mine, 1L, "我的诊断", "我的处方", LocalDateTime.now());

        String otherToken = newPatientToken("t18-intruder");
        assertEquals(0, idsOf(expectDataList(getWith(otherToken, "/user/medical-records"))).size(),
                "别人的病历一条都不该出现");

        // 越权与不存在同码：403 等于允许枚举别人的病历
        mockMvc.perform(get("/user/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        assertEquals(List.of(recordId), idsOf(expectDataList(getJson("/user/medical-records"))),
                "我这条当然还在");
    }

    // ============================================================
    // J42 病历详情 → 内容正确（卡片 572 行）
    // ============================================================

    @Test
    void j42_detailCarriesDiagnosisAndPrescriptionVerbatim() throws Exception {
        long patientId = createPatient("病辛");
        String diagnosis = "2型糖尿病；糖尿病视网膜病变（非增殖期）";
        String prescription = "二甲双胍缓释片 0.5g 口服 每日两次；眼底荧光造影复查，三个月后门诊随访。";
        long recordId = insertRecord(patientId, 1L, diagnosis, prescription,
                LocalDateTime.now().minusDays(7));

        Map<?, ?> detail = expectData(getJson("/user/medical-records/" + recordId));

        assertEquals(recordId, ((Number) detail.get("recordId")).longValue());
        assertEquals("病辛", detail.get("patientName"));
        assertEquals("张伟", detail.get("doctorName"));
        assertEquals(diagnosis, detail.get("diagnosis"), "诊断原文一字不改（PRD 590 行的「诊断」）");
        assertEquals(prescription, detail.get("prescription"), "处方原文一字不改（PRD 590 行的「处方」）");
        assertNotNull(detail.get("recordTime"));
    }

    @Test
    void j42_detailHasNoAdviceField() throws Exception {
        // 卡片 566 行与 PRD 177 行点名「医嘱」，但 PRD 590 行数据字典与 V1:220-232 都没有这一列。
        // 本卡的取舍（不加列、不显示、不编造）钉在这里：将来真要支持医嘱，这条会红着提醒契约变了。
        long patientId = createPatient("病壬");
        long recordId = insertRecord(patientId, 1L, "诊断", "处方", LocalDateTime.now());

        Map<?, ?> detail = expectData(getJson("/user/medical-records/" + recordId));

        assertFalse(detail.containsKey("advice"), "没有 advice：V1 的 medical_record 没这一列，本卡也不加");
        assertFalse(detail.containsKey("doctorAdvice"), "同理，不拿别的字段冒充医嘱");
        assertFalse(detail.containsKey("note"), "不造一个万能备注字段来装医嘱");
        assertEquals(List.of("recordId", "recordNo", "patientName", "doctorName", "recordTime",
                        "diagnosis", "prescription"),
                new ArrayList<>(detail.keySet()),
                "详情七个字段全部可追到 PRD 590 行（recordNo 是列存在带来的第七项）");
    }

    @Test
    void j42_absentDiagnosisAndPrescriptionBecomeAbsentKeys() throws Exception {
        // 两列都可空（V1:225/226）+ Jackson NON_NULL ⇒ 键整个消失，前端必须 || '—'。
        long patientId = createPatient("病癸");
        long recordId = insertRecord(patientId, 1L, null, null, LocalDateTime.now());

        Map<?, ?> detail = expectData(getJson("/user/medical-records/" + recordId));

        assertFalse(detail.containsKey("diagnosis"), "没写诊断就没有这个键，而不是 diagnosis: null");
        assertFalse(detail.containsKey("prescription"), "同上");
        assertNotNull(detail.get("recordTime"), "record_time 是 NOT NULL，这一项永远在");
    }

    @Test
    void j42_detailOfNonexistentRecordIsSameCodeAsNotMine() throws Exception {
        ownerToken();
        mockMvc.perform(get("/user/medical-records/999999999")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void recordIdStaysInsideJsSafeInteger() throws Exception {
        // T14 的跨卡闸门：会被客户端回传的 id 必须自增且落在 2^53 以内。
        // MedicalRecord extends BaseEntity ⇒ @TableId(AUTO) 本该成立，但"本该"不是证据。
        long patientId = createPatient("病子");
        insertRecord(patientId, 1L, "x", "y", LocalDateTime.now());

        Map<?, ?> item = onlyItem(getJson("/user/medical-records"));

        assertTrue(((Number) item.get("recordId")).longValue() < 9007199254740991L,
                "病历 id 必须在 JS Number.MAX_SAFE_INTEGER 以内，否则小程序会静默改掉末位");
    }

    @Test
    void j42_softDeletedDoctorLeavesNameNullNotFake() throws Exception {
        // 医生行被软删时 @TableLogic 会让 selectBatchIds 跳过它 ⇒ 名字解析不出来。
        // 这里不新建医生（那要写 doctor 表），而是插一条指向不存在 doctorId 的探针行，
        // 走的是同一条解析路径，效果一样且零污染。
        long patientId = createPatient("病丑");
        long recordId = insertRecord(patientId, 999999L, "诊断", "处方", LocalDateTime.now());

        Map<?, ?> detail = expectData(getJson("/user/medical-records/" + recordId));

        assertNull(detail.get("doctorName"), "解析不出医生名就是没有这个键，不拿 id 或占位符冒充");
        assertEquals("病丑", detail.get("patientName"), "其余字段照常返回，一条名字不该拖垮整页");
    }

    // ============================================================
    // 角色隔离与只读证明
    // ============================================================

    @Test
    void staffAndAnonymousCannotReachMedicalRecordEndpoints() throws Exception {
        mockMvc.perform(get("/user/medical-records")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/medical-records"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/medical-records/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsWriteNothingIntoTheDatabase() throws Exception {
        long patientId = createPatient("病寅");
        long recordId = insertRecord(patientId, 1L, "诊断正文", "处方正文", LocalDateTime.now());
        String before = fingerprint(recordId);
        // 基线要在探针插完之后再采：与 @BeforeEach 的 countsBefore 比会把自己那条算成"多出来的"
        // （T17 就是这么红过一次）。
        int recordRows = count("SELECT COUNT(*) FROM medical_record");
        int auditRows = count("SELECT COUNT(*) FROM audit_log");

        for (int i = 0; i < 5; i++) {
            expectDataList(getWith(patientToken, "/user/medical-records"));
            expectData(getWith(patientToken, "/user/medical-records/" + recordId));
        }

        assertEquals(before, fingerprint(recordId), "连打十次读，病历行本身一个字都不许变");
        assertEquals(recordRows, count("SELECT COUNT(*) FROM medical_record"), "行数不变");
        assertEquals(auditRows, count("SELECT COUNT(*) FROM audit_log"),
                "只读接口不留审计（PRD 485 行把审计限定在管理后台操作）");
    }

    // ============================================================
    // 探针与助手
    // ============================================================

    private long insertRecord(long patientId, long doctorId, String diagnosis, String prescription,
                              LocalDateTime recordTime) {
        return insertRecord(patientId, doctorId, diagnosis, prescription, recordTime, 0);
    }

    /**
     * 裸插一条病历探针行。中文走 JDBC 参数（不是 shell 命令行），所以没有 GBK 问题；
     * record_no 带 T18 前缀，方便万一漏删时反查。
     */
    private long insertRecord(long patientId, long doctorId, String diagnosis, String prescription,
                              LocalDateTime recordTime, int deleted) {
        String recordNo = "T18" + String.format("%08d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000));
        jdbcTemplate.update("INSERT INTO medical_record (record_no, patient_id, doctor_id, "
                + "diagnosis, prescription, record_time, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                recordNo, patientId, doctorId, diagnosis, prescription, recordTime, deleted);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM medical_record WHERE record_no = ?",
                Long.class, recordNo);
        long recordId = id == null ? -1L : id;
        createdRecordIds.add(recordId);
        return recordId;
    }

    private String fingerprint(long recordId) {
        return jdbcTemplate.queryForObject(
                "SELECT CONCAT(IFNULL(diagnosis, ''), '|', IFNULL(prescription, ''), '|', "
                        + "doctor_id, '|', DATE_FORMAT(record_time, '%Y-%m-%d %H:%i:%s'), '|', deleted) "
                        + "FROM medical_record WHERE id = ?",
                String.class, recordId);
    }

    private List<Long> idsOf(List<?> items) {
        return items.stream().map(i -> ((Number) asMap(i).get("recordId")).longValue()).toList();
    }

    private String ownerToken() throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t18-owner");
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
        snapshot.put("medical_record", count("SELECT COUNT(*) FROM medical_record"));
        snapshot.put("report", count("SELECT COUNT(*) FROM report"));
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
        return "T18" + String.format("%07d",
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
