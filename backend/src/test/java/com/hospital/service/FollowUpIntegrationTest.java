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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T20 复诊配药（卡片 598–612 行）。J45 复诊申请 → 记录创建 / J46 复诊详情 → 内容正确。
 *
 * <h2>本卡是 {@code follow_up} 的第一个生产者，取证方式与 P4 后三张卡相反</h2>
 * T16/T17/T18 那三张只能裸插探针行（{@code queue_status}/{@code report}/{@code medical_record}
 * 全仓没有写它们的一行代码），而这里<b>复诊行一律经真接口产生</b>——
 * J45 要证的恰恰就是"申请会落一条记录"。测试里唯一直接写库的是就诊人（走 {@code POST /user/patients}）
 * 和软删动作，没有任何一条 {@code INSERT INTO follow_up}。
 *
 * <h2>四条"有意不做"各钉一条断言，将来谁改就谁红</h2>
 * <ul>
 *   <li>不给配药字段 → {@link #j46_detailShapeIsExactlySevenFieldsAndCarriesNoMedication}
 *       （七键白名单 + 五个药名字样逐个不许出现）；</li>
 *   <li>不做列表端点 → {@link #followUpEndpointsAreExactlyTheTwoTheSpecNamed}
 *       （直接从 Spring 的注册表里数 {@code /user/follow-ups} 上有几个映射）；</li>
 *   <li>不复制 T19 的唯一索引（复诊本就可以申请多次）→ {@link #j45_secondApplyCreatesASecondRow}；</li>
 *   <li>状态不接受客户端声明 → {@link #j45_statusAndOtherInventedFieldsCannotBeSetByTheClient}。</li>
 * </ul>
 *
 * <h2>{@code /user/follow-ups} 的映射计数为什么敢在测试里读注册表</h2>
 * 因为它比对 HTTP 状态码稳：未映射的路径经 {@code GlobalExceptionHandler} 兜底成 500，
 * 那个数字属 Spring 行为、不是本卡契约，钉死它将来配 404 处理器时会误红。
 * 注册表断言只问一件事——这个路径下挂了几把手，正是"§9.1 只给两个接口"的可执行翻译。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class FollowUpIntegrationTest {

    /** seed.sql:44-46 的三个科室与 seed.sql:60-64 的五位医生，本卡只用这些真实配对。 */
    private static final long DEPT_GASTROENTEROLOGY = 1L;
    private static final long DEPT_SURGERY = 2L;
    private static final long DOCTOR_ZHANG_WEI = 1L;      // 张伟，属科室 1
    private static final long DOCTOR_LI_HUIMIN = 2L;      // 李慧敏，属科室 1
    private static final long DOCTOR_WANG_JIANGUO = 3L;   // 王建国，属科室 2 —— 拿来做跨科室错配

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
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
        // 复诊行按"本卡自己的就诊人"删：follow_up 表里除了这些探针就诊人不可能有别人的行
        // （seed.sql 对 follow_up 零行，逐字 grep 过 insert into follow_up，无匹配）。
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM follow_up WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        // 只删本卡自己产生的审计：CREATE_FOLLOW_UP 这个 action 只有这里会写
        jdbcTemplate.update("DELETE FROM audit_log WHERE action = 'CREATE_FOLLOW_UP'");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(),
                "T20 只动自己造的探针就诊人与复诊行：四项计数必须回到基线（科室与医生是 seed 行，一张不许碰）");
    }

    // ============================================================
    // J45 复诊申请 → 记录创建（卡片 609 行）
    // ============================================================

    @Test
    void j45_applyCreatesTheFollowUpRow() throws Exception {
        long patientId = createPatient("复甲");

        Map<?, ?> created = expectData(postJson(Map.of(
                "patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_ZHANG_WEI,
                "disease", "慢性胃炎复查")));

        assertNotNull(created.get("followUpId"), "申请必须把复诊 id 回出来——它是详情页唯一的入口凭证");
        assertEquals("PENDING", created.get("status"), "V1:318 的初始态；首版没人推进它，但读路径要回原值");

        // 库里真的落了一行，而不只是回了一个像成功的 JSON
        assertEquals(1, count("SELECT COUNT(*) FROM follow_up WHERE patient_id = ?", patientId),
                "J45 的判据是「记录创建」，必须落到库");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT department_id, doctor_id, disease, status FROM follow_up WHERE patient_id = ?",
                patientId);
        assertEquals(DEPT_GASTROENTEROLOGY, ((Number) row.get("department_id")).longValue());
        assertEquals(DOCTOR_ZHANG_WEI, ((Number) row.get("doctor_id")).longValue());
        assertEquals("慢性胃炎复查", row.get("disease"), "疾病原文入库，页面显示的就是患者自己写的那句");
        assertEquals("PENDING", row.get("status"));
    }

    @Test
    void j45_statusAndOtherInventedFieldsCannotBeSetByTheClient() throws Exception {
        long patientId = createPatient("复乙");

        // 客户端把自己那行勾成"已完成"，再塞一个药品清单和一个假 id
        Map<?, ?> created = expectData(postJson(Map.of(
                "patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_ZHANG_WEI,
                "disease", "术后随访",
                "status", "COMPLETED",
                "medicines", List.of("阿莫西林"),
                "id", 999999L)));

        assertEquals("PENDING", created.get("status"),
                "状态推进是院内医生侧的动作，患者能声明"
                        + "「我的复诊已经看完」就等于自己把待办勾掉");
        assertFalse(created.containsKey("medicines"), "响应里没有配药栏目（红线 606 行）");
        assertEquals(1, count("SELECT COUNT(*) FROM follow_up WHERE patient_id = ? AND status = 'PENDING'",
                patientId), "入库的也只有 PENDING");
    }

    @Test
    void j45_secondApplyCreatesASecondRow() throws Exception {
        // 这条是"有意不复制 T19 唯一索引"的取证：发票一张缴费单只能开一次（V5 的 uk_payment_id），
        // 复诊却本就可以申请多次（PRD 615 行没有任何唯一性说法，664 行把它定义成一种常态服务）。
        // 如果将来有人照 T19 给 follow_up 加唯一索引，这条会红——那时应该有产品出处。
        long patientId = createPatient("复丙");
        Map<?, ?> body = Map.of("patientId", patientId,
                "departmentId", DEPT_SURGERY,
                "doctorId", DOCTOR_WANG_JIANGUO,
                "disease", "胆囊结石术后复查");

        long firstId = ((Number) expectData(postJson(body)).get("followUpId")).longValue();
        long secondId = ((Number) expectData(postJson(body)).get("followUpId")).longValue();

        assertTrue(secondId > firstId, "两次申请是两条独立记录，实际：" + firstId + " / " + secondId);
        assertEquals(2, count("SELECT COUNT(*) FROM follow_up WHERE patient_id = ?", patientId));
    }

    @Test
    void j45_missingRequiredFieldsAreRejectedByValidation() throws Exception {
        long patientId = createPatient("复丁");

        // @NotNull / @NotBlank 走 MethodArgumentNotValidException → HTTP 400 + code 400（T03 起的既有映射）
        mockMvc.perform(createRequest(Map.of("departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", DOCTOR_ZHANG_WEI, "disease", "胃炎")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "doctorId", DOCTOR_ZHANG_WEI, "disease", "胃炎")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "departmentId", DEPT_GASTROENTEROLOGY, "doctorId", DOCTOR_ZHANG_WEI)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        // 空白疾病：列可空（V1:317），但卡片 603 行把"疾病信息"定为流程一步，
        // 而它是本表唯一能承载复诊内容的列——空着它详情页就没东西可「内容正确」了。
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", DOCTOR_ZHANG_WEI, "disease", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(0, count("SELECT COUNT(*) FROM follow_up"), "四次被拒一条都不许留");
    }

    @Test
    void j45_diseaseLongerThanTheColumnIsRejectedNotTruncated() throws Exception {
        long patientId = createPatient("复戊");
        // V1:317 是 VARCHAR(256)。截断会把「慢性胃炎伴糜烂」这类病史改头，
        // 一条被悄悄改写的病史比当场拦下更坏，所以 @Size 是上限而不是提示。
        String tooLong = "胃".repeat(257);

        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", DOCTOR_ZHANG_WEI, "disease", tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        assertEquals(0, count("SELECT COUNT(*) FROM follow_up"), "超限那条没落库");

        // 刚好 256 字要能过：证明上限是 256，不是随手写的一个更小的数
        Map<?, ?> created = expectData(postJson(Map.of("patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_ZHANG_WEI, "disease", "胃".repeat(256))));
        assertEquals(256, String.valueOf(created.get("disease")).length(),
                "256 字整条通过并原样回出");
    }

    @Test
    void j45_foreignOrMissingPatientIs5001AndCreatesNothing() throws Exception {
        long mine = createPatient("复己");
        long foreign = createPatientFor("复庚", otherToken());

        mockMvc.perform(createRequest(Map.of("patientId", foreign,
                        "departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", DOCTOR_ZHANG_WEI, "disease", "复查")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(createRequest(Map.of("patientId", 999999999L,
                        "departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", DOCTOR_ZHANG_WEI, "disease", "复查")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        assertEquals(0, count("SELECT COUNT(*) FROM follow_up"),
                "别人的就诊人、没这个就诊人，两种都不能落行——归属只认 token 里的 userId");
        assertEquals(0, count("SELECT COUNT(*) FROM follow_up WHERE patient_id = ?", mine));
    }

    @Test
    void j45_unknownDepartmentOrDoctorIs5001() throws Exception {
        long patientId = createPatient("复辛");

        // 科室/医生不存在也给 5001，与"就诊人不是你的"同一个码：三类都是
        // "你提交的引用在库里找不到"，区分它们只是把库内形状泄露给猜的人。
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "departmentId", 999999999L,
                        "doctorId", DOCTOR_ZHANG_WEI, "disease", "复查")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", 999999999L, "disease", "复查")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        assertEquals(0, count("SELECT COUNT(*) FROM follow_up"));
    }

    @Test
    void j45_doctorFromAnotherDepartmentIsRejected400() throws Exception {
        long patientId = createPatient("复壬");
        // 王建国属普外科（seed.sql:62），却挂在消化内科（seed.sql:44）下申请。
        // 小程序结构上产生不了这种请求（医生列表本来就是按 ?departmentId= 拉的），
        // 所以这条只对手搓请求生效——给 400 而不是新开错误码。
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", DOCTOR_WANG_JIANGUO, "disease", "复查")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(0, count("SELECT COUNT(*) FROM follow_up"),
                "自相矛盾的复诊单不落库，否则详情页必然显示「消化内科 / 王建国（普外科）」");
    }

    @Test
    void j45_softDeletedPatientCannotFileAFollowUp() throws Exception {
        long patientId = createPatient("复癸");
        mockMvc.perform(delete("/user/patients/" + patientId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());

        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", DOCTOR_ZHANG_WEI, "disease", "复查")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        assertEquals(0, count("SELECT COUNT(*) FROM follow_up WHERE patient_id = ?", patientId),
                "@TableLogic 软删的就诊人不能继续挂新申请（与 T13 退号、T19 开票同一条口径）");
    }

    // ============================================================
    // J46 复诊详情 → 内容正确（卡片 610 行）
    // ============================================================

    @Test
    void j46_detailCarriesExactlyWhatWasSubmitted() throws Exception {
        long patientId = createPatient("复子");

        long followUpId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_LI_HUIMIN,
                "disease", "幽门螺杆菌复查")))
                .get("followUpId")).longValue();

        Map<?, ?> detail = expectData(getJson("/user/follow-ups/" + followUpId));

        assertEquals(followUpId, ((Number) detail.get("followUpId")).longValue());
        assertEquals("复子", detail.get("patientName"), "就诊人名字从 patient 表解析，不外放 patientId");
        assertEquals("消化内科", detail.get("departmentName"), "科室名按 seed.sql:44 的 id 1 解析");
        assertEquals("李慧敏", detail.get("doctorName"), "医生名按 seed.sql:61 的 id 2 解析");
        assertEquals("幽门螺杆菌复查", detail.get("disease"));
        assertEquals("PENDING", detail.get("status"));
        // 详情时间必须与申请接口回的时间一字不差：成功页跳详情时患者会看见同一句时间换了一种写法
        String createdAt = String.valueOf(detail.get("createdAt"));
        // 陷阱就地记一条：application.yml:35 的 date-format 只作用于 java.util.Date，
        // 对 java.time.LocalDateTime 无效 —— 后者由 JavaTimeModule 按 ISO-8601 输出。
        // 所以这里的形状是 2026-09-29T17:35:23.311（带 T，秒下小数可选），
        // 「yyyy-MM-dd HH:mm:ss」那个配置值压根不会出现在响应里。
        // 前端 format.js 的 formatDate 内部走 new Date(...)，带 T 的 ISO 正是它能解析的形式。
        assertTrue(createdAt.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?"),
                "LocalDateTime 按 ISO-8601 序列化，实际：" + createdAt);
    }

    @Test
    void j46_detailShapeIsExactlySevenFieldsAndCarriesNoMedication() throws Exception {
        long patientId = createPatient("复丑");
        long followUpId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId,
                "departmentId", DEPT_SURGERY,
                "doctorId", DOCTOR_WANG_JIANGUO,
                "disease", "腹股沟疝术后")))
                .get("followUpId")).longValue();

        Map<?, ?> detail = expectData(getJson("/user/follow-ups/" + followUpId));

        assertEquals(List.of("createdAt", "departmentName", "disease", "doctorName",
                        "followUpId", "patientName", "status"),
                new ArrayList<>(new TreeSet<>(detail.keySet())),
                "七键白名单：V1:312-323 的五列加两张名字表加申请时间，多一个键就是破口");
        // 卡片 604 行的「配药信息」有意不给字段（四路证据见 FollowUpDetailResponse 类注释）。
        // 这里逐个钉住可能的药名字样，将来谁加了这个栏目，必须先在规格里找到出处。
        for (String forbidden : List.of("medicines", "medicineInfo", "drugs", "prescription", "pharmacy")) {
            assertFalse(detail.containsKey(forbidden),
                    "红线 606 行「不做真实开药（二期做）」：响应不许出现配药字段 " + forbidden);
        }
        assertFalse(detail.containsKey("patientId"), "内部主键不外放（T13/T16/T17/T18 同一条纪律）");
        assertFalse(detail.containsKey("departmentId"));
        assertFalse(detail.containsKey("doctorId"));
    }

    @Test
    void j46_detailOfNonexistentOrForeignFollowUpIsSameCode() throws Exception {
        long patientId = createPatient("复寅");
        long followUpId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_ZHANG_WEI,
                "disease", "复查")))
                .get("followUpId")).longValue();

        String other = otherToken();
        mockMvc.perform(get("/user/follow-ups/" + followUpId).header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(get("/user/follow-ups/999999999").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        // 猜 id 猜不到内容：5001 那句文案是「数据不存在」，不回 403，
        // 因为 403 等于承认"这条存在，只是不是你的"。
    }

    @Test
    void j46_detailAfterPatientSoftDeleteIs5001() throws Exception {
        long patientId = createPatient("复卯");
        long followUpId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_ZHANG_WEI,
                "disease", "复查")))
                .get("followUpId")).longValue();

        mockMvc.perform(delete("/user/patients/" + patientId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/user/follow-ups/" + followUpId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void followUpIdStaysInsideJsSafeInteger() throws Exception {
        // T14 的跨卡闸门：follow_up 的 id 一定经客户端回传（成功页拿它跳详情）。
        // FollowUp extends BaseEntity（表有 deleted 列），@TableId(AUTO) 在 BaseEntity:13 上，
        // 所以本卡不需要像 Invoice/QueueStatus 那样自己补注解——这条断言就是那个结论的实测。
        long patientId = createPatient("复辰");
        Map<?, ?> created = expectData(postJson(Map.of(
                "patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_ZHANG_WEI,
                "disease", "复查")));

        Object rawId = created.get("followUpId");
        long followUpId = ((Number) rawId).longValue();
        assertTrue(followUpId < 9007199254740991L,
                "复诊 id 必须落在 JS 安全整数内，否则小程序会把末几位改掉、详情页必然 5001，实际：" + followUpId);

        // 更强的一条：把接口回出来的 id 原样送回详情，能查到就说明整条 JSON 往返没丢精度
        Map<?, ?> detail = expectData(getJson("/user/follow-ups/" + rawId));
        assertEquals(followUpId, ((Number) detail.get("followUpId")).longValue());
    }

    // ============================================================
    // 端点数量：§9.1 第 615 行只给两个，所以没有列表
    // ============================================================

    @Test
    void followUpEndpointsAreExactlyTheTwoTheSpecNamed() {
        // PRD 615 行逐字：| 复诊配药 | 创建复诊申请、复诊详情 |
        // 对照 614 行「病历列表、病历详情」是有列表的——本行没有，所以详情页唯一入口是申请成功页。
        // 这条断言读的是 Spring 的注册表，不是 HTTP 状态码（理由见类注释）。
        TreeSet<String> registered = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            // 自己拼「HTTP 方法 + 路径」而不是调 info.toString()：
            // 那个字符串的形状属 Spring 内部实现，随版本变，钉住它就成了脆弱的红。
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                if (pattern.startsWith("/user/follow-ups")) {
                    registered.add(verb + " " + pattern);
                }
            }
        });

        assertEquals(2, registered.size(),
                "本卡只该挂两把手（POST 创建 + GET 详情），实际注册到：" + registered);
        assertEquals("[GET /user/follow-ups/{id}, POST /user/follow-ups]", registered.toString(),
                "路径与动词都钉住：多出第三把手必须先有规格出处");
    }

    // ============================================================
    // 审计、角色与只读纪律
    // ============================================================

    @Test
    void auditIsWrittenInSameTransactionAndRollsBackWithRejection() throws Exception {
        long patientId = createPatient("复巳");
        int auditBefore = count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_FOLLOW_UP'");

        expectData(postJson(Map.of("patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_ZHANG_WEI,
                "disease", "复查")));
        assertEquals(auditBefore + 1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_FOLLOW_UP'"),
                "写操作必须留痕（附录 B 审计同事务）");

        // 被跨科室 400 拒掉的那一次：审计行与业务写同生共死，一条都不许多。
        // 这同时证明审计没走 @Async / REQUIRES_NEW / afterCommit ——
        // 那三种写法在这里都会留下一条"申请失败"的审计行。
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "departmentId", DEPT_GASTROENTEROLOGY,
                        "doctorId", DOCTOR_WANG_JIANGUO, "disease", "错配")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        assertEquals(auditBefore + 1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_FOLLOW_UP'"),
                "被拒的申请既不落复诊行，也不留审计行");
    }

    @Test
    void staffAndAnonymousCannotReachFollowUpEndpoints() throws Exception {
        mockMvc.perform(post("/user/follow-ups")
                        .header("Authorization", "Bearer " + doctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", 1L, "departmentId", 1L,
                                "doctorId", 1L, "disease", "复查"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/follow-ups/1").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/follow-ups/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/follow-ups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", 1L, "departmentId", 1L,
                                "doctorId", 1L, "disease", "复查"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsWriteNothingIntoTheDatabase() throws Exception {
        long patientId = createPatient("复午");
        long followUpId = ((Number) expectData(postJson(Map.of(
                "patientId", patientId,
                "departmentId", DEPT_GASTROENTEROLOGY,
                "doctorId", DOCTOR_ZHANG_WEI,
                "disease", "复查")))
                .get("followUpId")).longValue();
        int rows = count("SELECT COUNT(*) FROM follow_up");
        int audit = count("SELECT COUNT(*) FROM audit_log");

        for (int i = 0; i < 5; i++) {
            expectData(getJson("/user/follow-ups/" + followUpId));
        }

        assertEquals(rows, count("SELECT COUNT(*) FROM follow_up"), "读端点不许多写一行");
        assertEquals(audit, count("SELECT COUNT(*) FROM audit_log"), "读不留痕");
    }

    // ============================================================
    // 探针与助手
    // ============================================================

    private String ownerToken() throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t20-owner");
        }
        return patientToken;
    }

    private String otherToken() throws Exception {
        return newPatientToken("t20-other");
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
        snapshot.put("follow_up", count("SELECT COUNT(*) FROM follow_up"));
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
        return "T20" + String.format("%07d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
    }

    private String json(Map<?, ?> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private MockHttpServletRequestBuilder createRequest(Map<?, ?> body) throws Exception {
        return post("/user/follow-ups")
                .header("Authorization", "Bearer " + ownerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private MvcResult postJson(Map<?, ?> body) throws Exception {
        return mockMvc.perform(createRequest(body)).andExpect(status().isOk()).andReturn();
    }

    private MvcResult getJson(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + ownerToken())).andReturn();
    }

    private Map<?, ?> expectData(MvcResult result) throws Exception {
        Map<?, ?> root = objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }
}
