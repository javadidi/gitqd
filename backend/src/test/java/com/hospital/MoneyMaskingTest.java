package com.hospital;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.security.LoginUser;
import com.hospital.vo.PaymentDetailVO;
import com.hospital.vo.PaymentItemVO;
import com.hospital.vo.PaymentSummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * J7: 金额序列化裁剪 — 注入 Spring 真实 ObjectMapper，证明 Module 确实注册上了
 */
@SpringBootTest
class MoneyMaskingTest {

    private static final List<String> ROOT_MONEY_FIELDS =
            Arrays.asList("amountFen", "discountFen", "receivableFen", "receivedFen");
    private static final List<String> ITEM_MONEY_FIELDS =
            Arrays.asList("priceFen", "subtotalFen");
    private static final List<String> SUMMARY_MONEY_FIELDS =
            Arrays.asList("grossFen", "discountFen", "receivedFen", "outstandingFen", "diffFen");

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role) {
        LoginUser user = new LoginUser(1L, role, role, Collections.emptyList(), Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private PaymentDetailVO buildVo() {
        PaymentItemVO item = new PaymentItemVO();
        item.setId(11L);
        item.setName("血常规");
        item.setCategory("LAB");
        item.setQuantity(2);
        item.setPriceFen(2500L);
        item.setSubtotalFen(5000L);

        PaymentSummaryVO summary = new PaymentSummaryVO();
        summary.setGrossFen(43000L);
        summary.setDiscountFen(3000L);
        summary.setReceivedFen(40000L);
        summary.setOutstandingFen(0L);
        summary.setDiffFen(0L);
        summary.setNote("医保结算");
        summary.setItemCount(2);

        PaymentDetailVO vo = new PaymentDetailVO();
        vo.setId(99L);
        vo.setOrderNo("JF20260925-0001");
        vo.setPatientName("张三");
        vo.setStatus("PAID");
        vo.setAmountFen(43000L);
        vo.setDiscountFen(3000L);
        vo.setReceivableFen(40000L);
        vo.setReceivedFen(40000L);
        vo.setItems(Arrays.asList(item));
        vo.setSummary(summary);
        return vo;
    }

    private JsonNode serializeAs(String role) throws Exception {
        if (role == null) {
            SecurityContextHolder.clearContext();
        } else {
            loginAs(role);
        }
        String json = objectMapper.writeValueAsString(buildVo());
        System.out.println("=== role=" + role + " ===");
        System.out.println(json);
        return objectMapper.readTree(json);
    }

    @Test
    void nurse_allMoneyFieldsMaskedAtThreeLevels() throws Exception {
        JsonNode root = serializeAs("nurse");

        for (String field : ROOT_MONEY_FIELDS) {
            assertTrue(root.get(field).isNull(), "根级金额字段应为 null: " + field);
        }
        for (JsonNode item : root.get("items")) {
            for (String field : ITEM_MONEY_FIELDS) {
                assertTrue(item.get(field).isNull(), "数组内金额字段应为 null: " + field);
            }
        }
        JsonNode summary = root.get("summary");
        for (String field : SUMMARY_MONEY_FIELDS) {
            assertTrue(summary.get(field).isNull(), "嵌套对象金额字段应为 null: " + field);
        }
    }

    @Test
    void nurse_nonMoneyFieldsIntact() throws Exception {
        JsonNode root = serializeAs("nurse");

        assertEquals("JF20260925-0001", root.get("orderNo").asText());
        assertEquals("张三", root.get("patientName").asText());
        assertEquals("PAID", root.get("status").asText());
        assertEquals(99L, root.get("id").asLong());

        JsonNode item = root.get("items").get(0);
        assertEquals("血常规", item.get("name").asText());
        assertEquals("LAB", item.get("category").asText());
        assertEquals(2, item.get("quantity").asInt(), "quantity 是数量不是金额，必须保留");
        assertEquals(11L, item.get("id").asLong());

        JsonNode summary = root.get("summary");
        assertEquals("医保结算", summary.get("note").asText());
        assertEquals(2, summary.get("itemCount").asInt(), "itemCount 含 count 不含金额关键词，必须保留");
    }

    @Test
    void nurse_responseContainsNoMoneyDigits() throws Exception {
        loginAs("nurse");
        String json = objectMapper.writeValueAsString(buildVo());

        for (String leaked : Arrays.asList("43000", "3000", "40000", "2500", "5000")) {
            assertFalse(json.contains(leaked), "护士响应 JSON 中不应出现金额数字 " + leaked);
        }
    }

    @Test
    void admin_moneyFieldsPreserved() throws Exception {
        JsonNode root = serializeAs("admin");

        assertEquals(43000L, root.get("amountFen").asLong());
        assertEquals(40000L, root.get("receivableFen").asLong());
        assertEquals(2500L, root.get("items").get(0).get("priceFen").asLong());
        assertEquals(43000L, root.get("summary").get("grossFen").asLong());
    }

    @Test
    void doctor_moneyFieldsPreserved() throws Exception {
        JsonNode root = serializeAs("doctor");

        assertEquals(43000L, root.get("amountFen").asLong());
        assertEquals(2500L, root.get("items").get(0).get("priceFen").asLong());
    }

    @Test
    void noAuthentication_moneyFieldsPreserved() throws Exception {
        JsonNode root = serializeAs(null);

        assertEquals(43000L, root.get("amountFen").asLong());
    }

    @Test
    void maskedFieldKeyStillPresentForFrontend() throws Exception {
        JsonNode root = serializeAs("nurse");

        assertTrue(root.has("amountFen"), "字段 key 必须存在（值为 null），前端 <Money value={null}> 才能渲染 —");
    }
}
