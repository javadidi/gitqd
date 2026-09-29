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
import java.util.HashMap;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 门诊充值（T14）：J33 充值 → 余额增加 + 充值记录、J34 充值记录数据正确。
 *
 * <h2>本卡的第一条迁移，也是第一次给既有表加列</h2>
 * V4 给 {@code patient} 加了 {@code balance_fen}。跑这个测试类会真的把迁移应用到开发库
 * （Flyway 在 {@code @SpringBootTest} 启动时执行），所以这里同时承担一件事：
 * <b>验证种子回填后的余额是自洽的</b>（{@link #j33_seedBalanceMatchesSuccessRecharges}）。
 * 加列却不回填，种子里就会出现"有 100 元 SUCCESS 充值、余额却是 0"的矛盾演示数据。
 *
 * <h2>探针数据</h2>
 * 沿用 T08/T13 的口径：每个用例自己建就诊人（余额从 0 起，断言不用先减基线），
 * 收尾按 {@code patient_id} 精确删充值流水与就诊人，再比对五张表计数回到基线。
 * 充值单没有日期列可以做标记，所以完全靠 {@code patient_id} 归属来清——
 * 这也是为什么"建了就诊人必须登记进 createdPatientIds"是硬规矩（T08 踩过漏删）。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class RechargeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    private String patientToken;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM recharge_record WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        jdbcTemplate.update("DELETE FROM audit_log WHERE target_type = 'recharge_record' "
                + "AND action = 'CREATE_RECHARGE'");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(), "T14 只动自己造的探针行：五张表计数必须回到基线");
    }

    // ============================================================
    // J33 充值 → 就诊卡余额增加 + 充值记录
    // ============================================================

    @Test
    void j33_recharge_creditsBalanceAndWritesSuccessRecord() throws Exception {
        long patientId = createPatient("T14-甲");
        assertEquals(0L, balanceOf(patientId), "新建的就诊人余额从 0 起");

        Map<?, ?> data = expectData(recharge(patientId, 10000));

        assertEquals("SUCCESS", data.get("status"), "卡片 495 行：支付成功才算充值成功");
        assertEquals(10000L, ((Number) data.get("amountFen")).longValue());
        assertEquals(10000L, ((Number) data.get("balanceFen")).longValue(),
                "PRD 98 行「实时到账」——到账后的余额必须回给患者，否则患者无从判断钱有没有进卡");
        assertEquals("WECHAT", data.get("payMethod"), "卡片 494 行括号里写死微信支付");
        assertEquals("T14-甲", data.get("patientName"));
        assertNotNull(data.get("tradeNo"), "SUCCESS 的单必须有第三方流水号（mock 也是占位，见 RechargeService）");
        assertTrue(String.valueOf(data.get("orderNo")).matches("^CF\\d{8}-\\d{4}$"),
                "单号前缀 CF = SerialType.CF（充值单号），实际：" + data.get("orderNo"));

        assertEquals(10000L, balanceOf(patientId), "余额以库里为准");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, amount_fen, pay_method, trade_no, patient_id FROM recharge_record WHERE id = ?",
                ((Number) data.get("id")).longValue());
        assertEquals("SUCCESS", row.get("status"));
        assertEquals(10000L, ((Number) row.get("amount_fen")).longValue());
        assertEquals(1, ((Number) row.get("patient_id")).longValue() == patientId ? 1 : 0);
    }

    @Test
    void j33_twoRecharges_accumulateInsteadOfOverwrite() throws Exception {
        // addBalance 用的是 balance_fen = balance_fen + ?，不是"算好总数写回去"。
        // 这条测试就是那半个句号的证据：写成后者时第二次会覆盖第一次。
        long patientId = createPatient("T14-乙");
        recharge(patientId, 5000);
        recharge(patientId, 3000);

        assertEquals(8000L, balanceOf(patientId));
        assertEquals(2, count("SELECT COUNT(*) FROM recharge_record WHERE patient_id = ?", patientId));
    }

    @Test
    void j33_concurrentRecharges_loseNothing() throws Exception {
        long patientId = createPatient("T14-丙");
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<Integer>> codes = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                codes.add(pool.submit(() -> {
                    ready.countDown();
                    fire.await();
                    MvcResult result = mockMvc.perform(post("/user/recharges")
                                    .header("Authorization", "Bearer " + patientToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("patientId", patientId, "amountFen", 1000))))
                            .andReturn();
                    Map<?, ?> root = objectMapper.readValue(
                            result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
                    return ((Number) root.get("code")).intValue();
                }));
            }
            assertTrue(ready.await(15, TimeUnit.SECONDS), "线程应就位");
            fire.countDown();
            for (Future<Integer> future : codes) {
                assertEquals(200, future.get(30, TimeUnit.SECONDS).intValue(), "并发充值不该失败");
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(4000L, balanceOf(patientId),
                "四笔各 10 元必须累加成 40 元；读出来加完写回去的写法会在这里丢钱");
    }

    @Test
    void j33_rejectsNonPositiveAmounts() throws Exception {
        long patientId = createPatient("T14-丁");

        mockMvc.perform(post("/user/recharges")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "amountFen", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("大于 0")));

        mockMvc.perform(post("/user/recharges")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "amountFen", -100))))
                .andExpect(status().isBadRequest());

        assertEquals(0L, balanceOf(patientId), "被拒的请求不得动余额");
        assertEquals(0, count("SELECT COUNT(*) FROM recharge_record WHERE patient_id = ?", patientId),
                "也不该留下一张废单");
    }

    @Test
    void j33_ignoresPayMethodSentByClient() throws Exception {
        // 入参类里根本没有 payMethod 字段：客户端声明一个系统不支持的支付方式不该被记进财务表。
        long patientId = createPatient("T14-戊");
        Map<String, Object> body = new HashMap<>();
        body.put("patientId", patientId);
        body.put("amountFen", 2000L);
        body.put("payMethod", "CASH");

        Map<?, ?> data = expectData(rechargeWith(body));
        assertEquals("WECHAT", data.get("payMethod"));
        assertEquals("WECHAT", jdbcTemplate.queryForObject(
                "SELECT pay_method FROM recharge_record WHERE id = ?", String.class,
                ((Number) data.get("id")).longValue()));
    }

    @Test
    void j33_rechargingSomeoneElsesPatient_returns1003AndWritesNothing() throws Exception {
        long foreignPatient = createPatient("T14-他");
        String intruder = newPatientToken("t14-intruder");

        mockMvc.perform(post("/user/recharges")
                        .header("Authorization", "Bearer " + intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", foreignPatient, "amountFen", 5000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        assertEquals(0L, balanceOf(foreignPatient), "别人的充值请求一分也加不进这张卡");
        assertEquals(0, count("SELECT COUNT(*) FROM recharge_record WHERE patient_id = ?", foreignPatient),
                "也不该留下一张单——归属判定和加钱在同一条 UPDATE 里，回滚时一起消失");
    }

    @Test
    void j33_writesAuditAsPatientAction() throws Exception {
        long patientId = createPatient("T14-己");
        recharge(patientId, 6600);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT operator_id, operator_type, target_type, target_id, detail FROM audit_log "
                        + "WHERE action = 'CREATE_RECHARGE' ORDER BY id DESC LIMIT 1");
        assertEquals("PATIENT", audit.get("operator_type"));
        assertEquals("recharge_record", audit.get("target_type"));
        assertNull(audit.get("target_id"), "创建时还没有单号可指，切面只认 @AuditTarget 标的 Long 入参");
        assertTrue(String.valueOf(audit.get("detail")).contains("6600"),
                "detail 要能还原充了多少钱，实际：" + audit.get("detail"));
    }

    @Test
    void j33_seedBalanceMatchesSuccessRecharges() {
        // 种子自洽性：V4 加列之后，如果 seed.sql 的回填漏了或口径漂了，这条会红。
        // 种子里门诊 SUCCESS 充值只有 SEED-RC-0001（patient 1，10000 分）；
        // SEED-RC-0002 是 PENDING 不算，SEED-RC-0003 是住院充值（patient_id 为 NULL）。
        assertEquals(10000L, balanceOf(1L), "seed.sql 9b 节的回填口径");
        assertEquals(0L, balanceOf(5L), "PENDING 的那笔不该到账");
    }

    // ============================================================
    // J34 充值记录 → 数据正确
    // ============================================================

    @Test
    void j34_list_returnsOwnRechargesNewestFirstWithPatientNames() throws Exception {
        long patientA = createPatient("T14-庚");
        long patientB = createPatient("T14-辛");
        recharge(patientA, 1000);
        recharge(patientB, 2000);
        recharge(patientA, 3000);

        List<?> rows = (List<?>) expectRoot(mockMvc.perform(get("/user/recharges")
                .header("Authorization", "Bearer " + patientToken)).andReturn()).get("data");

        assertEquals(3, rows.size(), "本人两个就诊人的流水都要在");
        Map<?, ?> first = (Map<?, ?>) rows.get(0);
        assertEquals(3000L, ((Number) first.get("amountFen")).longValue(), "按 id 倒序 = 最近一笔在前");
        assertEquals("T14-庚", first.get("patientName"), "列表要能看出是哪个就诊人充的");
        assertEquals("SUCCESS", first.get("status"));
        assertNotNull(first.get("createdAt"));
        assertNull(first.get("inpatientId"),
                "本卡只做门诊充值，返回一个恒为 NULL 的住院字段只会让人以为住院流水也在这里");
    }

    @Test
    void j34_list_excludesOtherUsersAndEmptyIsArray() throws Exception {
        long patientId = createPatient("T14-壬");
        recharge(patientId, 1000);
        String other = newPatientToken("t14-other");

        List<?> rows = (List<?>) expectRoot(mockMvc.perform(get("/user/recharges")
                .header("Authorization", "Bearer " + other)).andReturn()).get("data");
        assertEquals(List.of(), rows, "别人有流水也不该在我这里出现；且空是 [] 不是 null");

        assertEquals(1, count("SELECT COUNT(*) FROM recharge_record WHERE patient_id = ?", patientId),
                "读别人的列表不该改动任何数据");
    }

    @Test
    void j34_detail_returnsOwnAndRejectsOthers() throws Exception {
        long patientId = createPatient("T14-癸");
        Map<?, ?> created = expectData(recharge(patientId, 4400));
        long rechargeId = ((Number) created.get("id")).longValue();

        Map<?, ?> data = expectData(mockMvc.perform(get("/user/recharges/" + rechargeId)
                .header("Authorization", "Bearer " + patientToken)).andReturn());
        assertEquals(created.get("orderNo"), data.get("orderNo"));
        assertEquals(4400L, ((Number) data.get("amountFen")).longValue());
        assertEquals("T14-癸", data.get("patientName"));

        String intruder = newPatientToken("t14-intruder3");
        mockMvc.perform(get("/user/recharges/" + rechargeId)
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isOk())
                // 别人的单与没这张单同码，不给枚举机会
                .andExpect(jsonPath("$.code").value(5001));
    }

    /**
     * id 必须落在 JS 安全整数以内——T14 UI 验收第 10 步就是被这条撞崩的。
     *
     * <p>{@code recharge_record} 没有 {@code deleted} 列（财务流水不做软删），实体因此不继承
     * {@code BaseEntity}；而 {@code @TableId(type = IdType.AUTO)} 一旦缺失，MyBatis-Plus 就退回默认的
     * 雪花策略，id 变成 2.1e18 量级。小程序 {@code JSON.parse} 会把尾数舍掉，患者点进账单详情
     * 只剩 5001「数据不存在」。MockMvc 用 Java long 解 JSON，看不见这次精度损失，所以只能显式断言。
     */
    @Test
    void j33_recordIdStaysInsideJsSafeInteger() throws Exception {
        long patientId = createPatient("T14-安全整数");
        Map<?, ?> created = expectData(recharge(patientId, 1200));
        long rechargeId = ((Number) created.get("id")).longValue();

        assertTrue(rechargeId > 0,
                "AUTO 策略下 id 要由 JDBC 生成键回写进实体（tradeNo 依赖它），实际：" + rechargeId);
        assertTrue(rechargeId <= 9007199254740991L,
                "id 必须在 JS Number.MAX_SAFE_INTEGER（2^53-1）以内，否则客户端回传即丢精度，实际：" + rechargeId);

        Map<?, ?> detail = expectData(mockMvc.perform(get("/user/recharges/" + rechargeId)
                .header("Authorization", "Bearer " + patientToken)).andReturn());
        assertEquals(rechargeId, ((Number) detail.get("id")).longValue(),
                "闭环：客户端原样把 id 传回来，必须还能查到同一笔");
    }

    @Test
    void staffAndAnonymousCannotReachRechargeEndpoints() throws Exception {
        long patientId = createPatient("T14-子");
        Map<?, ?> created = expectData(recharge(patientId, 1000));
        long rechargeId = ((Number) created.get("id")).longValue();
        String staff = jwtTokenForAdmin();

        mockMvc.perform(post("/user/recharges").header("Authorization", "Bearer " + staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "amountFen", 100))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/recharges").header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/user/recharges/" + rechargeId).header("Authorization", "Bearer " + staff))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/user/recharges").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("patientId", patientId, "amountFen", 100))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/recharges")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/recharges/" + rechargeId)).andExpect(status().isUnauthorized());

        assertEquals(1000L, balanceOf(patientId), "越权与匿名请求不得动余额");
    }

    // ============================================================
    // 辅助
    // ============================================================

    private String jwtTokenForAdmin() {
        return jwtUtil.generateToken(1L, "admin", "admin",
                List.of("dashboard", "schedule", "appointment"), List.of());
    }

    private MvcResult recharge(long patientId, long amountFen) throws Exception {
        return rechargeWith(Map.of("patientId", patientId, "amountFen", amountFen));
    }

    private MvcResult rechargeWith(Map<?, ?> body) throws Exception {
        return mockMvc.perform(post("/user/recharges")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private long createPatient(String name) throws Exception {
        if (patientToken == null) {
            patientToken = newPatientToken("t14-owner");
        }
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

    private long balanceOf(long patientId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT balance_fen FROM patient WHERE id = ?", Long.class, patientId);
        return value == null ? -1L : value;
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
        snapshot.put("recharge_record", count("SELECT COUNT(*) FROM recharge_record"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("audit_recharge", count(
                "SELECT COUNT(*) FROM audit_log WHERE target_type = 'recharge_record'"));
        snapshot.put("balance_total", count("SELECT COALESCE(SUM(balance_fen), 0) FROM patient"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String randomCardNo() {
        return "T14" + String.format("%07d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
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
}
