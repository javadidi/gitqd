package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Patient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {

    /**
     * 把「本人已软删、且卡号相同」的那一行复活，并用传入的姓名/关系/身份证/手机号覆盖。
     * 返回 1 = 复活成功，0 = 没有这样的行（调用方据此走正常 insert）。
     *
     * <p><b>为什么必须手写 SQL</b>：{@code BaseEntity.deleted} 上有 {@code @TableLogic}，
     * MyBatis-Plus 会给它自己生成的每一条 SELECT/UPDATE 追加 {@code deleted = 0}，
     * 所以 {@code updateById} 永远碰不到软删行。而逻辑删是 MP 的 SQL 注入器直接写进语句的，
     * 不是拦截器，{@code @InterceptorIgnore} 那套开关管不到它——3.5.5 没有「本次查询忽略逻辑删」的口子。
     * 手写 SQL 是唯一出路，也是本仓库第一条自定义 SQL。
     *
     * <p><b>为什么用一条 UPDATE 而不是「先查软删行再改」</b>：单条语句天然原子，
     * 不存在「查的时候还没有、改的时候已被别人复活」的窗口。{@code uk_card_no} 建在 card_no 单列上，
     * 全库同一卡号最多一行，所以这里最多影响 1 行，不用担心批量误伤。
     *
     * <p>{@code updated_at} 不在 SET 里：走自定义 SQL 时 MP 的 {@code MetaObjectHandler} 自动填充不生效，
     * 交给 DDL 的 {@code ON UPDATE CURRENT_TIMESTAMP(3)}。
     */
    @Update("UPDATE patient SET deleted = 0, name = #{name}, relation = #{relation}, "
            + "id_card = #{idCard}, phone = #{phone} "
            + "WHERE card_no = #{cardNo} AND user_id = #{userId} AND deleted = 1")
    int reviveSoftDeletedByCardNo(Patient patient);

    /**
     * 充值到账：把就诊卡余额原子加一笔（T14）。返回 1 = 加成功；0 = 这个就诊人不属于该用户（或不存在/已删）。
     *
     * <p><b>为什么把归属写进 UPDATE 的 WHERE 而不是先查一遍</b>：这条语句同时干两件事——
     * 校验"这张卡是你的"和"把钱加进去"。如果拆成"先 selectOne 判归属、再 updateById 加钱"，
     * 两次操作之间卡被删或归属判定基于旧快照，就会出现"给别人的卡加了钱"或"加给了已删除的行"。
     * 合成一条之后，判定和写入在 InnoDB 同一把行锁里完成，返回值 0 就是唯一的"没加成"信号。
     *
     * <p>{@code balance_fen = balance_fen + #{amountFen}} 而不是"读出来加完写回去"，
     * 是为了让并发充值变成累加而不是相互覆盖（两次充值各加一次，不会只加最后一笔）。
     * 这与 {@code ScheduleMapper.occupySlot} / {@code releaseSlot} 是同一族写法：
     * <b>账本的增减只在数据库里做一次算术</b>。
     *
     * <p>本卡只有加法。T15 缴费要加的是减法版本，且必须带
     * {@code AND balance_fen >= #{amountFen}} 下界守卫，否则并发能把余额花成负数。
     */
    @Update("UPDATE patient SET balance_fen = balance_fen + #{amountFen} "
            + "WHERE id = #{patientId} AND user_id = #{userId} AND deleted = 0")
    int addBalance(@Param("patientId") Long patientId, @Param("userId") Long userId,
                   @Param("amountFen") long amountFen);

    /**
     * 缴费扣减（T15 J36）：余额够才扣。返回 1 = 扣成功；0 = 没扣成。
     *
     * <p><b>{@code balance_fen >= #{amountFen}} 是这张卡的地板</b>，它把"够不够"放进 UPDATE 自己的
     * 判定条件里。若改成"先读余额、在 Java 里比大小、再写回差额"，两笔并发缴费会各自看到同一个
     * 充足余额、各自扣一笔，余额就成负数——这正是 T14 否决派生余额时预留的半个句号。
     * 与 {@link #addBalance} / {@code ScheduleMapper.occupySlot} 同族：<b>账本的增减只在数据库里做一次算术</b>。
     *
     * <p><b>返回 0 的三种原因里只有第一种需要在乎</b>：余额不足（业务拒绝）、卡被并发删除、
     * 卡不属于这个人。调用方（{@code OutpatientPaymentService}）在同一事务里已按
     * {@code (id, user_id, deleted=0)} 读过一次就诊人，后两种到这里几乎不可能发生；
     * 真发生时报"余额不足"也不会多扣钱，失败方向是安全的。
     */
    @Update("UPDATE patient SET balance_fen = balance_fen - #{amountFen} "
            + "WHERE id = #{patientId} AND user_id = #{userId} AND deleted = 0 "
            + "AND balance_fen >= #{amountFen}")
    int deductBalance(@Param("patientId") Long patientId, @Param("userId") Long userId,
                      @Param("amountFen") long amountFen);

    /**
     * 按 id 批量读就诊人，**不过滤软删行**。
     *
     * <p>给 T26 的费用流水列表用：流水表（{@code payment_record}/{@code recharge_record}/
     * {@code refund_record}）在 V1 里就没有 deleted 列、只增不删，而 T08 允许患者删除就诊人——
     * 于是"钱还在、人已被删"是正常状态。按默认读法这些行的姓名列会集体空掉，
     * 管理员看到的账本就会出现一批不知道是谁的钱。
     * 手写 SQL 不受 {@code @TableLogic} 注入的影响，理由见
     * {@code ScheduleMapper.selectByIdsIncludingDeleted} 与 {@link #reviveSoftDeletedByCardNo} 的注释。
     *
     * <p>只读名字与卡号用途：本方法不返回 idCard/phone（那两列在库里是密文，
     * 解不解由调用方决定，费用列表不需要）。
     */
    @Select("<script>SELECT * FROM patient WHERE id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
            + "</script>")
    List<Patient> selectByIdsIncludingDeleted(@Param("ids") java.util.Collection<Long> ids);
}
