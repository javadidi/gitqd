package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.OutpatientPaymentResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.OutpatientPaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的门诊自助缴费接口（T15）：{@code /user/payments}。
 *
 * <p><b>四个端点对应卡片 511–514 行的四件事</b>：
 * <ul>
 *   <li>{@code GET /pending} = 待缴费项目列表（卡片 511 行）；</li>
 *   <li>{@code GET /{id}} = 确认缴费信息页要的单条明细（卡片 512 行 / PRD 115 行），
 *       同一个端点也服务缴费详情（PRD 289 行）；</li>
 *   <li>{@code POST /{id}/pay} = 缴费（卡片 513 行，余额支付 → 扣减 → 写记录 → 审计，一个事务）；</li>
 *   <li>{@code GET /} = 缴费记录（卡片 514 行 / PRD 288 行）。</li>
 * </ul>
 *
 * <p>字面量路径 {@code /pending} 的匹配优先级高于模板 {@code /{id}}（Spring 的
 * {@code PathPattern}  specificity 规则），所以不会出现"pending 被当成 id"的歧义。
 *
 * <p><b>{@code pay} 用 POST 子资源而不是 {@code PUT /{id}}</b>：这一行动的是状态跃迁
 * （PENDING → SUCCESS）并附带一次余额扣减，不是把单据字段改成客户端给的值。
 * 与 T12 的 {@code POST /{id}/pay}、T13 的 {@code POST /{id}/cancel} 同一个形状。
 * 且它<b>没有请求体</b>——金额、就诊人、支付方式全由服务端从单据和自己的账上算，
 * 客户端唯一能决定的只有"缴哪一张"。
 *
 * <p>URL 里没有 {@code userId}：一律取 {@link SecurityUtils#currentUserId()}（附录 B
 * 「小程序端新接口是否强制注入 userId 归属校验」）。{@code /user/**} 的角色隔离由 T07 建好的
 * {@code SecurityConfig} 规则继承，本卡<b>一行安全配置都没改</b>。
 *
 * <p>与员工侧的 {@code /payments/{id}}（T04 的金额裁剪靶接口，返回 mock 数据）不是同一组路径，
 * 两者互不影响。
 */
@RestController
@RequestMapping("/user/payments")
public class OutpatientPaymentController {

    private final OutpatientPaymentService paymentService;

    public OutpatientPaymentController(OutpatientPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 待缴费项目列表（只回 PENDING 的行，带项目明细）。 */
    @GetMapping("/pending")
    public Result<List<OutpatientPaymentResponse>> pending() {
        return Result.success(paymentService.pendingList(SecurityUtils.currentUserId()));
    }

    /** 缴费记录列表（本人全部就诊人的全部缴费流水）。 */
    @GetMapping
    public Result<List<OutpatientPaymentResponse>> list() {
        return Result.success(paymentService.list(SecurityUtils.currentUserId()));
    }

    /** 确认缴费信息 / 缴费详情：单条明细 + 该就诊人此刻的余额。 */
    @GetMapping("/{id}")
    public Result<OutpatientPaymentResponse> detail(@PathVariable Long id) {
        return Result.success(paymentService.detail(SecurityUtils.currentUserId(), id));
    }

    /** 用就诊卡余额缴费（J35 / J36）。返回体里的 {@code balanceFen} 是<b>扣费后余额</b>。 */
    @PostMapping("/{id}/pay")
    public Result<OutpatientPaymentResponse> pay(@PathVariable Long id) {
        return Result.success(paymentService.pay(id, SecurityUtils.currentUserId()));
    }
}
