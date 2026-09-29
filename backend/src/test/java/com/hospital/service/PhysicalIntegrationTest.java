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
import java.time.LocalDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T22 体检预约（卡片 634–650 行）。J49 体检预约 → 记录创建 / J50 体检报告 → 内容正确。
 *
 * <h2>三张体检表，本卡只生产其中一张</h2>
 * 预约行（{@code physical_appointment}）经真接口产生——J49 要证的就是这个；
 * <b>套餐行与体检报告行都是人工取证探针</b>：套餐的生产者点名是 T27 后台
 * （PRD 406–408 行、{@code App.tsx:73} 占位），报告的生产者点名是 T25 后台
 * （PRD 357 行「查看/录入体检报告」），两张都不在本卡，
 * 而 {@code seed.sql} 对这三张表一行都没有（库里实测 {@code pkg 0 / item 0 / apt 0}）。
 * 所以本卡<strong>不往 seed 里塞套餐让页面好看</strong>，套餐列表首版就该是空的。
 *
 * <h2>本卡最硬的一条断言是"钱一分不动"</h2>
 * 卡片 640 行让患者"确认费用"，但预约表没有价格列、规格里没有体检缴费这一步。
 * 于是 {@link #j49_noMoneyMovesAtAll} 在创建前后各读一次
 * {@code payment_record} 行数与 {@code patient.balance_fen} 总额，要求<b>两个数字一字不变</b>。
 * 这一条比"响应里没有金额键"结实得多：它证明的不是形状，是账本没被碰。
 *
 * <h2>J50 走的是 T17 的两个端点，不是新端点</h2>
 * 体检报告的数据就在 {@code report} 表（V1:206 列注释含 PHYSICAL），
 * T22 只把 T17 预留的白名单钩子放开（{@code ReportType.isQueryable}）。
 * {@link #j50_physicalReportIsReadableThroughTheT17Endpoints} 就是这条跨卡路径的实测，
 * 而 T17 自己那 15 例里原本断言"PHYSICAL 进不来"的那条已同步反转（附录 C 要求重跑被改卡）。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class PhysicalIntegrationTest {

    /** 探针前缀：套餐 T22P、报告 T22R，与产品发号（TJ/RC 等）一眼分得开。 */
    private static final String PACKAGE_PREFIX = "T22P";
    private static final String REPORT_PREFIX = "T22R";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private final List<Long> createdPackageIds = new ArrayList<>();
    private final List<Long> createdReportIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;
    private Long balanceBefore;

    private String patientToken;
    private String doctorToken;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
        balanceBefore = balanceSum();
        today = LocalDate.now();
        doctorToken = jwtUtil.generateToken(3L, "doctor", "doctor",
                List.of("dashboard", "schedule", "appointment", "report"), List.of());
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM physical_appointment WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM report WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        for (Long packageId : createdPackageIds) {
            jdbcTemplate.update("DELETE FROM physical_package WHERE id = ?", packageId);
        }
        for (Long reportId : createdReportIds) {
            jdbcTemplate.update("DELETE FROM report WHERE id = ?", reportId);
        }
        jdbcTemplate.update("DELETE FROM audit_log WHERE action = 'CREATE_PHYSICAL_APPOINTMENT'");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(),
                "T22 只动自己造的探针套餐/报告与本卡预约行：七项计数必须回到基线");
        assertEquals(balanceBefore, balanceSum(),
                "本卡全程不碰钱：就诊人余额总额必须一分不差地回到基线");
    }

    // ============================================================
    // 套餐只读端点（卡片 638–639 行）
    // ============================================================

    @Test
    void packagesListIsEmptyBecauseSeedHasNone() throws Exception {
        ownerToken();
        // 结构性事实：physical_package 零行，而生产者是 T27（不是"没人负责"）。
        // 所以首版患者侧这一页必然是空的——本卡不塞 seed 让它好看。
        assertEquals(0, count("SELECT COUNT(*) FROM physical_package"),
                "seed.sql 里没有一行套餐（逐字 grep 过），这条断言就是那个事实的锚点");
        assertEquals(0, expectDataList(getJson("/user/physical-packages")).size(),
                "接口回空数组，不是 500、也不是 null");
    }

    @Test
    void packagesListReturnsProbeRowsInIdOrder() throws Exception {
        ownerToken();
        long first = insertPackage("基础体检（探针）", 19900L, "[\"身高\",\"体重\"]");
        long second = insertPackage("入职体检（探针）", 9900L, "[\"血常规\"]");

        List<?> rows = expectDataList(getJson("/user/physical-packages"));
        assertEquals(2, rows.size(), "探针行都该在列表里");
        assertEquals(first, ((Number) asMap(rows.get(0)).get("packageId")).longValue(),
                "没有 sort_order 列（套餐表没有，与 department 不同），所以按 id 升序回");
        assertEquals(second, ((Number) asMap(rows.get(1)).get("packageId")).longValue());

        Map<?, ?> row = asMap(rows.get(0));
        assertEquals(List.of("name", "packageId", "priceFen", "targetAudience"),
                new ArrayList<>(new TreeSet<>(row.keySet())),
                "列表四键：明细留给详情，也不派生一个「含 N 项」");
        assertEquals(19900L, ((Number) row.get("priceFen")).longValue(), "价格是分，全链路不出现浮点");
        assertFalse(row.containsKey("typeId"), "type_id 没有可解析的套餐类型表，不外放也不显示");
    }

    @Test
    void packageDetailCarriesPriceAndItemsUntouched() throws Exception {
        ownerToken();
        long packageId = insertPackage("全面体检（探针）", 58800L,
                "[{\"name\":\"身高\"},{\"id\":7,\"note\":\"外科\"}]");

        Map<?, ?> detail = expectData(getJson("/user/physical-packages/" + packageId));

        assertEquals(List.of("items", "name", "packageId", "priceFen", "targetAudience"),
                new ArrayList<>(new TreeSet<>(detail.keySet())), "详情五键 = 字典五项减去类型ID加上价格明细");
        assertEquals(58800L, ((Number) detail.get("priceFen")).longValue());
        assertEquals("探针套餐", detail.get("targetAudience"), "适用人群原样回");
        // items 是原样透传的 JsonNode：两个元素、各自的键一个不丢、顺序不变。
        // 后端不挑字段不改键名，是因为没有任何规格定义过这一列的形状（见 DTO 类注释）。
        List<?> items = (List<?>) detail.get("items");
        assertEquals(2, items.size(), "形状原样：两个元素就是两个元素");
        assertEquals("身高", asMap(items.get(0)).get("name"));
        assertEquals(7, ((Number) asMap(items.get(1)).get("id")).intValue(),
                "第二个元素带的是 id 与 note——后端要是自造了 {name,value} 结构，这一步就会红");
        assertEquals("外科", asMap(items.get(1)).get("note"));
    }

    @Test
    void unknownPackageDetailIs5001AndListIgnoresAnyFilter() throws Exception {
        mockMvc.perform(get("/user/physical-packages/999999999")
                        .header("Authorization", "Bearer " + ownerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        // 传任何筛选参数结果集一字不变：套餐表没有可筛的分类列，PRD 也没给参数。
        // 这一条不是防回归给"筛选功能"，是防止下一个读代码的人把"没做筛选"当成漏做补上。
        long packageId = insertPackage("筛不掉的套餐（探针）", 100L, "[]");
        List<?> plain = expectDataList(getJson("/user/physical-packages"));
        List<?> filtered = expectDataList(getJson(
                "/user/physical-packages?type=1&keyword=入职&page=2&sort=price"));
        assertEquals(plain.size(), filtered.size(), "没有筛选可生效");
        assertEquals(1, filtered.size(), "唯一那行套餐在，且不受参数影响");
        assertEquals(packageId, ((Number) asMap(filtered.get(0)).get("packageId")).longValue());
    }

    // ============================================================
    // J49 体检预约 → 记录创建（卡片 647 行）
    // ============================================================

    @Test
    void j49_createAppointmentWritesTheRow() throws Exception {
        long patientId = createPatient("检甲");
        long packageId = insertPackage("入职体检（探针）", 9900L, "[\"血常规\"]");

        Map<?, ?> created = expectData(postJson(Map.of(
                "patientId", patientId,
                "packageId", packageId,
                "appointmentDate", today.toString())));

        assertNotNull(created.get("appointmentId"), "预约 id 必须回出来，成功页与记录页都要它");
        assertTrue(String.valueOf(created.get("orderNo")).startsWith("TJ"),
                "单号走 T02 就建好的 SerialType.TJ（体检单号），实际：" + created.get("orderNo"));
        assertEquals("PENDING", created.get("status"), "V1:286 的初始态，也是本卡唯一写入的值");
        assertEquals("入职体检（探针）", created.get("packageName"), "确认页当场要看到套餐名");
        assertEquals(9900L, ((Number) created.get("priceFen")).longValue(),
                "费用从套餐表现读，与创建响应同一次就回出来");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT patient_id, package_id, appointment_date, status FROM physical_appointment WHERE id = ?",
                ((Number) created.get("appointmentId")).longValue());
        assertEquals(patientId, ((Number) row.get("patient_id")).longValue(),
                "J49 的判据是「记录创建」，必须落到库");
        assertEquals(packageId, ((Number) row.get("package_id")).longValue());
        assertEquals("PENDING", row.get("status"));
    }

    @Test
    void j49_priceIsNeverTakenFromTheClient() throws Exception {
        long patientId = createPatient("检乙");
        long packageId = insertPackage("贵套餐（探针）", 88800L, "[]");

        Map<?, ?> created = expectData(postJson(Map.of(
                "patientId", patientId,
                "packageId", packageId,
                "appointmentDate", today.toString(),
                "priceFen", 1L,
                "amountFen", 1L,
                "status", "COMPLETED",
                "orderNo", "TJ-FAKE")));

        assertEquals(88800L, ((Number) created.get("priceFen")).longValue(),
                "客户端塞的价格一律无效：预约表没有价格列，费用只有一个出处");
        assertEquals("PENDING", created.get("status"), "状态也不接受声明");
        assertTrue(String.valueOf(created.get("orderNo")).startsWith("TJ20"),
                "单号由服务端发，客户端给的 TJ-FAKE 不生效，实际：" + created.get("orderNo"));
        assertFalse(created.containsKey("amountFen"), "响应里不会长出客户端造的那个键");
    }

    @Test
    void j49_noMoneyMovesAtAll() throws Exception {
        // 本卡最硬的一条：体检"确认费用"只是显示，不产生任何资金动作。
        // 规格里没有体检缴费这一步（T15 的门诊账单是院内推的），所以造扣款就是替
        // 一笔不存在的交易编凭证。这条断言比"响应没金额键"结实——它证的是账本没被碰。
        long patientId = createPatient("检丙");
        long packageId = insertPackage("收费探针套餐", 66500L, "[]");
        int paymentsBefore = count("SELECT COUNT(*) FROM payment_record");
        int refundsBefore = count("SELECT COUNT(*) FROM refund_record");
        int rechargeBefore = count("SELECT COUNT(*) FROM recharge_record");

        expectData(postJson(Map.of("patientId", patientId, "packageId", packageId,
                "appointmentDate", today.toString())));
        expectData(postJson(Map.of("patientId", patientId, "packageId", packageId,
                "appointmentDate", today.plusDays(1).toString())));

        assertEquals(paymentsBefore, count("SELECT COUNT(*) FROM payment_record"),
                "两次预约不多一条缴费单");
        assertEquals(refundsBefore, count("SELECT COUNT(*) FROM refund_record"), "不产生退款单");
        assertEquals(rechargeBefore, count("SELECT COUNT(*) FROM recharge_record"), "不产生充值流水");
        assertEquals(balanceBefore, balanceSum(), "所有就诊人的余额总额一分不变");
    }

    @Test
    void j49_missingOrPastDateIsRejectedByValidation() throws Exception {
        long patientId = createPatient("检丁");
        long packageId = insertPackage("校验探针套餐", 100L, "[]");

        mockMvc.perform(createRequest(Map.of("packageId", packageId,
                        "appointmentDate", today.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(createRequest(Map.of("patientId", patientId,
                        "appointmentDate", today.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(createRequest(Map.of("patientId", patientId, "packageId", packageId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(createRequest(Map.of("patientId", patientId, "packageId", packageId,
                        "appointmentDate", today.minusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        assertEquals(0, count("SELECT COUNT(*) FROM physical_appointment"),
                "四次被拒一条都不许落库");
    }

    @Test
    void j49_foreignPatientOrMissingPackageIs5001AndCreatesNothing() throws Exception {
        createPatient("检戊");
        long foreign = createPatientFor("检己", otherToken());
        long packageId = insertPackage("归属探针套餐", 100L, "[]");

        mockMvc.perform(createRequest(Map.of("patientId", foreign, "packageId", packageId,
                        "appointmentDate", today.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(createRequest(Map.of("patientId", foreign, "packageId", 999999999L,
                        "appointmentDate", today.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        // 套餐不存在也要 5001：package_id 是 NOT NULL（V1:284），
        // 不查就会留下一条"套餐名与价格都读不出来"的记录。
        mockMvc.perform(createRequest(Map.of("patientId", foreign, "packageId", 999999999L,
                        "appointmentDate", today.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        assertEquals(0, count("SELECT COUNT(*) FROM physical_appointment"),
                "别人的体检人、不存在的套餐，两种都不能落行");
    }

    @Test
    void listShowsMyRowsWithPackageValuesAndHidesOthers() throws Exception {
        long mine = createPatient("检庚");
        long alsoMine = createPatient("检辛");
        // otherToken() 每次调用都会新登录一个用户，所以"别人"只取一次、两处共用
        //（T21 踩过一次，这里照那条教训写）
        String other = otherToken();
        long foreign = createPatientFor("检壬", other);
        long pkgA = insertPackage("套餐甲（探针）", 12000L, "[]");
        long pkgB = insertPackage("套餐乙（探针）", 34000L, "[]");
        expectData(postJson(Map.of("patientId", mine, "packageId", pkgA,
                "appointmentDate", today.toString())));
        expectData(postJson(Map.of("patientId", alsoMine, "packageId", pkgB,
                "appointmentDate", today.plusDays(2).toString())));

        List<?> rows = expectDataList(getJson("/user/physical-appointments"));
        assertEquals(2, rows.size(), "两个体检人名下的都要合并进来（与 T18/T21 同一条断言）");
        Map<?, ?> newest = asMap(rows.get(0));
        assertEquals(List.of("appointmentDate", "appointmentId", "orderNo", "packageName",
                        "patientName", "priceFen", "status"),
                new ArrayList<>(new TreeSet<>(newest.keySet())),
                "列表七键：套餐与费用现场从套餐表带，因为预约表没有这两列");
        assertEquals("套餐乙（探针）", newest.get("packageName"));
        assertEquals(34000L, ((Number) newest.get("priceFen")).longValue());
        assertFalse(newest.containsKey("packageId"), "内部主键不外放，套餐回名字");

        createPatientFor("检癸", other);
        List<?> foreignRows = expectDataList(getWith(other, "/user/physical-appointments"));
        assertEquals(0, foreignRows.size(), "别人的预约不在我的列表里");
    }

    @Test
    void softDeletedPackageLeavesNameAndPriceAbsentNotZero() throws Exception {
        long patientId = createPatient("检子");
        long packageId = insertPackage("会被软删的套餐", 4500L, "[]");
        long appointmentId = ((Number) expectData(postJson(Map.of("patientId", patientId,
                "packageId", packageId, "appointmentDate", today.toString()))).get("appointmentId"))
                .longValue();
        assertEquals(4500L, ((Number) onlyRow().get("priceFen")).longValue(), "先确认价格读得出来");

        jdbcTemplate.update("UPDATE physical_package SET deleted = 1 WHERE id = ?", packageId);
        Map<?, ?> row = onlyRow();

        assertFalse(row.containsKey("packageName"), "套餐软删后名字键消失，不拿 id 冒充");
        assertFalse(row.containsKey("priceFen"),
                "价格也不许兜成 0——0 元与「价格未知」在钱上是两件完全不同的事");
        assertEquals(appointmentId, ((Number) row.get("appointmentId")).longValue(),
                "预约行本身还在（本卡不级联删），只是读不出套餐信息");
    }

    // ============================================================
    // J50 体检报告 → 内容正确（卡片 648 行，走 T17 的两个端点）
    // ============================================================

    @Test
    void j50_physicalReportIsReadableThroughTheT17Endpoints() throws Exception {
        ownerToken();
        // 这一条就是 T22 那"一行白名单"钩子的落地实测（ReportType.isQueryable 放开 PHYSICAL）。
        // 报告行的生产者是 T25 后台「查看/录入体检报告」（PRD 357 行），所以这里是人工取证探针。
        long patientId = createPatient("检丑");
        long reportId = insertPhysicalReport(patientId, "[\"身高\",\"血压\",\"视力\"]",
                "未见异常，建议一年一次。");

        List<?> rows = expectDataList(getJson("/user/reports?type=PHYSICAL"));
        assertEquals(1, rows.size(), "T22 之后体检报告列得出来");
        assertEquals(reportId, ((Number) asMap(rows.get(0)).get("reportId")).longValue());
        assertEquals("PHYSICAL", asMap(rows.get(0)).get("type"), "类型原样回码值，中文标签在前端");

        Map<?, ?> detail = expectData(getJson("/user/reports/" + reportId));
        assertEquals("未见异常，建议一年一次。", detail.get("result"),
                "J50「体检报告 → 内容正确」的正身");
        assertEquals(3, ((List<?>) detail.get("items")).size(), "项目原样透传，不解释形状");

        // 放开一类不等于混排：检验报告列表里不该出现体检行
        assertEquals(0, expectDataList(getJson("/user/reports?type=LAB")).size(),
                "LAB 列表仍然只筛 LAB");
    }

    @Test
    void j50_anotherUsersPhysicalReportIsStill5001() throws Exception {
        long patientId = createPatient("检寅");
        long reportId = insertPhysicalReport(patientId, "[\"身高\"]", "结论");
        String other = otherToken();

        mockMvc.perform(get("/user/reports/" + reportId).header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        assertEquals(0, expectDataList(getWith(other, "/user/reports?type=PHYSICAL")).size(),
                "放开类型不等于放开归属——归属那一跳仍然是唯一的门");
    }

    // ============================================================
    // 端点数量：本卡新增四把，且没有第五套报告端点
    // ============================================================

    @Test
    void physicalEndpointsAreExactlyTheFourThisCardAdds() {
        TreeSet<String> packages = new TreeSet<>();
        TreeSet<String> appointments = new TreeSet<>();
        TreeSet<String> reports = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                if (pattern.startsWith("/user/physical-packages")) {
                    packages.add(verb + " " + pattern);
                }
                if (pattern.startsWith("/user/physical-appointments")) {
                    appointments.add(verb + " " + pattern);
                }
                if (pattern.contains("physical-report") || pattern.contains("/user/physical-reports")) {
                    reports.add(verb + " " + pattern);
                }
            }
        });

        assertEquals("[GET /user/physical-packages, GET /user/physical-packages/{id}]",
                packages.toString(), "套餐两个只读端点，出自 §9.1 第 617 行前两项");
        assertEquals("[GET /user/physical-appointments, POST /user/physical-appointments]",
                appointments.toString(), "预约一把 POST 加一把列表（列表由 PRD 309/527 行的页面名撑起）");
        assertEquals("[]", reports.toString(),
                "刻意不开 /user/physical-reports：体检报告的数据在 report 表里，"
                        + "复用 T17 的两个端点，给同一张表两个读路径就是自造第二个出处");
    }

    // ============================================================
    // 审计、角色与只读纪律
    // ============================================================

    @Test
    void auditIsWrittenInSameTransactionAndRollsBackWithRejection() throws Exception {
        long patientId = createPatient("检卯");
        long packageId = insertPackage("审计探针套餐", 100L, "[]");
        int auditBefore = count("SELECT COUNT(*) FROM audit_log "
                + "WHERE action = 'CREATE_PHYSICAL_APPOINTMENT'");

        expectData(postJson(Map.of("patientId", patientId, "packageId", packageId,
                "appointmentDate", today.toString())));
        assertEquals(auditBefore + 1, count("SELECT COUNT(*) FROM audit_log "
                + "WHERE action = 'CREATE_PHYSICAL_APPOINTMENT'"), "写操作必须留痕（附录 B 第 3 条）");

        mockMvc.perform(createRequest(Map.of("patientId", patientId, "packageId", packageId,
                        "appointmentDate", today.minusDays(3).toString())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(createRequest(Map.of("patientId", patientId, "packageId", 999999999L,
                        "appointmentDate", today.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        assertEquals(auditBefore + 1, count("SELECT COUNT(*) FROM audit_log "
                        + "WHERE action = 'CREATE_PHYSICAL_APPOINTMENT'"),
                "两次被拒既不落预约行也不留审计行——这同时证明审计没走 @Async/REQUIRES_NEW/afterCommit");
    }

    @Test
    void staffAndAnonymousCannotReachPhysicalEndpoints() throws Exception {
        mockMvc.perform(get("/user/physical-packages").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/physical-appointments").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/physical-packages"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/physical-appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", 1L, "packageId", 1L,
                                "appointmentDate", today.toString()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsWriteNothingIntoTheDatabase() throws Exception {
        long patientId = createPatient("检辰");
        long packageId = insertPackage("只读探针套餐", 700L, "[]");
        expectData(postJson(Map.of("patientId", patientId, "packageId", packageId,
                "appointmentDate", today.toString())));
        int rows = count("SELECT COUNT(*) FROM physical_appointment");
        int packages = count("SELECT COUNT(*) FROM physical_package");
        int audit = count("SELECT COUNT(*) FROM audit_log");

        for (int i = 0; i < 5; i++) {
            expectDataList(getJson("/user/physical-packages"));
            expectData(getJson("/user/physical-packages/" + packageId));
            expectDataList(getJson("/user/physical-appointments"));
        }

        assertEquals(rows, count("SELECT COUNT(*) FROM physical_appointment"), "读端点不许多写一行");
        assertEquals(packages, count("SELECT COUNT(*) FROM physical_package"), "套餐是目录表，读不改它");
        assertEquals(audit, count("SELECT COUNT(*) FROM audit_log"), "读不留痕");
    }

    @Test
    void appointmentIdStaysInsideJsSafeInteger() throws Exception {
        // T14 的跨卡闸门：appointmentId 与 packageId 都要经客户端回传（记录页点进报告、
        // 详情页带 id）。PhysicalAppointment/PhysicalPackage 都 extends BaseEntity，
        // @TableId(AUTO) 在 BaseEntity:13，所以本卡不需要像 Invoice/QueueStatus 那样自己补注解。
        long patientId = createPatient("检巳");
        long packageId = insertPackage("精度探针套餐", 800L, "[]");
        long appointmentId = ((Number) expectData(postJson(Map.of("patientId", patientId,
                "packageId", packageId, "appointmentDate", today.toString()))).get("appointmentId"))
                .longValue();

        assertTrue(appointmentId < 9007199254740991L,
                "预约 id 必须落在 JS 安全整数内，否则小程序会把末几位改掉，实际：" + appointmentId);
        assertTrue(packageId < 9007199254740991L,
                "套餐 id 同理，实际：" + packageId);
        Map<?, ?> row = onlyRow();
        assertEquals(appointmentId, ((Number) row.get("appointmentId")).longValue(),
                "接口回的 id 在列表里原样读得回来，说明 JSON 往返没丢精度");
    }

    // ============================================================
    // 探针与助手
    // ============================================================

    /** 人工取证探针：套餐行的生产者是 T27 后台，本卡不生产它。 */
    private long insertPackage(String name, Long priceFen, String itemsJson) {
        jdbcTemplate.update("INSERT INTO physical_package (name, type_id, price_fen, target_audience, "
                        + "items) VALUES (?, NULL, ?, '探针套餐', ?)", name, priceFen, itemsJson);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM physical_package WHERE name = ? ORDER BY id DESC LIMIT 1",
                Long.class, name);
        if (id == null) {
            throw new IllegalStateException("探针套餐没插进去，name=" + name);
        }
        createdPackageIds.add(id);
        return id;
    }

    /** 人工取证探针：体检报告行的生产者是 T25 后台「录入体检报告」（PRD 357 行）。 */
    private long insertPhysicalReport(long patientId, String itemsJson, String result) {
        String reportNo = REPORT_PREFIX + String.format("%08d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000));
        jdbcTemplate.update("INSERT INTO report (report_no, patient_id, type, items, result, report_time) "
                        + "VALUES (?, ?, 'PHYSICAL', ?, ?, ?)",
                reportNo, patientId, itemsJson, result, LocalDateTime.now());
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM report WHERE report_no = ?", Long.class, reportNo);
        if (id == null) {
            throw new IllegalStateException("探针报告没插进去，report_no=" + reportNo);
        }
        createdReportIds.add(id);
        return id;
    }

    private Map<?, ?> onlyRow() throws Exception {
        List<?> rows = expectDataList(getJson("/user/physical-appointments"));
        assertEquals(1, rows.size(), "期望恰好一条预约记录，实际：" + rows);
        return asMap(rows.get(0));
    }

    private String ownerToken() throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t22-owner");
        }
        return patientToken;
    }

    private String otherToken() throws Exception {
        return newPatientToken("t22-other");
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
        snapshot.put("physical_package", count("SELECT COUNT(*) FROM physical_package"));
        snapshot.put("physical_item", count("SELECT COUNT(*) FROM physical_item"));
        snapshot.put("physical_appointment", count("SELECT COUNT(*) FROM physical_appointment"));
        snapshot.put("report", count("SELECT COUNT(*) FROM report"));
        snapshot.put("payment_record", count("SELECT COUNT(*) FROM payment_record"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("audit_log", count("SELECT COUNT(*) FROM audit_log"));
        return snapshot;
    }

    private Long balanceSum() {
        Number value = jdbcTemplate.queryForObject(
                "SELECT IFNULL(SUM(balance_fen), 0) FROM patient", Number.class);
        return value.longValue();
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String randomCardNo() {
        return "T22" + String.format("%07d",
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
        return post("/user/physical-appointments")
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

    private Map<?, ?> expectData(MvcResult result) throws Exception {
        Map<?, ?> root = readRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }

    private List<?> expectDataList(MvcResult result) throws Exception {
        Map<?, ?> root = readRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        return (List<?>) data;
    }

    private Map<?, ?> readRoot(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }
}
