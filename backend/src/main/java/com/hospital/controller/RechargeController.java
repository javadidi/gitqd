package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.RechargeCreateRequest;
import com.hospital.dto.RechargeResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.RechargeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的门诊充值接口（T14）：{@code /user/recharges}。
 *
 * <p><b>三个端点对应卡片 493–496 行的三件事</b>：
 * <ul>
 *   <li>{@code POST /} = 充值页面提交（建单 + 支付 + 到账，一个事务，J33）；</li>
 *   <li>{@code GET /} = 充值记录（卡片 496 行 / PRD 296 行）；</li>
 *   <li>{@code GET /{id}} = 账单详情（PRD 297 行）。</li>
 * </ul>
 *
 * <p><b>没有独立的"支付回调"端点</b>：PRD §9.1 第 611 行「充值缴费 | 创建充值订单、支付回调、
 * 缴费列表、缴费详情」里确实写了"支付回调"，但回调是<b>通道</b>不是业务——T12 已经建好
 * {@code /payments/wechat/notify}，真实微信支付落地时它按 {@code out_trade_no} 的前缀
 * （{@code YY}=挂号、{@code CF}=充值）分流处理即可，本卡不该为"充值"再造一个回调入口。
 * 现在充值是同步支付（mock 桩），到账就发生在 {@code POST /} 这一个事务里。
 *
 * <p><b>没有"缴费列表/缴费详情"</b>：那是 §9.1 同一格里的后两项，属 T15「自助缴费」；
 * 卡片 498 行红线也明写「不做缴费（T15）；不做退款（T19）」，所以这里也不会有余额扣减接口。
 */
@RestController
@RequestMapping("/user/recharges")
public class RechargeController {

    private final RechargeService rechargeService;

    public RechargeController(RechargeService rechargeService) {
        this.rechargeService = rechargeService;
    }

    /** 充值并即时到账（J33）；返回体带到账后的余额，成功页要拿它当"钱真进卡了"的证据 */
    @PostMapping
    public Result<RechargeResponse> recharge(@Valid @RequestBody RechargeCreateRequest request) {
        return Result.success(rechargeService.recharge(SecurityUtils.currentUserId(), request));
    }

    /** 充值记录列表（本人全部就诊人） */
    @GetMapping
    public Result<List<RechargeResponse>> list() {
        return Result.success(rechargeService.list(SecurityUtils.currentUserId()));
    }

    /** 账单详情（单笔充值） */
    @GetMapping("/{id}")
    public Result<RechargeResponse> detail(@PathVariable Long id) {
        return Result.success(rechargeService.detail(SecurityUtils.currentUserId(), id));
    }
}
