package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.InpatientRechargeCreateRequest;
import com.hospital.dto.InpatientRechargeResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.InpatientRechargeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的住院充值接口（T23）：{@code /user/inpatient-recharges}。
 *
 * <p><b>三个端点的出处</b>：
 * <ul>
 *   <li>{@code POST /} = 卡片 657 行「选择住院人，输入充值金额，支付」+ J51；</li>
 *   <li>{@code GET /} = PRD 300 行「充值记录列表 — 展示住院充值历史」（个人中心，
 *       {@code mine.js:33} 那条「住院充值记录」占位入口由本卡接上）；</li>
 *   <li>{@code GET /{id}} = PRD 301 行「账单详情 — 查看单笔充值明细」。</li>
 * </ul>
 * 列表上的 {@code ?inpatientId=} 不是第四个端点，是 PRD 232-233 行
 * 「选择住院人员 → 住院记录」这条链路的筛选：个人中心不带它（看本人全部住院人），
 * 住院服务带它（只看这一个住院人）。附录 B「列表筛选是否进 URL」要求它可分享、刷新不丢。
 *
 * <p><b>为什么不开 {@code /user/recharges?type=INPATIENT}</b>：T14 的
 * {@code RechargeService.list} 按「我的就诊人 id 集合」过滤 {@code patient_id}，
 * 而住院单的 {@code patient_id} 恒为 NULL——两条链路在 SQL 层面天然互斥，
 * 硬塞进一个控制器只会让「门诊充值记录」页有机会读到住院单，反之也一样。
 * 分开还有一处好处：住院侧没有余额可回（{@code inpatient} 表无余额列），
 * 两个出参形状本就不同，共用一个 DTO 就得留一个恒为 NULL 的 {@code balanceFen}。
 *
 * <p><b>没有「支付回调」端点</b>：与 T14 同一条理由——回调是通道不是业务，
 * {@code /payments/wechat/notify} 已在 T12 建好，按单号前缀分流即可。
 * 现在充值是同步 mock 支付，到账发生在 {@code POST /} 这一个事务里。
 *
 * <p><b>没有「费用详情 / 住院日清单」端点</b>：卡片 659-660 行要这两件事，
 * 但 V1 的 28 张表里没有任何住院费用或每日清单表，PRD §八 数据字典（575-596 行）
 * 也没有这两行，T26（卡片 718-719 行）只负责展示、不负责产生。
 * 没有数据源就不编接口，判断与证据记在 WORK_LOG 的 T23 段。
 */
@RestController
@RequestMapping("/user/inpatient-recharges")
public class InpatientRechargeController {

    private final InpatientRechargeService inpatientRechargeService;

    public InpatientRechargeController(InpatientRechargeService inpatientRechargeService) {
        this.inpatientRechargeService = inpatientRechargeService;
    }

    /** 住院充值并留流水（J51）；不返回余额，因为住院侧没有余额这个东西 */
    @PostMapping
    public Result<InpatientRechargeResponse> recharge(
            @Valid @RequestBody InpatientRechargeCreateRequest request) {
        return Result.success(inpatientRechargeService.recharge(SecurityUtils.currentUserId(), request));
    }

    /** 住院充值记录；{@code inpatientId} 为空即本人全部住院人的流水 */
    @GetMapping
    public Result<List<InpatientRechargeResponse>> list(
            @RequestParam(required = false) Long inpatientId) {
        return Result.success(inpatientRechargeService.list(SecurityUtils.currentUserId(), inpatientId));
    }

    /** 账单详情（单笔住院充值） */
    @GetMapping("/{id}")
    public Result<InpatientRechargeResponse> detail(@PathVariable Long id) {
        return Result.success(inpatientRechargeService.detail(SecurityUtils.currentUserId(), id));
    }
}
