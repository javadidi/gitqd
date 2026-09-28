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

    /**
     * 退号（T13）：待支付 / 已确认 → 已取消。返回 1 = 这次真的取消了；0 = 状态不允许。
     *
     * <p>与 {@link #confirmIfPending} 同一个手法：把"当前状态能不能改"写进 WHERE，
     * 让判定和改动在一次数据库操作里完成。退号这条路上"改状态 + 恢复号源 + 写退款单"
     * 必须整体发生一次且仅一次——患者连点两下退号如果落进两条 UPDATE，
     * 号源会被加回去两次（凭空多出名额），退款单也会出现两条。
     * 所以第二个请求在这里拿到 0 行，调用方据此什么都不做。
     *
     * <p>{@code COMPLETED}（已就诊）<b>故意不在这个集合里</b>：卡片 479 行红线「已就诊不可退号」。
     * 把它留在 SQL 层而不是只写在 Java 里，是因为 SQL 是最后一道——
     * 任何绕过 service 的调用（将来的批量脚本、后台运维）也照样改不动已就诊的单。
     */
    @Update("UPDATE appointment SET status = 'CANCELLED' "
            + "WHERE id = #{id} AND status IN ('PENDING_PAYMENT', 'CONFIRMED') AND deleted = 0")
    int cancelIfActive(@Param("id") Long id);

    /**
     * 退号后重约同一个班（T13-C）：把那条 CANCELLED 行复活成待支付，并换发新单号。
     * 返回 1 = 复活成功；0 = 这行已经不是 CANCELLED（并发下被别人抢先了）。
     *
     * <p><b>为什么要复活而不是新插一条</b>：{@code uk_patient_schedule(patient_id, schedule_id)}
     * （V1:130）<b>不看 status</b>，已取消的行仍永久占着这个索引位——不复活的话，
     * 患者退号之后再想约同一个班永远只能拿到 2005，等于"退号即拉黑"。
     * 而 PRD 86 行的原话是「同一就诊人同一时间段<b>不可重复预约</b>」，
     * 说的是"不得同时持有两张有效预约"，不是"取消后永久不能再见这个班"。
     * 这与 T08-G 就诊人「本人同卡号可复活」、T11 排班「取消过的槽位可重排」是同一种形状：
     * <b>唯一索引不含 deleted/status 时，复活是唯一的出路</b>。
     *
     * <p><b>换发新 {@code order_no} 是必须的</b>：旧单号已经在 {@code payment_record} 和
     * {@code refund_record} 里被引用（{@code payment_record.order_no} 就是靠它关联预约的，
     * 见 {@code AppointmentPaymentService.bookPayment}）。如果复用旧单号，
     * 第二次支付的流水会和第一次的流水撞在同一个号上，退款审核就分不清哪笔对应哪次支付。
     *
     * <p>{@code fee_fen} 与 {@code appointment_time} 一并覆盖：职称定价可能已经变了，
     * 排班时段也不会变，但按"以本次请求为准"整体重算比留旧值更诚实。
     */
    @Update("UPDATE appointment SET status = 'PENDING_PAYMENT', order_no = #{orderNo}, "
            + "fee_fen = #{feeFen}, appointment_time = #{appointmentTime} "
            + "WHERE id = #{id} AND status = 'CANCELLED' AND deleted = 0")
    int reviveCancelled(@Param("id") Long id, @Param("orderNo") String orderNo,
                        @Param("feeFen") Long feeFen,
                        @Param("appointmentTime") java.time.LocalDateTime appointmentTime);
}
