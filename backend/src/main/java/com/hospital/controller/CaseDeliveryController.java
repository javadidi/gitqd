package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.CaseDeliveryCreateRequest;
import com.hospital.dto.CaseDeliveryResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.CaseDeliveryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的病案配送接口（T23）：{@code /user/case-deliveries}。
 *
 * <p><b>三个端点的出处</b>：
 * <ul>
 *   <li>{@code POST /} = 卡片 661 行的「填写邮寄申请信息」+ J52；</li>
 *   <li>{@code GET /} = PRD 315 行「申请记录列表」（个人中心 {@code mine.js:36}
 *       那条「病案邮寄记录」占位入口由本卡接上）；</li>
 *   <li>{@code GET /{id}} = PRD 316 行「申请详情 — 查看申请详情及物流状态」。</li>
 * </ul>
 *
 * <p><b>「物流查询」不是第四个端点</b>：PRD 619 行那一格写了「创建配送申请、配送详情、物流查询」，
 * 但本系统里"物流"这件事只有一个落点——{@code case_delivery.tracking_no}（V1:335 快递单号），
 * 它随详情一起回。要"查询物流轨迹"得接承运商，那是附录 A 二期「消息推送」同一级的外部依赖，
 * 首版一律不做。所以详情接口就是物流状态的唯一出口，不额外造一个只会回同一个字符串的端点。
 *
 * <p><b>没有「支付配送费」端点</b>：卡片 661 行有这句话，但表没有费用列、字典没有这一项、
 * {@code payment_record} 又要求 {@code patient_id NOT NULL} 而住院人没有就诊人关联，
 * 结构上无处记账。判断与三处证据记在 {@code CaseDeliveryService} 的类注释与 WORK_LOG 的 T23 段。
 */
@RestController
@RequestMapping("/user/case-deliveries")
public class CaseDeliveryController {

    private final CaseDeliveryService caseDeliveryService;

    public CaseDeliveryController(CaseDeliveryService caseDeliveryService) {
        this.caseDeliveryService = caseDeliveryService;
    }

    /** 提交病案邮寄申请（J52）；状态固定 PENDING，寄出与签收归后台 */
    @PostMapping
    public Result<CaseDeliveryResponse> create(@Valid @RequestBody CaseDeliveryCreateRequest request) {
        return Result.success(caseDeliveryService.create(SecurityUtils.currentUserId(), request));
    }

    /** 申请记录列表（本人全部住院人） */
    @GetMapping
    public Result<List<CaseDeliveryResponse>> list() {
        return Result.success(caseDeliveryService.list(SecurityUtils.currentUserId()));
    }

    /** 申请详情（含物流单号，如果后台已填） */
    @GetMapping("/{id}")
    public Result<CaseDeliveryResponse> detail(@PathVariable Long id) {
        return Result.success(caseDeliveryService.detail(SecurityUtils.currentUserId(), id));
    }
}
