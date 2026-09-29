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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T19 电子发票（卡片 578–592 行）。J43 开票申请 → 发票记录创建 / J44 票据详情 → 内容正确。
 *
 * <h2>本卡是 P4 三张卡里唯一有生产者的一张</h2>
 * T16 的 {@code queue_status}、T17 的 {@code report}、T18 的 {@code medical_record} 都只能裸插读，
 * 而发票行是<b>产品代码自己写进去的</b>（{@code POST /user/invoices}）。
 * 所以这里的取证分两层：缴费单仍是裸插探针（门诊账单由医院推，本系统没有建单端点，
 * 与 T15 同一处境），<b>发票则一律经真接口产生</b>——J43 要证的正是"申请会落一条记录"。
 *
 * <h2>并发那条用例是本卡最值钱的断言</h2>
 * {@code uk_payment_id}（V5）存在的意义就是它：两个线程同时对同一张缴费单开票时，
 * 前置查在两边都看不到对方，只有唯一索引能拦。断言"恰好一张发票 + 一个 200 一个 3005"，
 * 少了这条，双层守卫的下层就只是注释里的一句话。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class InvoiceIntegrationTest {

    /** items 形状抄 seed.sql:192（与 T15 同一份），票据详情的明细解析才有得对。 */
    private static final String TWO_ITEMS_JSON =
            "[{\"name\":\"血常规\",\"amountFen\":1200},{\"name\":\"胃镜检查\",\"amountFen\":2800}]";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private final List<Long> createdPaymentIds = new ArrayList<>();
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
        // 先删发票再删缴费单：invoice 有 uk_payment_id，反过来删会留下指向空气的票
        jdbcTemplate.update("DELETE FROM invoice WHERE payment_id IN "
                + "(SELECT id FROM payment_record WHERE order_no LIKE 'T19P%')");
        for (Long paymentId : createdPaymentIds) {
            jdbcTemplate.update("DELETE FROM payment_record WHERE id = ?", paymentId);
        }
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM payment_record WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM invoice WHERE payment_id IN "
                    + "(SELECT id FROM payment_record WHERE patient_id = ?)", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        // 只删本卡自己产生的审计：CREATE_INVOICE 这个 action 只有这里会写
        jdbcTemplate.update("DELETE FROM audit_log WHERE action = 'CREATE_INVOICE'");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(),
                "T19 只动自己造的探针行：六项计数必须回到基线");
    }

    // ============================================================
    // J43 开票申请 → 发票记录创建（卡片 589 行）
    // ============================================================

    @Test
    void j43_pendingListShowsOnlyPaidAndUninvoicedBills() throws Exception {
        long patientId = createPatient("票甲");
        long paid = insertPaidPayment(patientId, 4000);
        insertPendingPayment(patientId, 5000);   // 还没缴 → 不该出现在待开具里

        Map<?, ?> item = onlyItem(getJson("/user/invoices/pending"));

        assertEquals(paid, ((Number) item.get("paymentId")).longValue(),
                "待开具的主体是缴费单：还没开票的发票压根不存在，不能拿发票表当骨架");
        assertEquals(4000L, ((Number) item.get("amountFen")).longValue(), "可开票金额就是缴费金额");
        assertEquals("票甲", item.get("patientName"));
        assertTrue(String.valueOf(item.get("orderNo")).startsWith("T19P"),
                "单号原样带回，实际：" + item.get("orderNo"));
        assertNotNull(item.get("paidAt"), "缴费时间给出来，患者才知道这张单是哪天的");
    }

    @Test
    void j43_applyCreatesTheInvoiceRow() throws Exception {
        long patientId = createPatient("票乙");
        long paymentId = insertPaidPayment(patientId, 4000);

        Map<?, ?> created = expectData(postJson("/user/invoices", Map.of("paymentId", paymentId)));

        assertNotNull(created.get("invoiceId"), "申请必须把发票 id 回出来，成功页与详情都要它");
        assertTrue(String.valueOf(created.get("invoiceNo")).startsWith("FP"),
                "发票编号走已建好的 SerialType.FP，实际：" + created.get("invoiceNo"));
        assertEquals("ISSUED", created.get("status"), "模拟阶段申请即开票（真实通道的受理态二期再说）");
        assertEquals(4000L, ((Number) created.get("amountFen")).longValue());

        // 库里真的落了一行，而不只是回了一个像成功的 JSON
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice WHERE payment_id = ?", Integer.class, paymentId);
        assertEquals(1, rows.intValue(), "J43 的判据是「发票记录创建」，必须落到库");
        assertEquals(4000L, jdbcTemplate.queryForObject(
                        "SELECT amount_fen FROM invoice WHERE payment_id = ?", Long.class, paymentId)
                .longValue(), "金额抄自缴费单");
    }

    @Test
    void j43_mockInvoiceCodeIsObviouslyFake() throws Exception {
        // 卡片 586 行红线「不做真实开票（二期做）；首版仅模拟开票流程」。
        // 真实发票代码会被患者拿去税务平台验真，所以模拟值必须一眼假：
        // 编一个像真的 12 位数字就是造伪凭证，比留空更糟。
        long patientId = createPatient("票丙");
        long paymentId = insertPaidPayment(patientId, 1200);

        Map<?, ?> created = expectData(postJson("/user/invoices", Map.of("paymentId", paymentId)));
        String code = String.valueOf(created.get("invoiceCode"));

        assertTrue(code.startsWith("MOCK-"), "发票代码必须是明显的模拟值，实际：" + code);
        // 反面断言：绝不能编出一个"看起来能拿去税务平台验真"的纯数字代码
        assertFalse(code.replace("MOCK-", "").matches("[0-9]+"),
                "去掉 MOCK- 前缀后不许是纯数字，实际：" + code);
    }

    @Test
    void j43_appliedBillDisappearsFromPendingList() throws Exception {
        long patientId = createPatient("票丁");
        long paymentId = insertPaidPayment(patientId, 4000);
        expectData(postJson("/user/invoices", Map.of("paymentId", paymentId)));

        assertEquals(0, expectDataList(getJson("/user/invoices/pending")).size(),
                "开过票的单必须从待开具列表消失——这正是卡片 581 行「可开票的缴费记录」的直译");

        Map<?, ?> issued = onlyItem(getJson("/user/invoices"));
        assertEquals(1, expectDataList(getJson("/user/invoices")).size(),
                "开过的那张要出现在已开具列表里，正好一条");
        assertNotNull(issued.get("invoiceNo"));
        assertNotNull(issued.get("issuedAt"));
    }

    @Test
    void j43_secondApplyOnSameBillIsRejected3005AndCreatesNothing() throws Exception {
        long patientId = createPatient("票戊");
        long paymentId = insertPaidPayment(patientId, 4000);
        expectData(postJson("/user/invoices", Map.of("paymentId", paymentId)));

        // 患者连点两次：第二次必须给「已经开过了，去已开具列表看」，
        // 而不是复用 3004「状态不允许缴费」——那会把人引回缴费流程。
        mockMvc.perform(post("/user/invoices")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentId", paymentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005));

        assertEquals(1, count("SELECT COUNT(*) FROM invoice WHERE payment_id = ?", paymentId),
                "被拒的那次一条都不能留");
    }

    @Test
    void j43_concurrentAppliesOnSameBill_onlyOneInvoiceSurvives() throws Exception {
        long patientId = createPatient("票己");
        long paymentId = insertPaidPayment(patientId, 4000);

        List<Integer> codes = twoThreadApply(paymentId);
        long ok = codes.stream().filter(code -> code == 200).count();
        long rejected = codes.stream().filter(code -> code == 3005).count();

        assertEquals(1L, ok, "必须恰好一个成功，实际：" + codes);
        assertEquals(1L, rejected, "另一个必须撞 uk_payment_id 拿 3005，实际：" + codes);
        assertEquals(1, count("SELECT COUNT(*) FROM invoice WHERE payment_id = ?", paymentId),
                "前置查在两个线程里都看不见对方，只有唯一索引拦得住——这条断言就是 V5 存在的全部理由");
    }

    @Test
    void j43_amountCannotBeChangedByTheClient() throws Exception {
        long patientId = createPatient("票庚");
        long paymentId = insertPaidPayment(patientId, 4000);

        // 发票是要拿去报销的凭证，"缴 4000 开 400000" 必须结构上不可能
        Map<?, ?> created = expectData(postJson("/user/invoices",
                Map.of("paymentId", paymentId, "amountFen", 400000L, "status", "PENDING")));

        assertEquals(4000L, ((Number) created.get("amountFen")).longValue(),
                "入参里的金额与状态一律无效：金额只从缴费单抄，状态由服务端定");
        assertEquals("ISSUED", created.get("status"));
    }

    @Test
    void j43_applyOnUnpaidOrForeignOrMissingBillIs5001() throws Exception {
        long patientId = createPatient("票辛");
        long unpaid = insertPendingPayment(patientId, 5000);
        long foreign = insertPaidPayment(createPatientFor("票壬", otherToken()), 6000);

        mockMvc.perform(post("/user/invoices")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentId", unpaid))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(post("/user/invoices")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentId", foreign))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(post("/user/invoices")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentId", 999999999L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));

        assertEquals(0, count("SELECT COUNT(*) FROM invoice"),
                "三种被拒都不许留下发票行");
    }

    @Test
    void j43_missingPaymentIdIsRejectedByValidation() throws Exception {
        ownerToken();
        // @NotNull 走 MethodArgumentNotValidException → HTTP 400 + code 400（T03 起的既有映射）
        mockMvc.perform(post("/user/invoices")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amountFen", 100))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ============================================================
    // J44 票据详情 → 内容正确（卡片 590 行）
    // ============================================================

    @Test
    void j44_detailCarriesInvoiceFieldsAndPaymentItems() throws Exception {
        long patientId = createPatient("票癸");
        long paymentId = insertPaidPayment(patientId, 4000);
        long invoiceId = ((Number) expectData(
                postJson("/user/invoices", Map.of("paymentId", paymentId))).get("invoiceId")).longValue();

        Map<?, ?> detail = expectData(getJson("/user/invoices/" + invoiceId));

        assertEquals(invoiceId, ((Number) detail.get("invoiceId")).longValue());
        assertEquals("票癸", detail.get("patientName"));
        assertEquals(4000L, ((Number) detail.get("amountFen")).longValue());
        assertEquals("ISSUED", detail.get("status"));
        assertNotNull(detail.get("invoiceNo"));
        assertNotNull(detail.get("invoiceCode"));
        assertNotNull(detail.get("issuedAt"));
        assertTrue(String.valueOf(detail.get("paymentOrderNo")).startsWith("T19P"),
                "PRD 592 行的「缴费ID」回的是患者可读的单号");

        List<?> items = (List<?>) detail.get("items");
        assertEquals(2, items.size(), "票据必须写清自己开的是哪几项，否则患者无从核对");
        assertEquals("血常规", asMap(items.get(0)).get("name"));
        assertEquals(1200, ((Number) asMap(items.get(0)).get("amountFen")).intValue(),
                "明细与 T15 缴费详情走同一个解析路径，两处显示必然一致");
    }

    @Test
    void j44_detailShapeIsExactlyNineFieldsAndNoInternalIds() throws Exception {
        long patientId = createPatient("票子");
        long paymentId = insertPaidPayment(patientId, 1200);
        long invoiceId = ((Number) expectData(
                postJson("/user/invoices", Map.of("paymentId", paymentId))).get("invoiceId")).longValue();

        Map<?, ?> detail = expectData(getJson("/user/invoices/" + invoiceId));

        assertEquals(List.of("invoiceId", "invoiceNo", "invoiceCode", "status", "amountFen",
                        "patientName", "paymentOrderNo", "issuedAt", "items"),
                new ArrayList<>(detail.keySet()),
                "PRD 592 行五项 + 编号/就诊人/时间/明细，多一个键就是破口");
        assertFalse(detail.containsKey("paymentId"), "内部主键不外放：详情靠 invoiceId 定位就够");
        assertFalse(detail.containsKey("patientId"), "同上");
    }

    @Test
    void j44_listRowShapeAndStatusPassThrough() throws Exception {
        long patientId = createPatient("票丑");
        long paymentId = insertPaidPayment(patientId, 2800);
        expectData(postJson("/user/invoices", Map.of("paymentId", paymentId)));

        Map<?, ?> row = onlyItem(getJson("/user/invoices"));

        assertEquals(List.of("invoiceId", "invoiceNo", "invoiceCode", "status", "amountFen",
                        "patientName", "paymentOrderNo", "issuedAt"),
                new ArrayList<>(row.keySet()), "列表八项：明细留给详情（T15/T17 同一条纪律）");
        assertEquals("ISSUED", row.get("status"), "状态原样回码值，中文标签在前端");
        assertFalse(row.containsKey("items"));
    }

    @Test
    void j44_otherUsersInvoiceIsInvisibleBothWays() throws Exception {
        long mine = createPatient("票寅");
        long paymentId = insertPaidPayment(mine, 4000);
        long invoiceId = ((Number) expectData(
                postJson("/user/invoices", Map.of("paymentId", paymentId))).get("invoiceId")).longValue();

        String other = otherToken();
        assertEquals(0, expectDataList(getWith(other, "/user/invoices")).size(),
                "别人的发票不在我的已开具列表里");
        assertEquals(0, expectDataList(getWith(other, "/user/invoices/pending")).size(),
                "别人的缴费单也不在我的待开具列表里");
        mockMvc.perform(get("/user/invoices/" + invoiceId).header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(get("/user/invoices/999999999").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void invoiceIdStaysInsideJsSafeInteger() throws Exception {
        // T14 的跨卡闸门这一次是**真的会咬人**：发票行由本卡自己插入，
        // 且 invoiceId 一定经客户端回传（?id= 进票据详情）。
        // Invoice 不 extends BaseEntity（表没有 deleted 列），所以 @TableId(AUTO) 必须自己写。
        long patientId = createPatient("票卯");
        long paymentId = insertPaidPayment(patientId, 1200);

        Map<?, ?> created = expectData(postJson("/user/invoices", Map.of("paymentId", paymentId)));
        long invoiceId = ((Number) created.get("invoiceId")).longValue();

        assertTrue(invoiceId < 9007199254740991L,
                "发票 id 必须落在 JS 安全整数内，否则小程序会把末几位改掉、详情页必然 5001，实际：" + invoiceId);
        // 更强的一条：把 JSON 文本里的数字原样读回来比对，证明序列化侧也没丢精度
        assertTrue(String.valueOf(created.get("invoiceNo")).length() > 4);
    }

    // ============================================================
    // 审计与角色
    // ============================================================

    @Test
    void auditIsWrittenInSameTransactionAndRollsBackWithRejection() throws Exception {
        long patientId = createPatient("票辰");
        long paymentId = insertPaidPayment(patientId, 4000);
        int auditBefore = count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_INVOICE'");

        expectData(postJson("/user/invoices", Map.of("paymentId", paymentId)));
        assertEquals(auditBefore + 1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_INVOICE'"),
                "写操作必须留痕（附录 B 审计同事务）");

        // 被 3005 拒掉的那一次：审计行与业务写同生共死，一条都不许多
        mockMvc.perform(post("/user/invoices")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentId", paymentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005));
        assertEquals(auditBefore + 1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_INVOICE'"),
                "被拒的申请不留审计行——这同时证明审计没走 @Async/REQUIRES_NEW/afterCommit");
    }

    @Test
    void staffAndAnonymousCannotReachInvoiceEndpoints() throws Exception {
        mockMvc.perform(get("/user/invoices").header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/invoices/pending"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/invoices")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("paymentId", 1L))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/invoices/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readsWriteNothingIntoTheDatabase() throws Exception {
        long patientId = createPatient("票巳");
        long paymentId = insertPaidPayment(patientId, 4000);
        long invoiceId = ((Number) expectData(
                postJson("/user/invoices", Map.of("paymentId", paymentId))).get("invoiceId")).longValue();
        int invoices = count("SELECT COUNT(*) FROM invoice");
        int audit = count("SELECT COUNT(*) FROM audit_log");

        for (int i = 0; i < 5; i++) {
            expectDataList(getJson("/user/invoices/pending"));
            expectDataList(getJson("/user/invoices"));
            expectData(getJson("/user/invoices/" + invoiceId));
        }

        assertEquals(invoices, count("SELECT COUNT(*) FROM invoice"), "三个读端点都不许多写一行");
        assertEquals(audit, count("SELECT COUNT(*) FROM audit_log"), "读不留痕");
        assertEquals(1, count("SELECT COUNT(*) FROM invoice WHERE payment_id = ?", paymentId));
    }

    // ============================================================
    // 探针与助手
    // ============================================================

    /** 裸插一张已缴成功的门诊缴费单（本系统没有建账单的端点，与 T15 同一处境）。 */
    private long insertPaidPayment(long patientId, long amountFen) {
        return insertPayment(patientId, amountFen, "SUCCESS");
    }

    private long insertPendingPayment(long patientId, long amountFen) {
        return insertPayment(patientId, amountFen, "PENDING");
    }

    private long insertPayment(long patientId, long amountFen, String status) {
        String orderNo = "T19P" + String.format("%08d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000));
        jdbcTemplate.update("INSERT INTO payment_record (order_no, patient_id, items, amount_fen, "
                        + "pay_method, status) VALUES (?, ?, ?, ?, 'WECHAT', ?)",
                orderNo, patientId, TWO_ITEMS_JSON, amountFen, status);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM payment_record WHERE order_no = ?",
                Long.class, orderNo);
        if (id == null) {
            throw new IllegalStateException("探针缴费单没插进去，order_no=" + orderNo);
        }
        createdPaymentIds.add(id);
        return id;
    }

    private List<Integer> twoThreadApply(long paymentId) throws Exception {
        String token = patientToken;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = List.of(
                    pool.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return codeOf(mockMvc.perform(post("/user/invoices")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("paymentId", paymentId)))).andReturn());
                    }),
                    pool.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return codeOf(mockMvc.perform(post("/user/invoices")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("paymentId", paymentId)))).andReturn());
                    }));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "两个线程没能同时就位");
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                codes.add(future.get(20, TimeUnit.SECONDS));
            }
            return codes;
        } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
            throw new IllegalStateException("并发开票没跑起来", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private int codeOf(MvcResult result) throws Exception {
        Map<?, ?> root = objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
        return ((Number) root.get("code")).intValue();
    }

    private String ownerToken() throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t19-owner");
        }
        return patientToken;
    }

    private String otherToken() throws Exception {
        return newPatientToken("t19-other");
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
        snapshot.put("invoice", count("SELECT COUNT(*) FROM invoice"));
        snapshot.put("payment_record", count("SELECT COUNT(*) FROM payment_record"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("report", count("SELECT COUNT(*) FROM report"));
        snapshot.put("audit_log", count("SELECT COUNT(*) FROM audit_log"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String randomCardNo() {
        return "T19" + String.format("%07d",
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

    private MvcResult postJson(String path, Map<?, ?> body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + ownerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
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
