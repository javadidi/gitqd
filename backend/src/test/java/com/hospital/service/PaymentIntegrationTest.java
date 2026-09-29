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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 门诊自助缴费（T15）：J35 缴费 → 余额扣减 + 缴费记录、J36 余额不足 → 被拒。
 *
 * <h2>探针数据必须手写 SQL 插进去</h2>
 * 待缴费单<b>不是本系统能造出来的</b>：T15 只推进状态，没有任何端点会新建
 * {@code payment_record} 的 PENDING 行（真实部署里这些行来自 HIS，本项目里来自 {@code seed.sql}
 * 第 10 段）。所以「给这位就诊人挂一张待缴单」只能由测试用 {@code jdbcTemplate} 裸插，
 * 并把 id 登记进 {@link #createdPaymentIds} 收尾删掉。
 *
 * <h2>为什么不动种子那几张单</h2>
 * {@code SEED-PY-0002} 确实是 PENDING，但它挂在 patient 5（属于 user 2）名下。
 * 缴掉它等于永久改掉种子财务数据（{@code payment_record} 没有软删列，删不掉只能改状态），
 * 会让 {@code SeedCheckService} 与后续每张卡的演示数据都变脏。所以一律用自己的探针就诊人。
 *
 * <h2>并发用例是这张卡的核心证据</h2>
 * J36 的「拒绝」不能只在余额本来就够不到这种静态场景里测——真会翻车的是
 * <b>两笔并发各自读到同一个充足余额</b>。
 * {@link #j36_concurrentPaymentsOnSharedBalance_onlyOneWins} 用 6000+6000 对 10000 的余额把这条路钉死：
 * 恰好一笔成功、另一笔 3002、余额收在 4000、库里恰好一条 SUCCESS。
 * 少了这个用例，「余额不许花成负数」就只能靠读 SQL 的 WHERE 口头保证。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    /** items 的形状抄 seed.sql:192（{@code {"name":…,"amountFen":…}}），历史行才有得解析。 */
    private static final String TWO_ITEMS_JSON =
            "[{\"name\":\"血常规\",\"amountFen\":1200},{\"name\":\"胃镜检查\",\"amountFen\":2800}]";
    private static final String ONE_ITEM_JSON = "[{\"name\":\"儿童腹泻口服补液\",\"amountFen\":6000}]";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private final List<Long> createdPaymentIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    private String patientToken;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        for (Long paymentId : createdPaymentIds) {
            jdbcTemplate.update("DELETE FROM payment_record WHERE id = ?", paymentId);
        }
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM payment_record WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM recharge_record WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        jdbcTemplate.update("DELETE FROM audit_log WHERE action = 'PAY_OUTPATIENT_PAYMENT'");
        jdbcTemplate.update("DELETE FROM audit_log WHERE action = 'CREATE_RECHARGE'");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(), "T15 只动自己造的探针行：六项计数必须回到基线");
    }

    // ============================================================
    // J35 缴费 → 余额扣减 + 缴费记录
    // ============================================================

    @Test
    void j35_pay_deductsBalanceAndMarksRecordSuccess() throws Exception {
        long patientId = createPatient("T15-甲");
        recharge(patientId, 10000);
        long paymentId = insertPendingPayment(patientId, 4000, TWO_ITEMS_JSON);

        Map<?, ?> data = expectData(payOn(paymentId));

        assertEquals("SUCCESS", data.get("status"), "卡片 513 行：缴费要写缴费记录");
        assertEquals(4000L, ((Number) data.get("amountFen")).longValue());
        assertEquals("BALANCE", data.get("payMethod"),
                "余额支付必须记成 BALANCE；沿用单据里原来的 WECHAT 就是在财务表里记一笔假账");
        assertEquals(6000L, ((Number) data.get("balanceFen")).longValue(),
                "扣费后余额是 J35 的另一半：成功页拿它当「钱确实出卡了」的证据");
        // 第三方交易号必须留 NULL：余额支付全程不出本院系统，编一个号会让对账误以为有通道交易
        assertNull(data.get("tradeNo"), "余额支付没有第三方交易号");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, pay_method, trade_no FROM payment_record WHERE id = ?", paymentId);
        assertEquals("SUCCESS", row.get("status"), "出参说了 SUCCESS，库里就得真是 SUCCESS");
        assertEquals("BALANCE", row.get("pay_method"));
        assertNull(row.get("trade_no"), "库里也不能有自造流水号");
        assertEquals(6000L, balanceOf(patientId), "余额以库里为准");
    }

    @Test
    void j35_pay_returnsItemDetailFromJsonColumn() throws Exception {
        long patientId = createPatient("T15-乙");
        recharge(patientId, 5000);
        long paymentId = insertPendingPayment(patientId, 4000, TWO_ITEMS_JSON);

        Map<?, ?> data = expectData(payOn(paymentId));

        // PRD 116 行「缴费信息 — 缴费成功页，展示缴费明细」：明细必须在响应里，且不能被拆散
        List<?> items = (List<?>) data.get("items");
        assertEquals(2, items.size(), "两项都要在");
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertEquals("血常规", first.get("name"), "项目名要原样从 JSON 列里出来，含中文");
        assertEquals(1200L, ((Number) first.get("amountFen")).longValue());
        long sum = items.stream()
                .mapToLong(i -> ((Number) ((Map<?, ?>) i).get("amountFen")).longValue()).sum();
        assertEquals(4000L, sum, "明细合计必须等于单据金额，否则成功页就是一张对不上的账单");
    }

    @Test
    void j35_twoPayments_accumulateDeduction() throws Exception {
        long patientId = createPatient("T15-丙");
        recharge(patientId, 10000);
        long first = insertPendingPayment(patientId, 3000, ONE_ITEM_JSON);
        long second = insertPendingPayment(patientId, 2000, ONE_ITEM_JSON);

        expectData(payOn(first));
        Map<?, ?> afterSecond = expectData(payOn(second));

        assertEquals(5000L, ((Number) afterSecond.get("balanceFen")).longValue(),
                "两笔各扣一次：扣减若写成「读出来算完写回去」，第二笔会把第一笔覆盖掉");
        assertEquals(5000L, balanceOf(patientId));
    }

    @Test
    void j35_paidBillShowsUpInRecordsListWithPayMethod() throws Exception {
        long patientId = createPatient("T15-丁");
        recharge(patientId, 8000);
        long paymentId = insertPendingPayment(patientId, 3000, ONE_ITEM_JSON);
        expectData(payOn(paymentId));

        List<?> rows = expectDataList(getJson("/user/payments"));
        assertEquals(1, rows.size(), "缴费记录（卡片 514 行）里要能看到刚缴的那笔");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("SUCCESS", row.get("status"));
        assertEquals("BALANCE", row.get("payMethod"));
        assertEquals("T15-丁", row.get("patientName"));
        assertNull(row.get("items"), "记录列表不带明细——明细属于详情页，塞进列表只会让人漏看金额");
        assertNull(row.get("balanceFen"),
                "记录列表不给余额：每行重复一遍实时余额会被读成「当时扣完剩多少」，那是假账");
    }

    // ============================================================
    // J36 余额不足 → 被拒（卡片 516 行红线）
    // ============================================================

    @Test
    void j36_insufficientBalance_isRejectedAndChangesNothing() throws Exception {
        long patientId = createPatient("T15-戊");
        recharge(patientId, 1000);
        long paymentId = insertPendingPayment(patientId, 5000, ONE_ITEM_JSON);

        mockMvc.perform(post("/user/payments/" + paymentId + "/pay")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").value("余额不足"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, pay_method FROM payment_record WHERE id = ?", paymentId);
        assertEquals("PENDING", row.get("status"),
                "被拒的单据必须还站在待缴：状态推进与扣减同事务，扣不动就一起回滚");
        assertEquals("WECHAT", row.get("pay_method"), "支付方式也不许被改成 BALANCE");
        assertEquals(1000L, balanceOf(patientId), "拒绝不许动一分钱");
    }

    @Test
    void j36_zeroBalance_isRejected() throws Exception {
        long patientId = createPatient("T15-己");
        // 一分不充：新建就诊人的余额是 0（V4 的 DEFAULT 0）
        long paymentId = insertPendingPayment(patientId, 100, ONE_ITEM_JSON);

        mockMvc.perform(post("/user/payments/" + paymentId + "/pay")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(jsonPath("$.code").value(3002));
        assertEquals(0L, balanceOf(patientId));
        assertEquals("PENDING", statusOf(paymentId));
    }

    @Test
    void j36_concurrentPaymentsOnSharedBalance_onlyOneWins() throws Exception {
        long patientId = createPatient("T15-庚");
        recharge(patientId, 10000);
        long first = insertPendingPayment(patientId, 6000, ONE_ITEM_JSON);
        long second = insertPendingPayment(patientId, 6000, ONE_ITEM_JSON);

        List<Integer> codes = twoThreadPay(first, second);
        long successCount = codes.stream().filter(code -> code == 200).count();
        long rejectedCount = codes.stream().filter(code -> code == 3002).count();

        // 本卡最重要的一条断言：两笔并发各自读到 10000、各自觉得付得起时，
        // 只有把 balance_fen >= ? 写进 UPDATE 的 WHERE 才拦得住
        assertEquals(1L, successCount, "余额只够一笔：必须恰好一笔成功，实际：" + codes);
        assertEquals(1L, rejectedCount, "另一笔必须被 3002 拒绝，实际：" + codes);
        assertEquals(4000L, balanceOf(patientId),
                "赢了的那笔扣完，余额收在 4000；不许出现负数，也不许两笔都扣成 2000 之后又回滚成一笔");
        assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_record WHERE id IN (?, ?) AND status = 'SUCCESS'",
                        Integer.class, first, second),
                "库里也只能有一条 SUCCESS，账与钱要对得上");
    }

    // ============================================================
    // 幂等：一张单只能缴一次
    // ============================================================

    @Test
    void pay_alreadyPaidBill_returns3004AndDeductsOnce() throws Exception {
        long patientId = createPatient("T15-辛");
        recharge(patientId, 10000);
        long paymentId = insertPendingPayment(patientId, 4000, ONE_ITEM_JSON);
        expectData(payOn(paymentId));

        // 患者连点两次 / 两个设备各点一次：第二次绝不能报「支付失败」——那会诱导他再试一次
        mockMvc.perform(post("/user/payments/" + paymentId + "/pay")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3004));

        assertEquals(6000L, balanceOf(patientId), "重复缴费只扣一次钱");
    }

    @Test
    void pay_sendingAmountInBodyCannotChangeTheBill() throws Exception {
        long patientId = createPatient("T15-壬");
        recharge(patientId, 10000);
        long paymentId = insertPendingPayment(patientId, 4000, ONE_ITEM_JSON);

        // 端点没有 @RequestBody：客户端塞进来的金额整个被忽略，扣的还是单据上那 4000
        mockMvc.perform(post("/user/payments/" + paymentId + "/pay")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amountFen", 1, "patientId", 999999))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.amountFen").value(4000));

        assertEquals(6000L, balanceOf(patientId), "金额由服务端从单据上取，不由请求决定");
    }

    // ============================================================
    // 归属与角色
    // ============================================================

    @Test
    void paySomeoneElsesBill_returns5001AndChangesNothing() throws Exception {
        long patientId = createPatient("T15-癸");
        recharge(patientId, 10000);
        long paymentId = insertPendingPayment(patientId, 3000, ONE_ITEM_JSON);

        String intruder = newPatientToken("t15-intruder");
        mockMvc.perform(post("/user/payments/" + paymentId + "/pay")
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
        assertEquals("PENDING", statusOf(paymentId), "别人的单连状态都不许碰");

        mockMvc.perform(get("/user/payments/" + paymentId).header("Authorization", "Bearer " + intruder))
                .andExpect(jsonPath("$.code").value(5001));

        assertEquals(10000L, balanceOf(patientId), "越权请求既不改单也不扣钱");
    }

    @Test
    void missingBill_returns5001OnBothEndpoints() throws Exception {
        ensureLogin();
        mockMvc.perform(get("/user/payments/99999999").header("Authorization", "Bearer " + patientToken))
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(post("/user/payments/99999999/pay").header("Authorization", "Bearer " + patientToken))
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void staffAndAnonymousCannotReachPaymentEndpoints() throws Exception {
        long patientId = createPatient("T15-子");
        recharge(patientId, 5000);
        long paymentId = insertPendingPayment(patientId, 1000, ONE_ITEM_JSON);
        String staff = jwtTokenForAdmin();

        mockMvc.perform(get("/user/payments/pending").header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/payments").header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/user/payments/" + paymentId).header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/user/payments/" + paymentId + "/pay").header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/user/payments/pending")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/payments/" + paymentId + "/pay")).andExpect(status().isUnauthorized());

        assertEquals("PENDING", statusOf(paymentId), "员工与匿名都不得推进缴费状态");
        assertEquals(5000L, balanceOf(patientId), "也不得动余额");
    }

    // ============================================================
    // 待缴列表 / 详情 / id 量级 / 审计
    // ============================================================

    @Test
    void pendingList_onlyPendingRowsAndOwnPatients() throws Exception {
        long patientId = createPatient("T15-丑");
        recharge(patientId, 9000);
        long unpaid = insertPendingPayment(patientId, 2000, TWO_ITEMS_JSON);
        long paid = insertPendingPayment(patientId, 3000, ONE_ITEM_JSON);
        expectData(payOn(paid));

        List<?> rows = expectDataList(getJson("/user/payments/pending"));
        assertEquals(1, rows.size(), "缴掉的单要从待缴列表里消失");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals(unpaid, ((Number) row.get("id")).longValue());
        assertEquals("PENDING", row.get("status"));
        assertEquals(2, ((List<?>) row.get("items")).size(), "待缴列表要能显示这一单里有几项");
        assertEquals("T15-丑", row.get("patientName"));

        String other = newPatientToken("t15-other-list");
        mockMvc.perform(get("/user/payments/pending").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void detailCarriesItemsAndCurrentBalanceForTheConfirmPage() throws Exception {
        long patientId = createPatient("T15-寅");
        recharge(patientId, 7000);
        long paymentId = insertPendingPayment(patientId, 4000, TWO_ITEMS_JSON);

        Map<?, ?> data = expectData(getJson("/user/payments/" + paymentId));
        assertEquals(4000L, ((Number) data.get("amountFen")).longValue());
        assertEquals(7000L, ((Number) data.get("balanceFen")).longValue(),
                "确认页要在按下缴费之前就知道余额够不够——这是 J36 的 UI 入口");
        assertEquals(2, ((List<?>) data.get("items")).size());
        assertEquals("PENDING", data.get("status"));
        assertTrue(String.valueOf(data.get("orderNo")).startsWith("T15P"),
                "单号原样回显：缴费记录与 HIS 对账靠它，实际：" + data.get("orderNo"));
    }

    @Test
    void paymentIdStaysInsideJsSafeInteger() throws Exception {
        // T14 的 UI 验收撞出过雪花 id 越过 2^53 导致详情整页打不开。缴费单 id 从本卡开始
        // 真的会被客户端原样回传（POST /{id}/pay、GET /{id}），所以这条必须在 T15 钉住。
        long patientId = createPatient("T15-卯");
        recharge(patientId, 5000);
        long paymentId = insertPendingPayment(patientId, 1000, ONE_ITEM_JSON);

        Map<?, ?> data = expectData(getJson("/user/payments/" + paymentId));
        long returnedId = ((Number) data.get("id")).longValue();
        assertEquals(paymentId, returnedId);
        assertTrue(returnedId > 0 && returnedId <= 9007199254740991L,
                "id 必须落在 JS Number.MAX_SAFE_INTEGER 以内，否则小程序回传即丢精度，实际：" + returnedId);

        // 原样把 id 传回来还能缴成功，才是「客户端拿得到的 id」的完整闭环
        expectData(payOn(returnedId));
    }

    @Test
    void payWritesAuditInSameTransactionWithTargetId() throws Exception {
        long patientId = createPatient("T15-辰");
        recharge(patientId, 6000);
        long paymentId = insertPendingPayment(patientId, 2000, ONE_ITEM_JSON);
        expectData(payOn(paymentId));

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT operator_type, target_type, target_id FROM audit_log "
                        + "WHERE action = 'PAY_OUTPATIENT_PAYMENT' ORDER BY id DESC LIMIT 1");
        assertEquals("PATIENT", audit.get("operator_type"),
                "患者侧缴费也要留痕（沿用 T12 的多态主体）");
        assertEquals("payment_record", audit.get("target_type"));
        assertEquals(paymentId, ((Number) audit.get("target_id")).longValue(),
                "缴费单 id 在调用前就存在，所以 target_id 不是 NULL（与 T14 的 CREATE_RECHARGE 不同）");
    }

    // ============================================================
    // 辅助
    // ============================================================

    private String jwtTokenForAdmin() {
        return jwtUtil.generateToken(1L, "admin", "admin", List.of("dashboard", "finance"), List.of());
    }

    private MvcResult payOn(long paymentId) throws Exception {
        return mockMvc.perform(post("/user/payments/" + paymentId + "/pay")
                .header("Authorization", "Bearer " + patientToken)).andReturn();
    }

    private MvcResult getJson(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + patientToken)).andReturn();
    }

    private List<Integer> twoThreadPay(long firstId, long secondId) throws Exception {
        String token = patientToken;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = List.of(
                    pool.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return codeOf(mockMvc.perform(post("/user/payments/" + firstId + "/pay")
                                .header("Authorization", "Bearer " + token)).andReturn());
                    }),
                    pool.submit(() -> {
                        ready.countDown();
                        start.await(10, TimeUnit.SECONDS);
                        return codeOf(mockMvc.perform(post("/user/payments/" + secondId + "/pay")
                                .header("Authorization", "Bearer " + token)).andReturn());
                    }));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "两个线程没能同时就位");
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> future : futures) {
                codes.add(future.get(20, TimeUnit.SECONDS));
            }
            return codes;
        } catch (java.util.concurrent.ExecutionException | InterruptedException e) {
            throw new IllegalStateException("并发缴费没跑起来", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private int codeOf(MvcResult result) throws Exception {
        return ((Number) expectRoot(result).get("code")).intValue();
    }

    /**
     * 裸插一张待缴单。T15 没有建单的端点（见类注释），所以探针只能这么来。
     * 单号带 {@code T15P} 前缀，万一漏删也反查得到。
     */
    private long insertPendingPayment(long patientId, long amountFen, String itemsJson) {
        jdbcTemplate.update("INSERT INTO payment_record (order_no, patient_id, items, amount_fen, "
                        + "pay_method, status) VALUES (?, ?, ?, ?, 'WECHAT', 'PENDING')",
                "T15P" + String.format("%08d",
                        Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000)),
                patientId, itemsJson, amountFen);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM payment_record WHERE patient_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, patientId);
        if (id == null) {
            throw new IllegalStateException("探针待缴单没插进去，patient=" + patientId);
        }
        createdPaymentIds.add(id);
        return id;
    }

    private String statusOf(long paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payment_record WHERE id = ?", String.class, paymentId);
    }

    private long balanceOf(long patientId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT balance_fen FROM patient WHERE id = ?", Long.class, patientId);
        return value == null ? -1L : value;
    }

    /** 只有第一个用例需要显式登录；后续 createPatient 都会复用同一个 token。 */
    private void ensureLogin() throws Exception {
        if (patientToken == null) {
            newPatientToken("t15-owner");
        }
    }

    private long createPatient(String name) throws Exception {
        ensureLogin();
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

    /**
     * 新建一个患者账号。第一个建出来的当主用户（{@code patientToken} 为空时才占位），
     * 之后建的都是越权对照组，返回它们自己的 token。
     */
    private String newPatientToken(String tag) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", tag + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        createdUserIds.add(((Number) data.get("userId")).longValue());
        String token = String.valueOf(data.get("token"));
        if (patientToken == null) {
            patientToken = token;
            return token;
        }
        return token;
    }

    private void recharge(long patientId, long amountFen) throws Exception {
        expectData(mockMvc.perform(post("/user/recharges")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "amountFen", amountFen))))
                .andExpect(status().isOk())
                .andReturn());
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put("payment_record", count("SELECT COUNT(*) FROM payment_record"));
        snapshot.put("recharge_record", count("SELECT COUNT(*) FROM recharge_record"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("audit_payment", count(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'PAY_OUTPATIENT_PAYMENT'"));
        snapshot.put("balance_total", count("SELECT COALESCE(SUM(balance_fen), 0) FROM patient"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String randomCardNo() {
        return "T15" + String.format("%07d",
                Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
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
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }

    private List<?> expectDataList(MvcResult result) throws Exception {
        Map<?, ?> root = expectRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        return (List<?>) data;
    }
}
