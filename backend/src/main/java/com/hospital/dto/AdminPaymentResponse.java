package com.hospital.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端门诊消费记录（T26 卡片 716 行 / PRD 369–370 行）。列表与详情共用一个类。
 *
 * <p>{@code items} 只有详情那趟会填：列表按行反解 JSON 明细是白做的功
 * （PRD 369 行那句「展示门诊消费记录」没有要求列表明细），
 * 而 {@code default-property-inclusion: non_null} 会让列表行里连 {@code items} 这个键都不出现。
 *
 * <h2>为什么这里用强类型 {@link OutpatientPaymentResponse.Item}，不像 T17 的 report 那样原样透传 JSON</h2>
 * {@code payment_record.items}（V1:160）里装的每一项都是<b>钱</b>（{@code amountFen}）。
 * 金额裁剪层（{@code MoneyMaskingModifier}）是按 <b>bean 属性名</b>挂拦截器的：
 * 只有 Number 类型的 Java 字段才会被套上 {@code MoneyMaskingSerializer}。
 * 若这一列以 {@code JsonNode}／字符串的形式出口，节点里的 {@code amountFen} 就不在任何 bean 属性表上，
 * 护士角色会照常看见明细金额——这正是附录 B「严禁前端隐藏金额」反过来的破法。
 * T17 那边能透传是因为 {@code report.items} 装的是检验项数值，不是钱，裁剪层本来就管不着。
 *
 * <p>解析复用 {@code OutpatientPaymentService.parseItems}（同一个键名集合、同一套跳行规则），
 * 与 T19 票据详情走同一条路径的理由一致。
 */
public class AdminPaymentResponse {

    private Long id;
    private String orderNo;
    private Long patientId;
    private String patientName;
    private String cardNo;
    private Long amountFen;
    private String payMethod;
    private String status;
    private String tradeNo;
    private List<OutpatientPaymentResponse.Item> items;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getCardNo() { return cardNo; }
    public void setCardNo(String cardNo) { this.cardNo = cardNo; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public List<OutpatientPaymentResponse.Item> getItems() { return items; }
    public void setItems(List<OutpatientPaymentResponse.Item> items) { this.items = items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
