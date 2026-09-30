package com.hospital.service;

import com.hospital.common.ErrorCode;
import com.hospital.entity.RefundRecord;
import com.hospital.exception.BizException;
import com.hospital.mapper.RefundRecordMapper;
import com.hospital.security.SecurityUtils;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 退款审核（T26 卡片 721 行「退款记录/详情：支持审核通过/拒绝」/ PRD 390 行同句 /
 * §9.1 631 行「退款审核」/ J58「退款审核 → 状态更新」）。
 *
 * <h2>审核只改状态，不出钱——这三处证据是同一句话</h2>
 * PRD 390 行的原文是「查看退款明细，<b>支持审核通过/拒绝</b>」，一个"退"字都没有；
 * J58 的判定口径写的是「<b>状态更新</b>」；卡片 721 行同样只到"通过/拒绝"。
 * 真正要把钱退回微信钱包的是 PRD 136–142 行「在线退款」那节
 * （141 行「退款金额原路返回微信钱包」），而那是小程序侧的功能，
 * 依赖微信退款 API——与 T14/T15 定的"真实通道不落地"是同一条二期边界。
 *
 * <p>所以 approve 之后：{@code refund_record.status} 变 APPROVED、{@code reviewer_id} 落下审核人，
 * {@code patient.balance_fen} 一分不动、{@code payment_record}/{@code recharge_record} 一分不动。
 * V1:179 的第四个取值 {@code COMPLETED} 属于"钱真的出出去了"，
 * <b>产品代码里没有任何路径写它</b>——库里现存那一行 COMPLETED 是 {@code seed.sql:235} 的演示数据，
 * 不是哪个接口跑出来的。主人与 T16 的 {@code queue_status}、T22 的三张体检表同一类"有主的空"：
 * 微信退款落地那天。测试里这条是用"改状态前后钱表四个数字一个不变"来断言的，不是靠注释说说。
 *
 * <h2>为什么状态机只认 PENDING 一个入口</h2>
 * V1:179 的取值域是 PENDING/APPROVED/REJECTED/COMPLETED 四个。本卡开的两条边：
 * PENDING→APPROVED、PENDING→REJECTED。<b>不做 APPROVED→REJECTED 的反悔、
 * 也不做 REJECTED→APPROVED 的重审</b>：规格没有任何一处写过"改判"，
 * 而一次改判在财务上等于推翻别人已经做过的动作——这种事要真做，
 * 需要的是"谁改判、为什么改判"的第二次留痕，规格给不出这个理由。
 * 二次点击的后果就是 3006，让第二个人去刷新列表看结果。
 *
 * <h2>审核人只能从 token 里取</h2>
 * {@code reviewer_id}（V1:180）是业务表里第一列记"谁批的"的字段，
 * 一旦能从请求参数传，就等于任何持有能力的人可以把审核记录挂到别的管理员名下。
 * 取法见 {@link SecurityUtils#currentAdminId()}。
 *
 * <h2>审计的 action 名与 T04 的靶接口重名，这是有意还是巧合</h2>
 * T04 的 {@code PaymentService.approveRefund} 早就占用了 {@code APPROVE_REFUND} 这个 action，
 * 但它的 {@code target_type} 是 {@code payment_record}，而这里写的是 {@code refund_record}——
 * 退款单在 V1 里就是 {@code refund_record} 这张表，T04 那个是靶接口，本来就在演一件不存在的事。
 * 两个 target_type 天然分得开，测试按 {@code action + target_type + target_id} 三元组精确计数
 * （WORK_LOG 里 T04 那条"审计基线"就写明了总数断言会误判）。
 */
@Service
public class RefundReviewService {

    /** V1:179 取值域里的两个终态（对本卡而言）。 */
    static final String APPROVED = "APPROVED";
    static final String REJECTED = "REJECTED";

    private final RefundRecordMapper refundRecordMapper;

    public RefundReviewService(RefundRecordMapper refundRecordMapper) {
        this.refundRecordMapper = refundRecordMapper;
    }

    /** 审核通过（J58）。 */
    @AuditLog(action = "APPROVE_REFUND", targetType = "refund_record")
    @Transactional
    public void approve(@AuditTarget Long refundId) {
        review(refundId, APPROVED);
    }

    /** 审核拒绝（J58 的另一半）。 */
    @AuditLog(action = "REJECT_REFUND", targetType = "refund_record")
    @Transactional
    public void reject(@AuditTarget Long refundId) {
        review(refundId, REJECTED);
    }

    private void review(Long refundId, String toStatus) {
        Long reviewerId = SecurityUtils.currentAdminId();
        int updated = refundRecordMapper.review(refundId, toStatus, reviewerId);
        if (updated == 1) {
            return;
        }
        // 影响 0 行有两种原因，分开报：没有这张单（5001）与这张单早被人审过（3006）。
        // 只在这条分支里补一次读——它不在正常路径上，为并发输家多读一行不亏。
        RefundRecord existing = refundRecordMapper.selectById(refundId);
        if (existing == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        throw new BizException(ErrorCode.REFUND_ALREADY_REVIEWED);
    }
}
