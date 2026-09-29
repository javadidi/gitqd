package com.hospital.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 住院充值入参（T23 卡片 657 行「住院充值：选择住院人，输入充值金额，支付。」）。
 *
 * <p><b>只有两个字段，与 T14 的 {@link RechargeCreateRequest} 同形，差别只在主语换成住院人</b>：
 * <ul>
 *   <li><b>没有 userId</b>：归属只从 token 取（附录 B「权限判断是否只写在 UI」）；</li>
 *   <li><b>没有 payMethod</b>：PRD 560 行的住院充值流程写的是「输入金额 → 微信支付 → 充值成功」，
 *       通道只有微信一个；{@code pay_method} 的另外三个取值（ALIPAY/CASH，V1:146）在本系统没有任何通道，
 *       让客户端传这个字段等于允许它把一个系统不支持的支付方式声明进财务表。服务端固定写 {@code WECHAT}，
 *       与 T14 同一条取舍；</li>
 *   <li><b>没有金额上限</b>：卡片与 PRD 都没给过限额，编一个「单笔最多 X 元」就是替规格发明数字
 *       （同 T09 不给住院号编正则、T14 不给充值编上限）。只校验正数：0 与负数会造出
 *       「充值成功但一分钱没进」的账，那是数据层面的错，不是业务偏好。</li>
 * </ul>
 *
 * <p>{@code amountFen} 用分不用元：与 {@code amount_fen} 列存分一致（V1:145），
 * 也让「客户端传小数元」这种精度陷阱在类型层面不存在（附录 B「金额有没有 FLOAT/DOUBLE」）。
 */
public class InpatientRechargeCreateRequest {

    @NotNull(message = "请选择住院人")
    private Long inpatientId;

    @NotNull(message = "请输入充值金额")
    @Positive(message = "充值金额必须大于 0")
    private Long amountFen;

    public Long getInpatientId() { return inpatientId; }
    public void setInpatientId(Long inpatientId) { this.inpatientId = inpatientId; }
    public Long getAmountFen() { return amountFen; }
    public void setAmountFen(Long amountFen) { this.amountFen = amountFen; }
}
