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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端费用管理（T26）：J57 消费记录 → 数据正确、J58 退款审核 → 状态更新。
 *
 * <h2>本卡接住的是前面几张卡挂下来的账</h2>
 * T13 的退号与 T25 的停诊都只把退款单挂到 {@code PENDING} 就停手
 * （{@code RefundTicketService} 的类注释写明"真实出款是二期的事"）。
 * 那批单子在本卡之前<b>没有任何人能处理</b>——J58 就是它们的出口。
 * 所以 {@link #j58_aRefundIssuedByPatientCancelIsReviewableEndToEnd} 是本卡最重要的一条：
 * 它跑完整链路（患者退号 → 后台看见 PENDING → 审核通过 → 后台变成 APPROVED），
 * 证明挂单方与本卡的审单方用的是同一张表、同一个 {@code related_type} 词表。
 *
 * <h2>探针一律裸插 SQL</h2>
 * 消费/充值/配送三族流水走真实链路要拉进支付通道（T12 的 mock 回调、T14 的余额扣减），
 * 而本卡测的是<b>后台怎么读这四张流水表</b>，流水行只要长得像真的就够
 * （T15 的探针同一条理由）。退款单是唯一例外：J58 那条端到端故意走 {@code /user/appointments/{id}/cancel}，
 * 因为"挂单→审单"的口径衔接正是被测对象本身。
 *
 * <h2>清理按单号前缀，审计按水位线</h2>
 * 与 T25 的教训一致：清理谓词必须只覆盖本卡探针。库里现存 21 条退款单
 * （19 条 PENDING，多数是前面几张卡的测试遗留），一条都不许被我的 DELETE 扫掉。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class FinanceIntegrationTest {

    private static final int PROBE_YEAR_OFFSET = 5;
    private static final String PAYMENT_PREFIX = "T26P";
    private static final String RECHARGE_PREFIX = "T26R";
    private static final String REFUND_PREFIX = "T26K";
    private static final String APPOINTMENT_PREFIX = "T26A";
    private static final String INPATIENT_NO_PREFIX = "T26ZY";
    private static final String RECIPIENT_NAME = "T26 探针收件人";
    private static final String DOCTOR_NAME = "T26 探针医生";
    private static final String ADMIN_USERNAME = "admin";

    /** 五个列表都声明不接筛选参数，这条测试拿这三个名字去试（PRD §4.4 从未要求）。 */
    private static final String FILTER_PROBE = "?status=PENDING&patientId=1&dateFrom=2020-01-01";

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
    void setUp() {
        countsBefore = snapshotCounts();
        Long maxAuditId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM audit_log", Long.class);
        auditIdFloor = maxAuditId == null ? 0L : maxAuditId;
        adminToken = staffToken(1L, "admin");
        doctorToken = staffToken(3L, "doctor");
        nurseToken = staffToken(4L, "nurse");
    }

    @AfterEach
    void cleanupAndAssertSeedUntouched() {
        jdbcTemplate.update("DELETE FROM case_delivery WHERE recipient_name = ?", RECIPIENT_NAME);
        jdbcTemplate.update("DELETE FROM payment_record WHERE order_no LIKE ?", PAYMENT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM recharge_record WHERE order_no LIKE ?", RECHARGE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM refund_record WHERE order_no LIKE ?", REFUND_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM refund_record WHERE related_type = 'APPOINTMENT' "
                + "AND related_id IN (SELECT id FROM appointment WHERE order_no LIKE ?)",
                APPOINTMENT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM appointment WHERE order_no LIKE ?", APPOINTMENT_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL ? YEAR)",
                PROBE_YEAR_OFFSET);
        jdbcTemplate.update("DELETE FROM doctor WHERE name = ?", DOCTOR_NAME);
        jdbcTemplate.update("DELETE FROM inpatient WHERE inpatient_no LIKE ?", INPATIENT_NO_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM patient WHERE user_id IN (" + placeholders() + ")", userIdArgs());
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM audit_log WHERE id > ?", auditIdFloor);
        assertEquals(countsBefore, snapshotCounts(),
                "探针行必须清干净：seed 的 4 笔消费 / 3 笔充值 / 21 张退款单一张都不能少，"
                        + "别的卡遗留的退款单也不许被我扫掉");
    }

    // ============================================================
    // J57 消费记录 → 数据正确
    // ============================================================

    @Test
    void j57_paymentListResolvesPatientNamesAndCarriesNoItems() throws Exception {
        List<Map<?, ?>> rows = expectList(admin(get("/admin/payments")));

        assertTrue(rows.size() >= 4, "seed 有 4 笔门诊消费，后台列表至少该看到这些，实际 " + rows.size());
        Map<?, ?> first = rows.get(0);
        for (String key : List.of("id", "orderNo", "patientId", "patientName", "cardNo",
                "amountFen", "payMethod", "status", "createdAt")) {
            assertTrue(first.containsKey(key), "列表行缺字段 " + key + "：" + first.keySet());
        }
        assertNotNull(first.get("patientName"), "就诊人姓名必须解析出来（payment_record 只有 patient_id）");
        assertFalse(first.containsKey("items"),
                "列表不逐行反解 JSON 明细（PRD 369 行只要「展示门诊消费记录」），NON_NULL 让这一键整个消失");
    }

    @Test
    void j57_paymentDetailCarriesItemizedAmounts() throws Exception {
        long patientId = seedPatient("消费明细甲");
        long paymentId = seedPayment(patientId, "DETAIL", 12345L);

        Map<?, ?> detail = expectData(admin(get("/admin/payments/" + paymentId)));
        assertEquals(PAYMENT_PREFIX + "DETAIL", detail.get("orderNo"));
        assertEquals(12345, ((Number) detail.get("amountFen")).intValue());

        List<?> items = (List<?>) detail.get("items");
        assertNotNull(items, "PRD 370 行「订单详情 — 查看消费明细」，详情必须带明细");
        assertEquals(2, items.size());
        Map<?, ?> head = (Map<?, ?>) items.get(0);
        assertEquals("探针项目一", head.get("name"));
        assertEquals(7000, ((Number) head.get("amountFen")).intValue(),
                "明细里的金额是强类型 bean 属性，裁剪层才找得到它（见 AdminPaymentResponse 的类注释）");
    }

    @Test
    void j57_rechargeListsAreSplitByInpatientIdAndDisjoint() throws Exception {
        long patientId = seedPatient("充值拆分甲");
        long inpatientId = seedInpatient("T26ZY-IP-A");
        long outpatientRecharge = seedRecharge(RECHARGE_PREFIX + "OP", patientId, null);
        long inpatientRecharge = seedRecharge(RECHARGE_PREFIX + "IP", null, inpatientId);

        List<Map<?, ?>> outpatient = expectList(admin(get("/admin/recharges")));
        List<Map<?, ?>> inpatient = expectList(admin(get("/admin/inpatient-recharges")));

        assertTrue(containsId(outpatient, outpatientRecharge), "门诊充值列表该有那条 patient_id 行");
        assertTrue(containsId(inpatient, inpatientRecharge), "住院充值列表该有那条 inpatient_id 行");
        for (Map<?, ?> row : outpatient) {
            assertFalse(row.containsKey("inpatientId"), "门诊那一页不该出现住院主体：" + row.keySet());
        }
        for (Map<?, ?> row : inpatient) {
            assertTrue(row.containsKey("inpatientNo"), "住院那一页必须以住院人为主语");
            assertFalse(row.containsKey("patientName"),
                    "T23 的 SEED-RC-0003 那种 patient_id=NULL 的行是真的，硬编一个就诊人名就是假账");
        }
        assertEquals(0, outpatient.stream().filter(row -> containsId(inpatient, number(row.get("id"))))
                .count(), "两把列表必须互不相交：同一笔钱不能既算门诊又算住院");
    }

    @Test
    void j57_rechargeDetailRejectsTheOtherSectionsId() throws Exception {
        long patientId = seedPatient("跨栏甲");
        long inpatientId = seedInpatient("T26ZY-IP-B");
        long outpatientRecharge = seedRecharge(RECHARGE_PREFIX + "OP2", patientId, null);
        long inpatientRecharge = seedRecharge(RECHARGE_PREFIX + "IP2", null, inpatientId);

        // 同表两把端点：各认自己那一族，误传 id 回 5001，不"反正同表就读给你"。
        expectCode(admin(get("/admin/recharges/" + inpatientRecharge)), 5001);
        expectCode(admin(get("/admin/inpatient-recharges/" + outpatientRecharge)), 5001);
        // 自己那族读得到
        assertEquals(outpatientRecharge, number(
                expectData(admin(get("/admin/recharges/" + outpatientRecharge))).get("id")));
    }

    @Test
    void j57_caseDeliveryListUsesInpatientAsSubject() throws Exception {
        long inpatientId = seedInpatient("T26ZY-IP-C");
        long deliveryId = seedCaseDelivery(inpatientId);

        List<Map<?, ?>> rows = expectList(admin(get("/admin/case-deliveries")));
        Map<?, ?> mine = rows.stream()
                .filter(row -> deliveryId == number(row.get("id")))
                .findFirst().orElseThrow();
        assertEquals("T26ZY-IP-C", mine.get("inpatientNo"), "住院号要能从 inpatient 表解析出来");
        assertEquals("探针科室", mine.get("department"));
        assertEquals("09 层 03 床", mine.get("bedNo"));
        assertEquals(RECIPIENT_NAME, mine.get("recipientName"));
        assertEquals("PENDING", mine.get("status"));
        assertFalse(mine.containsKey("trackingNo"),
                "PRD 386 行「物流状态」后半句没有数据源：全系统无人写 tracking_no，后端不编运单号");
        assertFalse(mine.containsKey("idCardPhoto"), "T23 定过「不传证件」，那一列首版无人写");

        Map<?, ?> detail = expectData(admin(get("/admin/case-deliveries/" + deliveryId)));
        assertEquals(mine.get("inpatientName"), detail.get("inpatientName"),
                "列表与详情必须同源，否则管理员在两页看到两个名字");
    }

    @Test
    void j57_softDeletedPatientStillHasNameOnMoneyRows() throws Exception {
        // 流水表按 V1:138 的注释「财务单据，不软删」——只增不删；而 T08 允许本人删就诊人。
        // 于是"钱还在、人已被删"是正常状态，默认读法（@TableLogic 追加 deleted=0）会让这批钱没了主人。
        long patientId = seedPatient("已删卡主");
        long paymentId = seedPayment(patientId, "GHOST", 3000L);
        mockMvc.perform(delete("/user/patients/" + patientId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM patient WHERE id = ?", Integer.class, patientId),
                "前置条件：这个人现在确实是软删状态");

        Map<?, ?> row = expectList(admin(get("/admin/payments"))).stream()
                .filter(item -> paymentId == number(item.get("id")))
                .findFirst().orElseThrow();
        assertEquals("已删卡主", row.get("patientName"),
                "被删的人的钱还在账上，姓名必须仍然解析得出来（T25 停诊那批同一条教训）");

        Map<?, ?> detail = expectData(admin(get("/admin/payments/" + paymentId)));
        assertEquals("已删卡主", detail.get("patientName"));
    }

    @Test
    void j57_missingAndForeignIdsReturnBusinessCodesOnEveryDetail() throws Exception {
        // 五把详情端点的"没有这一行"口径必须一致：全部 5001，不给 500 兜底
        expectCode(admin(get("/admin/payments/99999999")), 5001);
        expectCode(admin(get("/admin/recharges/99999999")), 5001);
        expectCode(admin(get("/admin/inpatient-recharges/99999999")), 5001);
        expectCode(admin(get("/admin/case-deliveries/99999999")), 5001);
        expectCode(admin(get("/admin/refunds/99999999")), 5001);
    }

    @Test
    void j57_nurseSeesNoMoneyDigitsAnywhereInFinanceEndpoints() throws Exception {
        long patientId = seedPatient("护士看不到甲");
        long inpatientId = seedInpatient("T26ZY-IP-D");
        long paymentId = seedPayment(patientId, "MASK", 888888L);
        long rechargeId = seedRecharge(RECHARGE_PREFIX + "MASK", patientId, null);
        long refundId = seedRefund(REFUND_PREFIX + "MASK", "APPOINTMENT", paymentId, 777777L, "PENDING");

        for (String path : List.of("/admin/payments", "/admin/recharges", "/admin/inpatient-recharges",
                "/admin/case-deliveries", "/admin/refunds",
                "/admin/payments/" + paymentId, "/admin/recharges/" + rechargeId,
                "/admin/refunds/" + refundId)) {
            String body = mockMvc.perform(get(path).header("Authorization", "Bearer " + nurseToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            for (String leaked : List.of("888888", "777777")) {
                assertFalse(body.contains(leaked),
                        "护士角色的响应里出现了金额数字 " + leaked + "（路径 " + path + "）：" + body);
            }
        }

        // 详情那条的 items[].amountFen 必须是 null——这条是"用强类型而不是裸 JSON"换来的
        Map<?, ?> root = expectRoot(mockMvc.perform(get("/admin/payments/" + paymentId)
                .header("Authorization", "Bearer " + nurseToken)).andReturn());
        Map<?, ?> detail = (Map<?, ?>) root.get("data");
        List<?> items = (List<?>) detail.get("items");
        assertEquals(2, items.size(), "明细的行数不是金额，护士照样该看到几项");
        for (Object element : items) {
            Map<?, ?> item = (Map<?, ?>) element;
            assertTrue(item.containsKey("amountFen") && item.get("amountFen") == null,
                    "items[].amountFen 应被裁剪成 null：" + item);
            assertNotNull(item.get("name"), "项目名不是金额，必须保留");
        }
    }

    @Test
    void j57_listsAcceptNoFilterParamsBecausePrd44NeverAskedForThem() throws Exception {
        long patientId = seedPatient("无筛选甲");
        seedPayment(patientId, "NOFILTER", 4000L);

        for (String path : List.of("/admin/payments", "/admin/recharges", "/admin/inpatient-recharges",
                "/admin/case-deliveries", "/admin/refunds")) {
            int plain = expectList(admin(get(path))).size();
            int withProbe = expectList(admin(get(path + FILTER_PROBE))).size();
            assertEquals(plain, withProbe,
                    path + " 收到未知 query 参数时行为不变：PRD §4.4 全章没有「筛选」二字，"
                            + "对照 §4.3.1 的 347 行是明写筛选的，作者会筛的时候会说");
        }
    }

    @Test
    void t26EndpointsAreExactlyWhatTheCardNamed() {
        TreeSet<String> found = new TreeSet<>();
        TreeSet<String> forbidden = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                if (pattern.startsWith("/admin/payments") || pattern.startsWith("/admin/recharges")
                        || pattern.startsWith("/admin/inpatient-recharges")
                        || pattern.startsWith("/admin/case-deliveries")
                        || pattern.startsWith("/admin/refunds")) {
                    found.add(verb + " " + pattern);
                }
                // 卡片 719 行「住院消费记录/详情」：V1 十七张表里没有一张装得下它。
                if (pattern.contains("inpatient-consume") || pattern.contains("inpatient-payment")) {
                    forbidden.add(verb + " " + pattern);
                }
                // 审核两把是唯一写口；改判/撤销/直接删单都不在规格里。
                if ((pattern.startsWith("/admin/refunds") && pattern.endsWith("/revoke"))
                        || pattern.startsWith("/admin/case-deliveries/") && pattern.contains("/ship")) {
                    forbidden.add(verb + " " + pattern);
                }
            }
        });

        assertEquals("[GET /admin/case-deliveries, GET /admin/case-deliveries/{id}, "
                        + "GET /admin/inpatient-recharges, GET /admin/inpatient-recharges/{id}, "
                        + "GET /admin/payments, GET /admin/payments/{id}, "
                        + "GET /admin/recharges, GET /admin/recharges/{id}, "
                        + "GET /admin/refunds, GET /admin/refunds/{id}, "
                        + "POST /admin/refunds/{id}/approve, POST /admin/refunds/{id}/reject]",
                found.toString(),
                "五组记录各两把（列表/详情）= 10，退款多两把审核 = 12；"
                        + "卡片 719 行的住院消费一把都不开");
        assertEquals("[]", forbidden.toString(),
                "住院消费无落点（V1 无表，与 T23 小程序侧同一结论）；退款只有 PENDING→APPROVED/REJECTED 两条边，"
                        + "规格没写过改判，病案配送也没给后台发货入口");
    }

    // ============================================================
    // J58 退款审核 → 状态更新
    // ============================================================

    @Test
    void j58_approveMovesPendingToApprovedAndStampsReviewer() throws Exception {
        long refundId = seedRefund(REFUND_PREFIX + "AP", "APPOINTMENT", 4242L, 5000L, "PENDING");

        Map<?, ?> data = expectData(admin(post("/admin/refunds/" + refundId + "/approve")));
        assertEquals("APPROVED", data.get("status"));
        assertEquals(1, ((Number) data.get("reviewerId")).intValue(), "admin token 的 adminId 是 1");
        assertEquals(ADMIN_USERNAME, data.get("reviewerName"), "admin 表没有姓名列，审核人只能是 username");

        Map<?, ?> row = jdbcTemplate.queryForMap(
                "SELECT status, reviewer_id FROM refund_record WHERE id = ?", refundId);
        assertEquals("APPROVED", row.get("status"));
        assertEquals(1L, ((Number) row.get("reviewer_id")).longValue());

        // 审核完立刻能在列表与详情读到新状态（前端不需要再发一次请求才知道成功）
        Map<?, ?> reread = expectData(admin(get("/admin/refunds/" + refundId)));
        assertEquals("APPROVED", reread.get("status"));
    }

    @Test
    void j58_rejectMovesPendingToRejected() throws Exception {
        long refundId = seedRefund(REFUND_PREFIX + "RE", "PAYMENT", 4242L, 6000L, "PENDING");

        Map<?, ?> data = expectData(admin(post("/admin/refunds/" + refundId + "/reject")));
        assertEquals("REJECTED", data.get("status"));
        assertEquals("REJECTED", jdbcTemplate.queryForObject(
                "SELECT status FROM refund_record WHERE id = ?", String.class, refundId));
    }

    @Test
    void j58_approvingARefundMovesNoMoney() throws Exception {
        // J58 的判定口径是「状态更新」四个字，PRD 390 行原文也只有「支持审核通过/拒绝」。
        // 真实出款属 PRD 136-142 行「在线退款」那节（141 行「原路返回微信钱包」），依赖微信退款 API = 二期。
        long patientId = seedPatient("审核不动钱甲");
        jdbcTemplate.update("UPDATE patient SET balance_fen = 30000 WHERE id = ?", patientId);
        long paymentId = seedPayment(patientId, "MONEY", 9000L);
        long refundId = seedRefund(REFUND_PREFIX + "MONEY", "PAYMENT", paymentId, 9000L, "PENDING");

        long balanceBefore = balanceOf(patientId);
        long paymentSumBefore = sum("payment_record");
        long rechargeSumBefore = sum("recharge_record");

        expectData(admin(post("/admin/refunds/" + refundId + "/approve")));

        assertEquals(balanceBefore, balanceOf(patientId),
                "审核通过不许动就诊卡余额——动了就是本卡在无微信退款通道的情况下凭空出钱");
        assertEquals(paymentSumBefore, sum("payment_record"), "缴费流水不许多一行也不许改金额");
        assertEquals(rechargeSumBefore, sum("recharge_record"), "充值流水同理");
        assertEquals(9000L, jdbcTemplate.queryForObject(
                "SELECT amount_fen FROM refund_record WHERE id = ?", Long.class, refundId),
                "退款金额必须在挂单时就定死，审核环节改不了（本卡不收任何金额入参）");
        assertEquals("SUCCESS", jdbcTemplate.queryForObject(
                "SELECT status FROM payment_record WHERE id = ?", String.class, paymentId),
                "原缴费单不被反写：V1:163 只有 PENDING/SUCCESS/REFUNDED，"
                        + "把它改成 REFUNDED 就等于宣称钱已到账，而本卡没有出款通道");
    }

    @Test
    void j58_secondReviewOnTheSameTicketReturns3006() throws Exception {
        long refundId = seedRefund(REFUND_PREFIX + "TWICE", "APPOINTMENT", 4242L, 5000L, "PENDING");

        expectCode(admin(post("/admin/refunds/" + refundId + "/approve")), 200);
        // 第二个人再点通过 / 换个动作点拒绝，都只该被告知"这张已经有人表过态了"
        expectCode(admin(post("/admin/refunds/" + refundId + "/approve")), 3006);
        expectCode(admin(post("/admin/refunds/" + refundId + "/reject")), 3006);

        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT status FROM refund_record WHERE id = ?", String.class, refundId),
                "3006 那两次一行都不该改（WHERE status='PENDING' 影响 0 行）");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT reviewer_id FROM refund_record WHERE id = ?", Long.class, refundId),
                "审核人也不能被第二次点击覆盖——那会让流水说不清是谁定的");
    }

    @Test
    void j58_reviewerIdIsNotAcceptableAsRequestInput() throws Exception {
        long refundId = seedRefund(REFUND_PREFIX + "SPOOF", "RECHARGE", 4242L, 5000L, "PENDING");

        // 假装有人往 body 与 query 里塞别人的 adminId：两个口子都不该生效
        mockMvc.perform(post("/admin/refunds/" + refundId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reviewerId", 999, "amountFen", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT reviewer_id FROM refund_record WHERE id = ?", Long.class, refundId),
                "reviewer_id 只能来自 token（SecurityUtils.currentAdminId）；"
                        + "接受参数传入等于任何人都能把审核记录挂到别的管理员名下");
        assertEquals(5000L, jdbcTemplate.queryForObject(
                "SELECT amount_fen FROM refund_record WHERE id = ?", Long.class, refundId),
                "金额也不能被 body 里的 amountFen 改写");
    }

    @Test
    void j58_onlyRolesWithApproveRefundCapMayReview() throws Exception {
        long refundId = seedRefund(REFUND_PREFIX + "CAP", "APPOINTMENT", 4242L, 5000L, "PENDING");

        for (String token : List.of(doctorToken, nurseToken)) {
            for (String verb : List.of("approve", "reject")) {
                mockMvc.perform(post("/admin/refunds/" + refundId + "/" + verb)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(4001));
            }
        }
        Map<?, ?> row = jdbcTemplate.queryForMap(
                "SELECT status, reviewer_id FROM refund_record WHERE id = ?", refundId);
        assertEquals("PENDING", row.get("status"), "RequireCap 切面在事务之前，被挡的请求一行都不该改");
        assertNull(row.get("reviewer_id"));

        // 读端点不要能力：护士有权看见退款进度，只是看不见金额
        expectCode(admin(get("/admin/refunds"), nurseToken), 200);
    }

    @Test
    void j58_reviewWritesAuditRowAgainstRefundRecordTargetType() throws Exception {
        // 两张单，分别审一次：一张单上连点两个动作会被状态机挡成 3006（这正是 j58_secondReview 要证的），
        // 所以这里不能拿同一张单去凑两个 action。
        long approved = seedRefund(REFUND_PREFIX + "AUDIT-A", "APPOINTMENT", 4242L, 5000L, "PENDING");
        long rejected = seedRefund(REFUND_PREFIX + "AUDIT-R", "APPOINTMENT", 4343L, 5000L, "PENDING");

        expectData(admin(post("/admin/refunds/" + approved + "/approve")));
        expectData(admin(post("/admin/refunds/" + rejected + "/reject")));

        // 按 action + target_type + target_id 三元组精确计数：
        // T04 的靶接口早就占用过 APPROVE_REFUND 这个 action 名，但它的 target_type 是
        // payment_record，所以按 action 名数总数会把它也算进来（WORK_LOG 里 T04 的审计基线就写明过）。
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'APPROVE_REFUND' "
                + "AND target_type = 'refund_record' AND target_id = ?", approved));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'REJECT_REFUND' "
                + "AND target_type = 'refund_record' AND target_id = ?", rejected));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'APPROVE_REFUND' "
                + "AND target_type = 'refund_record' AND target_id = ? AND operator_type = 'ADMIN' "
                + "AND operator_id = 1", approved), "审核留痕的操作人必须是 token 里那个 admin");
    }

    @Test
    void j58_refundListResolvesRelatedOrderNoAndDegradesHonestly() throws Exception {
        long patientId = seedPatient("关联单号甲");
        long paymentId = seedPayment(patientId, "REL", 5000L);
        long resolved = seedRefund(REFUND_PREFIX + "REL-P", "PAYMENT", paymentId, 5000L, "PENDING");
        long rechargeId = seedRecharge(RECHARGE_PREFIX + "REL", patientId, null);
        long rechargeRefund = seedRefund(REFUND_PREFIX + "REL-R", "RECHARGE", rechargeId, 2000L, "PENDING");
        long orphan = seedRefund(REFUND_PREFIX + "ORPHAN", "APPOINTMENT", 987654321L, 1000L, "PENDING");

        List<Map<?, ?>> rows = expectList(admin(get("/admin/refunds")));
        Map<?, ?> paymentRow = findById(rows, resolved);
        assertEquals(PAYMENT_PREFIX + "REL", paymentRow.get("relatedOrderNo"),
                "related_type=PAYMENT 要能反查出缴费单号，管理员不必再翻三张表");
        Map<?, ?> rechargeRow = findById(rows, rechargeRefund);
        assertEquals(RECHARGE_PREFIX + "REL", rechargeRow.get("relatedOrderNo"));
        Map<?, ?> orphanRow = findById(rows, orphan);
        assertFalse(orphanRow.containsKey("relatedOrderNo"),
                "关联行不存在时留空而不是编一个单号（库里现存 19 条 PENDING 就是这个形状）");
        assertNotNull(orphanRow.get("relatedId"), "related_id 仍然如实给出，让管理员自己去核");
    }

    @Test
    void j58_aRefundIssuedByPatientCancelIsReviewableEndToEnd() throws Exception {
        // 本卡最重要的一条：T13/T25 挂出来的单子在本卡之前无人能处理。
        // 走真实链路而不是裸插退款单，因为"挂单方与审单方用同一套 related_type 词表"正是被测对象。
        long doctorId = seedDoctor();
        long scheduleId = seedSchedule(doctorId);
        long patientId = seedPatient("端到端退号甲");
        long appointmentId = insertAppointment(patientId, doctorId, scheduleId, 7000L);

        mockMvc.perform(post("/user/appointments/" + appointmentId + "/cancel")
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "端到端探针退号"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        long refundId = jdbcTemplate.queryForObject(
                "SELECT id FROM refund_record WHERE related_type = 'APPOINTMENT' AND related_id = ?",
                Long.class, appointmentId);
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM refund_record WHERE id = ?", String.class, refundId),
                "前置条件：退号只挂单到 PENDING（RefundTicketService 的规矩）");

        Map<?, ?> beforeReview = expectData(admin(get("/admin/refunds/" + refundId)));
        assertEquals("PENDING", beforeReview.get("status"));
        assertEquals(APPOINTMENT_PREFIX + patientId, beforeReview.get("relatedOrderNo"),
                "后台一眼能看出退的是哪一张预约（APPOINTMENT 词表两边一致）");
        assertFalse(beforeReview.containsKey("reviewerId"), "未审时不该有审核人");

        Map<?, ?> afterReview = expectData(admin(post("/admin/refunds/" + refundId + "/approve")));
        assertEquals("APPROVED", afterReview.get("status"));
        assertEquals(ADMIN_USERNAME, afterReview.get("reviewerName"));

        // V1:179 的 COMPLETED 首版没有生产者：那属于"钱真的出去了"
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT status FROM refund_record WHERE id = ?", String.class, refundId),
                "本卡终点是 APPROVED，不是 COMPLETED——没有出款通道就不该声称已完成");
    }

    // ============================================================
    // 夹具
    // ============================================================

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private LocalDate probeDate() {
        return LocalDate.now().plusYears(PROBE_YEAR_OFFSET);
    }

    private long seedDoctor() {
        jdbcTemplate.update("INSERT INTO doctor (name, department_id, title_id, intro, specialty, "
                + "created_at, updated_at, deleted) VALUES (?, 1, 1, '探针简介', '探针方向', "
                + "NOW(3), NOW(3), 0)", DOCTOR_NAME);
        return maxId("doctor");
    }

    private long seedSchedule(long doctorId) {
        jdbcTemplate.update("INSERT INTO schedule (`date`, time_slot, doctor_id, total_slots, "
                + "remaining_slots, created_at, updated_at, deleted) VALUES (?, 'MORNING', ?, 10, 9, "
                + "NOW(3), NOW(3), 0)", probeDate(), doctorId);
        return maxId("schedule");
    }

    /** 已支付（CONFIRMED）的预约：只有进过钱的单子退号时才挂退款单。 */
    private long insertAppointment(long patientId, long doctorId, long scheduleId, long feeFen) {
        patientToken();
        jdbcTemplate.update("INSERT INTO appointment (order_no, patient_id, doctor_id, schedule_id, "
                        + "status, appointment_time, fee_fen, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, ?, ?, 'CONFIRMED', NOW(3), ?, NOW(3), NOW(3), 0)",
                APPOINTMENT_PREFIX + patientId, patientId, doctorId, scheduleId, feeFen);
        return maxId("appointment");
    }

    /** 通过真实注册链路拿一个 user + token，后续 DELETE /user/patients 与退号都要求归属对得上。 */
    private String patientToken() {
        if (patientToken != null) {
            return patientToken;
        }
        patientToken = newPatientToken("t26");
        return patientToken;
    }

    /** 裸插就诊人（id_card/phone 是密文列，本卡不需要解出来），user 走真实注册。 */
    private long seedPatient(String name) {
        patientToken();
        Long userId = userIds.get(userIds.size() - 1);
        jdbcTemplate.update("INSERT INTO patient (user_id, name, id_card, phone, relation, card_no, "
                        + "balance_fen, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, 'PROBE-CIPHER', 'PROBE-CIPHER', 'SELF', ?, 0, NOW(3), NOW(3), 0)",
                userId, name, "T26" + UUID.randomUUID().toString().substring(0, 12));
        return maxId("patient");
    }

    private long seedInpatient(String inpatientNo) {
        patientToken();
        Long userId = userIds.get(userIds.size() - 1);
        jdbcTemplate.update("INSERT INTO inpatient (user_id, name, inpatient_no, department, bed_no, "
                + "created_at, updated_at, deleted) VALUES (?, ?, ?, '探针科室', '09 层 03 床', "
                + "NOW(3), NOW(3), 0)", userId, "住院探针", inpatientNo);
        return maxId("inpatient");
    }

    private long seedPayment(long patientId, String tag, long amountFen) {
        // items 的两行金额固定 7000/3000，与 amount_fen 脱钩：这一列没有任何约束要求两项之和等于总额
        // （V1:160 只是 JSON NOT NULL），而本卡测的是后台怎么把它读出来。
        jdbcTemplate.update("INSERT INTO payment_record (order_no, patient_id, items, amount_fen, "
                        + "pay_method, status, trade_no, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'WECHAT', 'SUCCESS', ?, NOW(3), NOW(3))",
                PAYMENT_PREFIX + tag, patientId,
                "[{\"name\":\"探针项目一\",\"amountFen\":7000},{\"name\":\"探针项目二\",\"amountFen\":3000}]",
                amountFen, "T26-TXN");
        return maxId("payment_record");
    }

    private long seedRecharge(String orderNo, Long patientId, Long inpatientId) {
        jdbcTemplate.update("INSERT INTO recharge_record (order_no, patient_id, inpatient_id, "
                        + "amount_fen, pay_method, status, trade_no, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 10000, 'WECHAT', 'SUCCESS', 'T26-TXN-RC', NOW(3), NOW(3))",
                orderNo, patientId, inpatientId);
        return maxId("recharge_record");
    }

    private long seedCaseDelivery(long inpatientId) {
        jdbcTemplate.update("INSERT INTO case_delivery (inpatient_id, recipient_name, address, "
                + "id_card_photo, status, tracking_no, created_at, updated_at, deleted) "
                + "VALUES (?, ?, '探针省探针市探针路 1 号', NULL, 'PENDING', NULL, NOW(3), NOW(3), 0)",
                inpatientId, RECIPIENT_NAME);
        return maxId("case_delivery");
    }

    private long seedRefund(String orderNo, String relatedType, long relatedId, long amountFen, String status) {
        jdbcTemplate.update("INSERT INTO refund_record (order_no, related_id, related_type, amount_fen, "
                        + "reason, status, reviewer_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, '探针退款原因', ?, NULL, NOW(3), NOW(3))",
                orderNo, relatedId, relatedType, amountFen, status);
        return maxId("refund_record");
    }

    private long maxId(String table) {
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM " + table, Long.class);
        return id == null ? 0L : id;
    }

    private long balanceOf(long patientId) {
        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance_fen FROM patient WHERE id = ?", Long.class, patientId);
        return balance == null ? -1L : balance;
    }

    private long sum(String table) {
        Long total = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount_fen), 0) FROM " + table, Long.class);
        return total == null ? -1L : total;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String table : List.of("payment_record", "recharge_record", "refund_record", "case_delivery",
                "inpatient", "patient", "appointment", "schedule", "doctor", "`user`")) {
            counts.put(table, count("SELECT COUNT(*) FROM " + table));
        }
        return counts;
    }

    // ============================================================
    // 断言助手
    // ============================================================

    private void expectCode(MockHttpServletRequestBuilder builder, int code) throws Exception {
        expectCode(mockMvc.perform(builder).andReturn(), code);
    }

    private Map<?, ?> expectRoot(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    private void expectCode(MvcResult result, int code) throws Exception {
        Map<?, ?> root = expectRoot(result);
        assertEquals(code, ((Number) root.get("code")).intValue(),
                "期望业务码 " + code + "，实际：" + root);
    }

    private Map<?, ?> expectRoot(MockHttpServletRequestBuilder builder) throws Exception {
        return expectRoot(mockMvc.perform(builder).andReturn());
    }

    private Map<?, ?> expectData(MockHttpServletRequestBuilder builder) throws Exception {
        Map<?, ?> root = expectRoot(builder);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }

    private List<Map<?, ?>> expectList(MockHttpServletRequestBuilder builder) throws Exception {
        Map<?, ?> root = expectRoot(builder);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> rows = (List<Map<?, ?>>) data;
        return rows;
    }

    private Map<?, ?> findById(List<Map<?, ?>> rows, long id) {
        return rows.stream().filter(row -> id == number(row.get("id")))
                .findFirst().orElseThrow(() -> new AssertionError("列表里没有 id=" + id + " 这一行"));
    }

    private boolean containsId(List<Map<?, ?>> rows, long id) {
        return rows.stream().anyMatch(row -> id == number(row.get("id")));
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : -1L;
    }

    private String placeholders() {
        return userIds.isEmpty() ? "NULL"
                : userIds.stream().map(id -> "?").collect(Collectors.joining(","));
    }

    private Object[] userIdArgs() {
        return userIds.toArray();
    }

    private String staffToken(Long adminId, String role) {
        List<String> modules = List.of("dashboard", "schedule", "appointment", "report", "physical",
                "finance");
        return jwtUtil.generateToken(adminId, role, role, modules, List.of());
    }

    private String newPatientToken(String tag) {
        try {
            String body = mockMvc.perform(post("/auth/wechat-login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("code", tag + "-" + UUID.randomUUID()))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
            userIds.add(((Number) data.get("userId")).longValue());
            return String.valueOf(data.get("token"));
        } catch (Exception e) {
            throw new IllegalStateException("探针用户注册失败", e);
        }
    }

    private String json(Map<?, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }
}
