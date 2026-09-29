package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 住院充值出参（T23：卡片 657 行的支付结果 + PRD 300 行「充值记录列表」+ 301 行「账单详情」）。
 *
 * <p>列表与详情共用这一个形状（与 T14 的 {@link RechargeResponse} 同一条取舍）：
 * 两页要的字段完全重合，拆开只会让「哪个字段在哪一页没有」变成需要记忆的细节。
 *
 * <p><b>本 DTO 没有 {@code balanceFen}，这是它与 T14 那个类唯一实质性的不同</b>。
 * T14 带余额是因为 PRD 98 行明写「充值金额实时到账就诊卡余额」，成功页必须让患者看见钱进了卡；
 * 而住院侧没有任何一条规格说过到账，{@code inpatient} 表（V1:41-53）也只有
 * {@code user_id / name / inpatient_no / department / bed_no} 五列业务字段，
 * <b>根本没有余额列可回</b>。住院预交金账户在 HIS 侧，不在本系统。
 * 所以卡片 657 行的原话「选择住院人，输入充值金额，支付」到此为止——本卡建单、支付、留流水，
 * 不动任何一张钱表的数字（这条断言写在 {@code InpatientRechargeService} 的类注释里）。
 *
 * <p>字段来源逐条可追溯：{@code orderNo / amountFen / payMethod / status / tradeNo / createdAt}
 * 对应 {@code recharge_record} 的列（V1:140-151），{@code inpatientId} 即该表的
 * {@code inpatient_id}（V1:144「住院人（住院充值）」），整体形状对上 PRD 582 行数据字典
 * 「充值记录 = 充值ID、就诊人ID/住院人ID、金额、支付方式、状态、时间」。
 * {@code inpatientName} 与 {@code inpatientNo} 是列表可读项：一个用户可以绑多个住院人
 * （seed 里 user 1 就有张守义与周雅两人），只给 id 患者读不出这笔钱充给了谁。
 *
 * <p>{@code amountFen} 含 fen，会被金额裁剪层命中——但裁剪只对 {@code nurse} 生效，
 * 而本接口在 {@code /user/**} 下只有患者 token 进得来，患者看自己的流水是需求本身。
 */
public class InpatientRechargeResponse {

    private Long id;
    private String orderNo;
    private Long inpatientId;
    private String inpatientName;
    private String inpatientNo;
    private Long amountFen;
    private String status;
    private String payMethod;
    private String tradeNo;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getInpatientId() { return inpatientId; }
    public void setInpatientId(Long inpatientId) { this.inpatientId = inpatientId; }
    public String getInpatientName() { return inpatientName; }
    public void setInpatientName(String inpatientName) { this.inpatientName = inpatientName; }
    public String getInpatientNo() { return inpatientNo; }
    public void setInpatientNo(String inpatientNo) { this.inpatientNo = inpatientNo; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
