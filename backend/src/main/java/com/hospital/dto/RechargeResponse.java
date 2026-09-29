package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 充值出参（T14：卡片 495 行「支付成功—展示充值成功信息」+ 496 行「充值记录」+ PRD 297 行「账单详情」）。
 *
 * <p>列表与详情共用这一个形状：两者要求的字段完全重合（单号、就诊人、金额、状态、时间），
 * 拆成两个 DTO 只会让"哪个字段在哪一页没有"变成需要记忆的细节。
 *
 * <p><b>{@code balanceFen} 是本 DTO 存在的理由</b>：PRD 98 行写「充值金额实时到账就诊卡余额」，
 * 而"到账"这件事必须可被患者看见才算数——只回一句"充值成功"，患者无从判断钱有没有进卡。
 * 所以成功页显示的是<b>到账后的余额</b>，它和 {@code amountFen} 一起构成"加对了"的证据。
 *
 * <p><b>但只有详情给余额，列表不给</b>（T14 真 HTTP 验收第 7c2 步实测：列表行的 keys 里没有
 * {@code balanceFen}）。{@code RechargeService.list} 传给 {@code toResponse} 的是
 * {@code nameOnly} 桩对象，余额为 null，Jackson 的 NON_NULL 把键整个省掉。这不是疏漏而是刻意的：
 * 这一个字段是就诊人<b>此刻</b>的余额，历史行各显示一遍同一个数字，
 * 患者会读成"当时到账后还剩这么多"——那是个错误的账。列表页据此不渲染余额列。
 *
 * <p>字段来源逐条可追溯：{@code orderNo}/{@code status}/{@code payMethod}/{@code tradeNo}/
 * {@code createdAt} 对应 {@code recharge_record} 的列（V1:140-151）与 PRD 582 行数据字典
 * 「充值记录 = 充值ID、就诊人ID/住院人ID、金额、支付方式、状态、时间」；
 * {@code patientName} 是列表可读项；{@code amountFen} 即 PRD 里的"金额"。
 *
 * <p><b>没有 {@code inpatientId}</b>：本卡只做门诊充值（住院充值属 T23），
 * 列表按 {@code patient_id} 过滤，返回一个恒为 NULL 的字段只会让人以为住院充值也能从这里看到。
 *
 * <p>{@code amountFen}/{@code balanceFen} 含 fen，会被金额裁剪层命中——
 * 但裁剪只对 {@code nurse} 生效，而本接口在 {@code /user/**} 下只有患者 token 进得来，
 * 患者看自己的余额与流水是需求本身。
 */
public class RechargeResponse {

    private Long id;
    private String orderNo;
    private Long patientId;
    private String patientName;
    private Long amountFen;
    private String status;
    private String payMethod;
    private String tradeNo;
    private Long balanceFen;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
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
