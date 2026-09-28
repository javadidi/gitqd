package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.PayNotifyRequest;
import com.hospital.dto.PaymentResultResponse;
import com.hospital.service.AppointmentPaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付结果回调（T12 B 段，卡片 455 行「独立接口，幂等」）。
 *
 * <p><b>这是全仓第一个 permitAll 的业务接口</b>（此前只有 {@code /auth/*} 三个），
 * 理由是调用方是微信服务器，它不可能带我们的 JWT。
 * 放行的那一条规则写在 {@code SecurityConfig} 里，只放这一个精确路径，
 * 不是 {@code /payments/**} 一整片——那个前缀下的其余路径仍然只认员工角色（T04 的靶接口就在那儿）。
 *
 * <p><b>本接口的安全边界完全压在"验签"这一步上</b>：接口没有任何身份凭证，
 * 谁都能 POST，能改动的只有"这一笔单号的状态"。所以
 * <ul>
 *   <li>验不过就抛异常拒绝整个请求（不是当成重复回调 ACK，见 service 的注释）；</li>
 *   <li>验过之后能做的也只有 PENDING_PAYMENT → CONFIRMED 这一格前进，
 *       金额取自己账上的、退款/取消/读隐私一律够不着；</li>
 *   <li>首版是 mock 验签，这个敞口真实存在，{@code MockWechatPayService} 启动即打 WARN，
 *       而且小程序不走这条路（走 {@code /user/appointments/{id}/pay}，要 token 且校验归属）。</li>
 * </ul>
 *
 * <p>响应体用本项目的 {@code Result} 形状。真实微信要求的是它自己的 ACK 格式
 * （V3 是 JSON {@code {"code":"SUCCESS"}}，V2 是 XML），接真通道时连 ACK 带验签一起换，
 * 属同一张卡，不在这里先搭一半。
 */
@RestController
@RequestMapping("/payments/wechat")
public class WechatNotifyController {

    private final AppointmentPaymentService paymentService;

    public WechatNotifyController(AppointmentPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 支付结果通知（J28：同一笔重复推送只处理一次） */
    @PostMapping("/notify")
    public Result<PaymentResultResponse> notifyPayment(@Valid @RequestBody PayNotifyRequest request) {
        return Result.success(paymentService.handleNotify(request));
    }
}
