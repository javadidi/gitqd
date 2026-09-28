package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {

    /**
     * 待支付 → 已确认，一条语句完成"判状态 + 改状态"。返回 1 = 这次真的推进了；0 = 状态不是待支付。
     *
     * <p><b>这是卡片 B 段「幂等」的唯一实现手段</b>（J28：重复回调 → 只处理一次）。
     * 回调是微信服务器发的，同一次支付可能重推好几次，网络抖动、对方重试策略、
     * 我们自己手动补发都会走到这里，所以"这笔单已经处理过了"必须能在数据库层面一次性判定。
     *
     * <p><b>不能用「先 selectById 看状态，再 updateById 改状态」</b>：两次回调可以同时通过状态检查，
     * 于是同一笔支付写出两条 {@code payment_record}（钱记了两遍），
     * 而 {@code payment_record.order_no} 在 V1:156-167 里<b>没有唯一索引</b>，数据库不会拦。
     * 把条件写进 WHERE 之后，读改写在一句 SQL 里完成，InnoDB 对目标行加 X 锁，
     * 第二个并发回调要么等它、要么拿到 0 行——两种情况都只会有一次生效。
     * 这与 {@code ScheduleMapper.occupySlot} 是同一个套路：<b>幂等性由受影响行数承载，不靠应用层判断</b>。
     *
     * <p>状态取值见 V1:124 列注释（PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED）。
     *
     * <p>{@code deleted = 0} 是手写的：自定义 SQL 不会被 MyBatis-Plus 的 {@code @TableLogic}
     * 自动追加逻辑删条件（推导见 {@code PatientMapper.reviveSoftDeletedByCardNo} 的注释）。
     */
    @Update("UPDATE appointment SET status = 'CONFIRMED' "
            + "WHERE id = #{id} AND status = 'PENDING_PAYMENT' AND deleted = 0")
    int confirmIfPending(@Param("id") Long id);
}
