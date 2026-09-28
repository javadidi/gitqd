package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 支付回调入参（T12 B 段，卡片 455-456 行）。
 *
 * <p><b>字段名故意按"微信回调里有什么"来定，不按我们内部叫什么</b>：
 * 微信 V3 的回调是 {@code {id, resource:{ciphertext, ...}}} 的加密结构，
 * 解密后才有 {@code out_trade_no}/{@code transaction_id}/{@code amount} 这些字段。
 * 首版真实对接属附录 A「不做」，所以这里只把它的最外层形状立起来：
 * {@code orderNo} = 商户订单号（就是我们自己的 {@code appointment.order_no}），
 * {@code tradeNo} = 微信支付流水号，{@code signature} = 验签用的签名串。
 *
 * <p><b>没有金额字段，而且将来接真实回调也不要拿它记账</b>（卡片 458 行「支付金额禁篡改」在回调侧的那一半）：
 * 写支付流水用的金额只从 {@code appointment.fee_fen} 取，回调里带的金额最多用于"对不上就报警"。
 * 真实通道落地时要加的一步校验就写在这段注释上，别让下一个接手的人重新发现。
 *
 * <p>{@code signature} 允许为空：mock 模式无从验签（{@code MockWechatPayService} 的类注释记着这笔账
 * 与随之而来的启动 WARN），而真实验签必须有值——所以这里不加 {@code @NotBlank}，
 * 把"要不要签名"的判断留给验签实现本身，不在入参层写死，免得真实通道来了还得改校验。
 */
public class PayNotifyRequest {

    @NotBlank(message = "缺少商户订单号")
    @Size(max = 64)
    private String orderNo;

    /** 微信的.result_code 语义：SUCCESS / FAIL。 */
    @NotBlank(message = "缺少支付结果码")
    @Size(max = 32)
    private String returnCode;

    @Size(max = 128)
    private String tradeNo;

    @Size(max = 512)
    private String signature;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getReturnCode() { return returnCode; }
    public void setReturnCode(String returnCode) { this.returnCode = returnCode; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
