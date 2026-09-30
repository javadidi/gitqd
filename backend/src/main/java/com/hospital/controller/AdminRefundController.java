package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminRefundResponse;
import com.hospital.enums.Capability;
import com.hospital.service.AdminFinanceQueryService;
import com.hospital.service.RefundReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端退款记录 + 审核（T26 卡片 721 行 / PRD 4.4.6 的 389–390 行两页）。四把端点。
 *
 * <h2>读与写分开挂能力，这是 PRD 389 与 390 两行的分界</h2>
 * <ul>
 *   <li>列表（389 行「展示退款申请记录」）：员工角色即可，不要能力。
 *       护士要能在自己的页面上看到退款进度，这与 T25 的排班列表同一判据；
 *       金额那一列由裁剪层对护士置空。</li>
 *   <li>审核（390 行「支持审核通过/拒绝」）：{@code @RequireCap(APPROVE_REFUND)}。
 *       这是这个能力枚举（{@code Capability} 的中文 label 正是"审批退款"）
 *       第一次挂在<b>真业务</b>上——此前它只出现在 T04 的靶接口
 *       {@code GET /payments/{id}/refund-approve} 上，那个接口返回一句硬编码字符串。
 *       doctor/nurse 的 caps 为空 → 4001，切面在 service 之前拦，改前端藏按钮不影响这一层。</li>
 * </ul>
 *
 * <h2>审核两把端点不收任何入参</h2>
 * 通过/拒绝这个动作本身不需要载荷：金额在挂单时就定死了（{@code RefundTicketService}），
 * 原因（{@code refund_record.reason}）是患者申请时写的，审核人没有第二个原因列可写
 * （V1:172-183 只有八列，没有"审核意见"）。
 * 审核人更是只能从 token 取（见 {@code SecurityUtils.currentAdminId()}）。
 * 所以路径里那两个词就是全部输入，多一个 body 就是多一个能被篡改的口子。
 */
@RestController
@RequestMapping("/admin/refunds")
public class AdminRefundController {

    private final AdminFinanceQueryService adminFinanceQueryService;
    private final RefundReviewService refundReviewService;

    public AdminRefundController(AdminFinanceQueryService adminFinanceQueryService,
                                 RefundReviewService refundReviewService) {
        this.adminFinanceQueryService = adminFinanceQueryService;
        this.refundReviewService = refundReviewService;
    }

    /** 退款记录列表（PRD 389 行）。 */
    @GetMapping
    public Result<List<AdminRefundResponse>> list() {
        return Result.success(adminFinanceQueryService.listRefunds());
    }

    /** 退款详情（PRD 390 行前半句）。 */
    @GetMapping("/{id}")
    public Result<AdminRefundResponse> detail(@PathVariable Long id) {
        return Result.success(adminFinanceQueryService.refundDetail(id));
    }

    /** 审核通过（PRD 390 行后半句 / J58）。改完再读一次详情出口，前端不必二次请求。 */
    @PostMapping("/{id}/approve")
    @RequireCap(Capability.APPROVE_REFUND)
    public Result<AdminRefundResponse> approve(@PathVariable Long id) {
        refundReviewService.approve(id);
        return Result.success(adminFinanceQueryService.refundDetail(id));
    }

    /** 审核拒绝（J58 的另一半）。 */
    @PostMapping("/{id}/reject")
    @RequireCap(Capability.APPROVE_REFUND)
    public Result<AdminRefundResponse> reject(@PathVariable Long id) {
        refundReviewService.reject(id);
        return Result.success(adminFinanceQueryService.refundDetail(id));
    }
}
