package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecord> {

    /**
     * 退款审核：把一张待审核单推进到 APPROVED / REJECTED，同时落下审核人（T26）。
     * 返回 1 = 本次审核成立；0 = 这张单不是 PENDING（没人能审两次）。
     *
     * <p><b>{@code WHERE status = 'PENDING'} 是本卡唯一的并发防线</b>：
     * 与 {@code RechargeRecordMapper.markSuccess}、{@code PaymentRecordMapper.markPaidByBalance}、
     * {@code AppointmentMapper.confirmIfPending} 同族——判定和写入在一条语句里完成。
     * 后台是多人同岗的场合（两个财务同时开着同一张退款单的详情页），
     * "先读一遍状态确认还是 PENDING、再 updateById 改"会两个人都改成功、
     * 审计里出现两条都说"是我批的"的记录。用影响行数判定之后，输家拿 0 → 3006。
     *
     * <p>{@code updated_at} 不在 SET 里：自定义 SQL 不走 MP 的自动填充，
     * 交给 DDL 的 {@code ON UPDATE CURRENT_TIMESTAMP(3)}（V1:182）。
     */
    @Update("UPDATE refund_record SET status = #{toStatus}, reviewer_id = #{reviewerId} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int review(@Param("id") Long id, @Param("toStatus") String toStatus,
               @Param("reviewerId") Long reviewerId);
}
