package com.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 住院服务（T23）：J51 住院充值 → 记录创建、J52 病案配送 → 申请创建。
 *
 * <h2>本卡最难的一条不是"写对"，而是"少写"</h2>
 * 卡片 657-661 行五件事，只有两件有数据落点，所以这个测试类里有一半的用例断言的是
 * <b>不存在的东西</b>：住院充值不回余额（{@code inpatient} 表没有余额列）、
 * 病案配送不回证件照片也不收配送费（无上传通道、无费用列）、
 * 费用详情与住院日清单一个端点都没有（全仓没有住院费用表）。
 * 这些"没有"如果被将来的人当成疏漏补上，本类的断言会先响。
 *
 * <h2>探针数据</h2>
 * 每个用例自己 wechat-login 建用户、自己绑住院人，收尾按 {@code inpatient_id} 精确删
 * 充值流水与配送申请、再删住院人与用户与本卡审计，最后比对八项计数回到基线。
 * 住院人没有删除入口（T09 有意不做），所以软删往返那条用例是手工 {@code UPDATE deleted} 再还原。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class HospitalizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdInpatientIds = new ArrayList<>();
    private final List<Long> createdPatientIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;
    private int balanceBefore;

    private String token;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
        balanceBefore = balanceTotal();
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        for (Long inpatientId : createdInpatientIds) {
            jdbcTemplate.update("DELETE FROM recharge_record WHERE inpatient_id = ?", inpatientId);
            jdbcTemplate.update("DELETE FROM case_delivery WHERE inpatient_id = ?", inpatientId);
            jdbcTemplate.update("DELETE FROM inpatient WHERE id = ?", inpatientId);
        }
        for (Long patientId : createdPatientIds) {
            jdbcTemplate.update("DELETE FROM recharge_record WHERE patient_id = ?", patientId);
            jdbcTemplate.update("DELETE FROM patient WHERE id = ?", patientId);
        }
        jdbcTemplate.update("DELETE FROM audit_log WHERE action IN "
                + "('CREATE_INPATIENT_RECHARGE', 'CREATE_CASE_DELIVERY')");
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        assertEquals(countsBefore, snapshotCounts(), "T23 只动自己造的探针行：八项计数必须回到基线");
        assertEquals(balanceBefore, balanceTotal(), "本卡不碰任何就诊人余额");
    }

    // ============================================================
    // J51 住院充值 → 记录创建（卡片 657 行）
    // ============================================================

    @Test
    void j51_recharge_createsSuccessRecord() throws Exception {
        long inpatientId = createInpatient("T23甲", "ZY-T23A");

        Map<?, ?> data = expectData(recharge(inpatientId, 20000));

        assertEquals("SUCCESS", data.get("status"), "卡片 657 行到「支付」为止，mock 通道内即时完成");
        assertEquals(20000L, ((Number) data.get("amountFen")).longValue());
        assertEquals("WECHAT", data.get("payMethod"), "PRD 560 行住院充值流程写的是微信支付");
        assertEquals("T23甲", data.get("inpatientName"));
        assertEquals("ZY-T23A", data.get("inpatientNo"));
        assertNotNull(data.get("tradeNo"), "SUCCESS 的单必须有流水号（mock 占位，与 T14 同族）");
        assertTrue(String.valueOf(data.get("orderNo")).matches("^CF\\d{8}-\\d{4}$"),
                "复用 SerialType.CF（充值单号），本卡不新增序列种类，实际：" + data.get("orderNo"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, amount_fen, pay_method, patient_id, inpatient_id, trade_no "
                        + "FROM recharge_record WHERE id = ?",
                ((Number) data.get("id")).longValue());
        assertEquals("SUCCESS", row.get("status"));
        assertEquals(inpatientId, ((Number) row.get("inpatient_id")).longValue());
        assertNull(row.get("patient_id"),
                "V1:143-144 两列分别承载两种充值：住院单的 patient_id 必须为空");
        assertNotNull(row.get("trade_no"));
    }

    @Test
    void j51_noMoneyMovesAnywhereButThisOneRow() throws Exception {
        long inpatientId = createInpatient("T23乙", "ZY-T23B");

        recharge(inpatientId, 8800);

        assertEquals(balanceBefore, balanceTotal(),
                "住院充值不动任何就诊人的余额：inpatient 表（V1:41-53）根本没有余额列");
        assertEquals(countsBefore.get("payment_record").intValue(), count("SELECT COUNT(*) FROM payment_record"),
                "不产生缴费单");
        assertEquals(countsBefore.get("refund_record").intValue(), count("SELECT COUNT(*) FROM refund_record"),
                "不产生退款单");
        assertEquals(countsBefore.get("recharge_record").intValue() + 1,
                count("SELECT COUNT(*) FROM recharge_record"),
                "四张钱表里唯一变动的就是这一行充值流水");
    }

    @Test
    void j51_responseCarriesNoBalanceKey() throws Exception {
        long inpatientId = createInpatient("T23丙", "ZY-T23C");
        Map<?, ?> data = expectData(recharge(inpatientId, 1000));

        assertFalse(data.containsKey("balanceFen"),
                "T14 的回余额是因为 PRD 98 行写了「实时到账就诊卡余额」；"
                        + "住院侧没有任何到账规格，回一个恒为 null 的余额键等于挂一个假承诺");
    }

    @Test
    void j51_amountMustBePositiveAndPresent() throws Exception {
        long inpatientId = createInpatient("T23丁", "ZY-T23D");
        int rechargeRows = count("SELECT COUNT(*) FROM recharge_record");

        assertValidationRejected("/user/inpatient-recharges",
                body(Map.of("inpatientId", inpatientId, "amountFen", 0)));
        assertValidationRejected("/user/inpatient-recharges",
                body(Map.of("inpatientId", inpatientId, "amountFen", -100)));
        assertValidationRejected("/user/inpatient-recharges", body(Map.of("inpatientId", inpatientId)));

        assertEquals(rechargeRows, count("SELECT COUNT(*) FROM recharge_record"),
                "被拒的请求一行流水都不留（校验在进服务层之前）");
    }

    @Test
    void j51_foreignInpatientIsRejectedAndLeavesNothing() throws Exception {
        String otherToken = newPatientToken("t23-other");
        long otherInpatient = bindWith(otherToken, "T23他人", "ZY-T23-OTHER");

        Map<?, ?> root = expectRoot(recharge(otherInpatient, 5000, token()));
        assertEquals(1005, ((Number) root.get("code")).intValue(),
                "别人的住院人与不存在的住院人同码（T09 同一条判法，403 会确认记录存在）");
        assertEquals(countsBefore.get("recharge_record").intValue(),
                count("SELECT COUNT(*) FROM recharge_record"), "被拒不留单");
        assertEquals(0, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_INPATIENT_RECHARGE'"),
                "被拒不留痕（审计与业务同事务，回滚即一起消失）");
    }

    @Test
    void j51_missingInpatientUsesTheSameCodeAsForeign() throws Exception {
        Map<?, ?> root = expectRoot(recharge(9_999_999L, 5000, token()));
        assertEquals(1005, ((Number) root.get("code")).intValue(),
                "两条路径同码，否则这个接口可以用来枚举住院号");
    }

    @Test
    void j51_auditWrittenInSameTransactionWithPatientOperator() throws Exception {
        long inpatientId = createInpatient("T23戊", "ZY-T23E");
        recharge(inpatientId, 6600);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, operator_type, target_type FROM audit_log "
                        + "WHERE action = 'CREATE_INPATIENT_RECHARGE' ORDER BY id DESC LIMIT 1");
        assertEquals("PATIENT", audit.get("operator_type"), "操作人是患者主体，不是员工");
        assertEquals("recharge_record", audit.get("target_type"));
    }

    @Test
    void list_coversAllMineAndFiltersByInpatient() throws Exception {
        long first = createInpatient("T23己一", "ZY-T23F1");
        long second = createInpatient("T23己二", "ZY-T23F2");
        recharge(first, 1000);
        recharge(second, 2000);

        List<?> all = expectList(getWithToken("/user/inpatient-recharges"));
        assertEquals(2, all.size(), "个人中心那一层看本人全部住院人的流水（PRD 300 行）");

        List<?> filtered = expectList(getWithToken("/user/inpatient-recharges?inpatientId=" + first));
        assertEquals(1, filtered.size(), "PRD 232-233 行「选择住院人员 → 住院记录」的筛选");
        assertEquals("T23己一", ((Map<?, ?>) filtered.get(0)).get("inpatientName"));
    }

    @Test
    void list_filterRejectsForeignInpatient() throws Exception {
        String otherToken = newPatientToken("t23-filter-other");
        long otherInpatient = bindWith(otherToken, "T23越权筛选", "ZY-T23-FILTER");

        Map<?, ?> root = expectRoot(getWithTokenRaw("/user/inpatient-recharges?inpatientId=" + otherInpatient));
        assertEquals(1005, ((Number) root.get("code")).intValue(),
                "筛选参数不能变成读别人记录的钥匙");
    }

    @Test
    void detail_rejectsOutpatientRechargeId() throws Exception {
        long patientId = createPatient("T23门诊");
        Map<?, ?> outpatient = expectData(mockMvc.perform(post("/user/recharges")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("patientId", patientId, "amountFen", 3000))))
                .andExpect(status().isOk())
                .andReturn());
        long outpatientId = ((Number) outpatient.get("id")).longValue();

        Map<?, ?> root = expectRoot(getWithTokenRaw("/user/inpatient-recharges/" + outpatientId));
        assertEquals(5001, ((Number) root.get("code")).intValue(),
                "两张链路共用 recharge_record，靠 inpatient_id / patient_id 谁非空互斥；"
                        + "少了这道判据，门诊单会从这里漏进住院页");
    }

    @Test
    void detail_foreignRecordIsNotFound() throws Exception {
        String otherToken = newPatientToken("t23-detail-other");
        long otherInpatient = bindWith(otherToken, "T23他人单", "ZY-T23-DOTHER");
        long deliveryId = ((Number) expectData(recharge(otherInpatient, 4000, otherToken)).get("id")).longValue();

        Map<?, ?> root = expectRoot(getWithTokenRaw("/user/inpatient-recharges/" + deliveryId));
        assertEquals(5001, ((Number) root.get("code")).intValue());
    }

    // ============================================================
    // J52 病案配送 → 申请创建（卡片 661 行）
    // ============================================================

    @Test
    void j52_create_writesPendingApplication() throws Exception {
        long inpatientId = createInpatient("T23庚", "ZY-T23G");

        Map<?, ?> data = expectData(delivery(inpatientId, "张收件", "北京市朝阳区 1 号"));

        assertEquals("PENDING", data.get("status"),
                "状态由服务端固定写：SHIPPED/DELIVERED 的生产者是填快递单号的后台（卡片 720 行）");
        assertEquals("T23庚", data.get("inpatientName"));
        assertEquals("ZY-T23G", data.get("inpatientNo"));
        assertEquals("张收件", data.get("recipientName"));
        assertEquals("北京市朝阳区 1 号", data.get("address"));
        assertFalse(data.containsKey("trackingNo"),
                "患者提交那一刻不存在物流单号，NON_NULL 让这个键整个消失");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT inpatient_id, recipient_name, address, status, tracking_no, id_card_photo "
                        + "FROM case_delivery WHERE id = ?",
                ((Number) data.get("id")).longValue());
        assertEquals(inpatientId, ((Number) row.get("inpatient_id")).longValue());
        assertEquals("PENDING", row.get("status"));
        assertNull(row.get("tracking_no"));
        assertNull(row.get("id_card_photo"),
                "卡片 661 行的「上传证件」首版不做：全仓没有上传通道，不往这一列编路径");
    }

    @Test
    void j52_recipientAndAddressAreRequiredAndLengthCapped() throws Exception {
        long inpatientId = createInpatient("T23辛", "ZY-T23H");
        int before = count("SELECT COUNT(*) FROM case_delivery");

        assertValidationRejected("/user/case-deliveries", deliveryBody(Map.of("inpatientId", inpatientId,
                "recipientName", "  ", "address", "某地")));
        assertValidationRejected("/user/case-deliveries", deliveryBody(Map.of("inpatientId", inpatientId,
                "address", "某地")));
        assertValidationRejected("/user/case-deliveries", deliveryBody(Map.of("inpatientId", inpatientId,
                "recipientName", "甲")));
        assertValidationRejected("/user/case-deliveries", deliveryBody(Map.of("inpatientId", inpatientId,
                "recipientName", "甲".repeat(65), "address", "某地")));
        assertValidationRejected("/user/case-deliveries", deliveryBody(Map.of("inpatientId", inpatientId,
                "recipientName", "甲", "address", "某".repeat(513))));

        assertEquals(before, count("SELECT COUNT(*) FROM case_delivery"),
                "超长在进库前就被拦，不会变成 1406 或 catch-all 的 500");
    }

    @Test
    void j52_responseCarriesNoFeeAndNoPhotoKey() throws Exception {
        long inpatientId = createInpatient("T23壬", "ZY-T23I");
        Map<?, ?> data = expectData(delivery(inpatientId, "收件人", "地址"));

        assertFalse(data.containsKey("amountFen"), "没有配送费可收：表无费用列、字典无此项、无价目配置");
        assertFalse(data.containsKey("feeFen"));
        assertFalse(data.containsKey("idCardPhoto"), "首版这一列恒为 NULL，不挂一个永远为空的键");
        assertFalse(data.containsKey("orderNo"),
                "case_delivery 没有单号列（V1:328-339），本卡也不为它新增序列种类");
    }

    @Test
    void j52_foreignInpatientIsRejectedAndLeavesNothing() throws Exception {
        String otherToken = newPatientToken("t23-delivery-other");
        long otherInpatient = bindWith(otherToken, "T23他人病案", "ZY-T23-DEOTHER");
        int before = count("SELECT COUNT(*) FROM case_delivery");

        Map<?, ?> root = expectRoot(mockMvc.perform(post("/user/case-deliveries")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveryBody(Map.of("inpatientId", otherInpatient,
                                "recipientName", "收件", "address", "地址"))))
                .andExpect(status().isOk())
                .andReturn());

        assertEquals(1005, ((Number) root.get("code")).intValue());
        assertEquals(before, count("SELECT COUNT(*) FROM case_delivery"), "被拒不留申请");
        assertEquals(0, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_CASE_DELIVERY'"),
                "被拒不留痕");
    }

    @Test
    void j52_auditWrittenInSameTransaction() throws Exception {
        long inpatientId = createInpatient("T23癸", "ZY-T23J");
        delivery(inpatientId, "收件", "地址");

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT action, operator_type, target_type FROM audit_log "
                        + "WHERE action = 'CREATE_CASE_DELIVERY' ORDER BY id DESC LIMIT 1");
        assertEquals("PATIENT", audit.get("operator_type"));
        assertEquals("case_delivery", audit.get("target_type"));
    }

    @Test
    void deliveryListAndDetail_onlyMine() throws Exception {
        long mine = createInpatient("T23记录我", "ZY-T23-KMINE");
        String otherToken = newPatientToken("t23-delivery-list-other");
        long theirs = bindWith(otherToken, "T23记录他", "ZY-T23-KTHEIRS");
        delivery(mine, "我的收件", "我的地址");
        delivery(theirs, "他的收件", "他的地址", otherToken);

        List<?> rows = expectList(getWithToken("/user/case-deliveries"));
        assertEquals(1, rows.size(), "列表只回本人住院人的申请");
        assertEquals("T23记录我", ((Map<?, ?>) rows.get(0)).get("inpatientName"));

        long id = ((Number) ((Map<?, ?>) rows.get(0)).get("id")).longValue();
        Map<?, ?> detail = expectData(getWithTokenRaw("/user/case-deliveries/" + id));
        assertEquals("我的地址", detail.get("address"));

        long foreignId = jdbcTemplate.queryForObject(
                "SELECT id FROM case_delivery WHERE inpatient_id = ?", Long.class, theirs);
        Map<?, ?> denied = expectRoot(getWithTokenRaw("/user/case-deliveries/" + foreignId));
        assertEquals(5001, ((Number) denied.get("code")).intValue());
    }

    // ============================================================
    // 归属跳尊重软删 + 端点数量契约
    // ============================================================

    @Test
    void softDeletedInpatientHidesItsRecordsAndBlocksNewOnes() throws Exception {
        long inpatientId = createInpatient("T23软删", "ZY-T23-L");
        recharge(inpatientId, 1200);
        delivery(inpatientId, "收件", "地址");
        assertEquals(1, expectList(getWithToken("/user/inpatient-recharges")).size());
        assertEquals(1, expectList(getWithToken("/user/case-deliveries")).size());

        jdbcTemplate.update("UPDATE inpatient SET deleted = 1 WHERE id = ?", inpatientId);

        assertEquals(0, expectList(getWithToken("/user/inpatient-recharges")).size(),
                "住院人软删后它的充值流水不再出现在列表（BaseEntity 的 @TableLogic 让归属跳查不到）");
        assertEquals(0, expectList(getWithToken("/user/case-deliveries")).size());
        assertEquals(1005, ((Number) expectRoot(recharge(inpatientId, 100, token())).get("code")).intValue(),
                "也不能继续往一个已解绑的住院人身上充值或提申请");
        assertEquals(1005, ((Number) expectRoot(delivery(inpatientId, "收件", "地址")).get("code")).intValue());

        jdbcTemplate.update("UPDATE inpatient SET deleted = 0 WHERE id = ?", inpatientId);
        assertEquals(1, expectList(getWithToken("/user/inpatient-recharges")).size(), "还原后读得到");
    }

    @Test
    void t23_endpointsAreExactlyTheSixThisCardAdds() {
        TreeSet<String> recharges = new TreeSet<>();
        TreeSet<String> deliveries = new TreeSet<>();
        TreeSet<String> bills = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                if (pattern.startsWith("/user/inpatient-recharges")) {
                    recharges.add(verb + " " + pattern);
                }
                if (pattern.startsWith("/user/case-deliveries")) {
                    deliveries.add(verb + " " + pattern);
                }
                if (pattern.contains("bill") || pattern.contains("expense")
                        || pattern.contains("daily") || pattern.contains("logistics")) {
                    bills.add(verb + " " + pattern);
                }
            }
        });

        assertEquals("[GET /user/inpatient-recharges, GET /user/inpatient-recharges/{id}, "
                + "POST /user/inpatient-recharges]", recharges.toString(),
                "充值三把：POST 出自卡片 657 行，两把 GET 出自 PRD 300-301 行");
        assertEquals("[GET /user/case-deliveries, GET /user/case-deliveries/{id}, "
                + "POST /user/case-deliveries]", deliveries.toString(),
                "配送三把：POST 出自卡片 661 行，两把 GET 出自 PRD 315-316 行");
        assertEquals("[]", bills.toString(),
                "卡片 659-660 行的「费用详情 / 住院日清单」与 §9.1 619 行的「物流查询」"
                        + "首版一个端点都不开：全仓没有住院费用表，物流只有一个 tracking_no 列");
    }

    // ============================================================
    // 助手
    // ============================================================

    private String token() throws Exception {
        if (token == null) {
            token = newPatientToken("t23-owner");
        }
        return token;
    }

    private long createInpatient(String name, String inpatientNo) throws Exception {
        return bindWith(token(), name, inpatientNo);
    }

    private long bindWith(String bearer, String name, String inpatientNo) throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name, "inpatientNo", inpatientNo))))
                .andExpect(status().isOk())
                .andReturn());
        long id = ((Number) data.get("id")).longValue();
        createdInpatientIds.add(id);
        return id;
    }

    private long createPatient(String name) throws Exception {
        Map<?, ?> data = expectData(mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name, "cardNo", randomCardNo(),
                                "idCard", "110101199003071234", "phone", "13900002345",
                                "relation", "SELF"))))
                .andExpect(status().isOk())
                .andReturn());
        long id = ((Number) data.get("id")).longValue();
        createdPatientIds.add(id);
        return id;
    }

    private MvcResult recharge(long inpatientId, long amountFen) throws Exception {
        return recharge(inpatientId, amountFen, token());
    }

    private MvcResult recharge(long inpatientId, long amountFen, String bearer) throws Exception {
        return mockMvc.perform(post("/user/inpatient-recharges")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("inpatientId", inpatientId, "amountFen", amountFen))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult delivery(long inpatientId, String recipient, String address) throws Exception {
        return delivery(inpatientId, recipient, address, token());
    }

    private MvcResult delivery(long inpatientId, String recipient, String address, String bearer)
            throws Exception {
        return mockMvc.perform(post("/user/case-deliveries")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveryBody(Map.of("inpatientId", inpatientId,
                                "recipientName", recipient, "address", address))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String deliveryBody(Map<?, ?> fields) throws Exception {
        Map<String, Object> body = new HashMap<>();
        fields.forEach((key, value) -> body.put(String.valueOf(key), value));
        return objectMapper.writeValueAsString(body);
    }

    private MvcResult getWithTokenRaw(String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult getWithToken(String path) throws Exception {
        return getWithTokenRaw(path);
    }

    private List<?> expectList(MvcResult result) throws Exception {
        Map<?, ?> root = expectRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        return (List<?>) data;
    }

    private void assertValidationRejected(String path, String jsonBody) throws Exception {
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private String randomCardNo() {
        return "T23" + String.format("%07d", Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000));
    }

    private String newPatientToken(String tag) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("code", tag + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        createdUserIds.add(((Number) data.get("userId")).longValue());
        return String.valueOf(data.get("token"));
    }

    private int balanceTotal() {
        return count("SELECT COALESCE(SUM(balance_fen), 0) FROM patient");
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.put("recharge_record", count("SELECT COUNT(*) FROM recharge_record"));
        snapshot.put("payment_record", count("SELECT COUNT(*) FROM payment_record"));
        snapshot.put("refund_record", count("SELECT COUNT(*) FROM refund_record"));
        snapshot.put("case_delivery", count("SELECT COUNT(*) FROM case_delivery"));
        snapshot.put("inpatient", count("SELECT COUNT(*) FROM inpatient"));
        snapshot.put("patient", count("SELECT COUNT(*) FROM patient"));
        snapshot.put("user", count("SELECT COUNT(*) FROM `user`"));
        snapshot.put("audit_t23", count("SELECT COUNT(*) FROM audit_log WHERE action IN "
                + "('CREATE_INPATIENT_RECHARGE', 'CREATE_CASE_DELIVERY')"));
        return snapshot;
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private String body(Map<?, ?> payload) throws Exception {
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
}
