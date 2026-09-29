package com.hospital.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门诊缴费出参（T15：卡片 511–514 行 + PRD 115–116 行「确认缴费信息 / 缴费信息」+ PRD 288–289 行
 * 「缴费记录列表 / 缴费详情」）。
 *
 * <p>四个读端点与一个写端点共用这一个形状，缺的字段由 Jackson 的 NON_NULL 整个省掉
 * （见 {@code application.yml} 的 {@code default-property-inclusion: non_null}）——
 * 与其为每页造一个 DTO，不如把"哪一页该有什么"写在这里说清楚：
 * <ul>
 *   <li>{@code GET /user/payments/pending}（待缴费项目列表）：单号、就诊人、{@code items}、金额、时间；</li>
 *   <li>{@code GET /user/payments/{id}}（确认缴费信息页要用的明细）：同上 <b>+ {@code balanceFen}</b>，
 *       因为患者要在按下"缴费"之前就知道余额够不够（J36 的 UI 入口）；</li>
 *   <li>{@code POST /user/payments/{id}/pay}（缴费成功页）：全部字段，此时 {@code balanceFen} 是<b>扣费后余额</b>；</li>
 *   <li>{@code GET /user/payments}（缴费记录列表）：单号、就诊人、金额、状态、支付方式、时间，<b>不含明细与余额</b>。</li>
 * </ul>
 *
 * <p><b>{@code balanceFen} 的语义随场景变，所以标签必须跟着变</b>：缴费成功那一刻它是"扣费后余额"，
 * 看历史记录时它是"当前余额"。前端两处用了不同文案（{@code pay.wxml} 写「扣费后余额」、
 * {@code detail.wxml} 写「当前卡内余额」），这与 T14 列表不给余额是同一条纪律——
 * 同一个数字挂在不同语境下不写清就会变成假账。列表页干脆不给，是因为一张单属于一个就诊人，
 * 每行都重复一遍他的实时余额只会让人以为是"当时扣完剩多少"。
 *
 * <p>{@code items} 来自 {@code payment_record.items}（V1:160 的 JSON 列），形状是
 * {@code [{"name":"血常规","amountFen":3200}]}（{@code seed.sql:192}），
 * 由 service 解析成强类型 {@link Item} 而不是原样吐字符串：小程序侧要按行渲染明细，
 * 让它去 {@code JSON.parse} 一个字符串等于把 schema 知识漏给前端。
 *
 * <p>{@code amountFen}／{@code items[].amountFen} 都含 {@code fen}，会被金额裁剪层命中——
 * 但裁剪只对 {@code nurse} 生效，而本组接口在 {@code /user/**} 下只有患者 token 进得来，
 * 患者看自己的账单是需求本身。
 */
public class OutpatientPaymentResponse {

    private Long id;
    private String orderNo;
    private Long patientId;
    private String patientName;
    private List<Item> items;
    private Long amountFen;
    private String status;
    private String payMethod;
    private String tradeNo;
    private Long balanceFen;
    private LocalDateTime createdAt;

    /** 单个缴费项目。字段名对齐 {@code items} JSON 列里的键，不改名，否则历史行读不出来。 */
    public static class Item {
        private String name;
        private Long amountFen;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getAmountFen() { return amountFen; }
        public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public Long getBalanceFen() { return balanceFen; }
    public void setBalanceFen(Long balanceFen) { this.balanceFen = balanceFen; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
