package com.hospital.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 门诊充值入参（T14 卡片 494 行「充值页面：选择就诊人，输入充值金额，选择支付方式（微信支付）」）。
 *
 * <p><b>只有两个字段</b>：
 * <ul>
 *   <li><b>没有 userId</b>：归属只从 token 取（附录 B 那条老规矩）；</li>
 *   <li><b>没有 payMethod</b>：卡片括号里写死了微信支付，而 {@code pay_method} 的另外三个取值
 *       （ALIPAY/CASH，V1:146）在本系统里没有任何通道。让客户端传这个字段，
 *       等于允许它声明一个系统不支持的支付方式并被记进财务表——所以由服务端固定写 {@code WECHAT}；</li>
 *   <li><b>没有金额上限</b>：PRD 与卡片都没给过限额，编一个"单笔最多 X 元"就是替规格发明数字。
 *       只校验正数（{@code @Positive}）：0 和负数会让"充值成功但余额没变"甚至余额变少，
 *       那是账本层面的错，不是业务偏好。</li>
 * </ul>
 *
 * <p>{@code amountFen} 用分而不是元：与 {@code fee_fen}/{@code amount_fen} 全部列存分一致，
 * 也让"客户端传小数元"这种精度陷阱在类型层面就不存在（附录 B「金额有没有 FLOAT/DOUBLE」）。
 */
public class RechargeCreateRequest {

    @NotNull(message = "请选择就诊人")
    private Long patientId;

    @NotNull(message = "请输入充值金额")
    @Positive(message = "充值金额必须大于 0")
    private Long amountFen;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
}
