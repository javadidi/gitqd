package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 把「同医生同日期同时段、已取消（软删）」的那一行复活，并覆盖号源。
     * 返回 1 = 复活成功，0 = 没有这样的行（调用方据此走正常 insert）。
     *
     * <p><b>为什么必须有这一步</b>：{@code uk_doctor_date_slot(doctor_id, date, time_slot)}
     * （V1__init.sql:111）<b>不含 deleted 列</b>，取消排班只是把 deleted 置 1，
     * 索引位仍被占着——不复活就永远没法给这位医生的这个时段重新排班，
     * 而任务卡 J26「取消排班 → 剩余号源恢复」的"恢复"正包含号源回到可排状态。
     * 与 T08-G 就诊人「本人同卡号可复活」是同一个模式，理由也同一个：
     * 历史 appointment 以 {@code schedule_id} 引用本表（V1:123），物理删会静默留下一堆指向不存在排班的预约。
     *
     * <p><b>为什么必须手写 SQL</b>：{@code BaseEntity.deleted} 上有 {@code @TableLogic}，
     * MyBatis-Plus 给它自己生成的每条 SELECT/UPDATE 都追加 {@code deleted = 0}，
     * {@code updateById} 永远碰不到软删行；逻辑删是 SQL 注入器写进语句的，
     * {@code @InterceptorIgnore} 管不到（PatientMapper.reviveSoftDeletedByCardNo 的注释里有完整推导）。
     *
     * <p>{@code updated_at} 不在 SET 里：自定义 SQL 不走 MetaObjectHandler，
     * 交给 DDL 的 {@code ON UPDATE CURRENT_TIMESTAMP(3)}。
     */
    @Update("UPDATE schedule SET deleted = 0, total_slots = #{totalSlots}, remaining_slots = #{remainingSlots} "
            + "WHERE doctor_id = #{doctorId} AND `date` = #{date} AND time_slot = #{timeSlot} AND deleted = 1")
    int reviveSoftDeleted(Schedule schedule);

    /**
     * 取消排班：一条语句同时置软删标记并把剩余号源归位到总号源。
     * 返回 1 = 取消成功，0 = 这行不存在或已被取消（并发取消时第二个请求拿到 0）。
     *
     * <p><b>为什么合成一条</b>：拆成「updateById 改号源 + deleteById 软删」是两次写，
     * 中间那一下若失败会留下一行 deleted=0 但号源已被改动的排班；
     * 单条 UPDATE 天然原子，且 {@code WHERE deleted = 0} 顺带把并发取消变成可判定的返回值。
     *
     * <p>{@code remaining_slots = total_slots} 是 J26「剩余号源恢复」的字面实现：
     * 调用方已保证这行下面没有未取消的预约，所以取消后它不该再占着任何号。
     */
    @Update("UPDATE schedule SET deleted = 1, remaining_slots = total_slots "
            + "WHERE id = #{id} AND deleted = 0")
    int cancelById(@Param("id") Long id);

    /**
     * 占一个号：把剩余号源原子减 1。返回 1 = 抢到，0 = 号已满（或这行已被取消）。
     *
     * <p><b>为什么必须是「一条 UPDATE 带 {@code remaining_slots > 0} 条件」</b>：
     * 卡片 453 行第⑥步「扣减剩余号源」是全局唯一一处号源减少，而 T12 的并发场景就是
     * 「N 个人同时点同一个时段」。如果写成「先 selectById 判 {@code remaining > 0}，
     * 再 updateById 写 {@code remaining - 1}」，这两步之间别人也在读同一个值，
     * 两条 UPDATE 都会写成就剩 0，实际却放出去两个号 —— 超卖。
     * 把判断挪进 WHERE，读改写就在 InnoDB 的行锁里合成一个原子动作，
     * 返回值 0/1 直接就是"我抢到了没有"，不需要额外加锁也不需要重试循环。
     *
     * <p>前置查（{@code ScheduleService} 与本项目 R2 那套「友好提示 + 数据库兜底」的两层规矩一样）
     * 仍然留在 service 里，但那只为了把"号已满"提前变成一条不带异常的消息；
     * <b>正确性只依赖本方法</b>。
     *
     * <p>回滚不需要配套的"加回去"语句：调用方是 {@code @Transactional} 的预约事务，
     * 事务内任何一步失败（含 J27 要求的支付异常）都由数据库回滚把这一下减掉还原。
     *
     * <p>{@code deleted = 0} 是手写的：自定义 SQL 不会被 MyBatis-Plus 的 {@code @TableLogic}
     * 自动追加逻辑删条件（{@link #reviveSoftDeleted} 的注释里有完整推导），必须自己带上，
     * 否则能给一条已取消的排班占号。
     */
    @Update("UPDATE schedule SET remaining_slots = remaining_slots - 1 "
            + "WHERE id = #{scheduleId} AND deleted = 0 AND remaining_slots > 0")
    int occupySlot(@Param("scheduleId") Long scheduleId);

    /**
     * 退号还号（T13）：把剩余号源加回一个。返回 1 = 还号成功；0 = 这行已不存在/已取消，或号源已经满了。
     *
     * <p>{@code remaining_slots < total_slots} 这个上界条件是<b>必须</b>的，理由和
     * {@link #occupySlot} 的 {@code remaining_slots > 0} 是对称的：没有它，
     * 一句写错的 SQL 或一次重复执行就能让"剩余号源"超过"总号源"，
     * 于是 {@code SeedCheckService} 的号源自检（{@code 号源 = 总 - 未取消预约数}）会失配，
     * 而患者端看到的是一个个根本不存在的名额。宁可这次还号失败让事务回滚，
     * 也不能让账本出现 {@code remaining > total} 这种状态。
     *
     * <p>调用方只在 {@code AppointmentMapper.cancelIfActive} 返回 1 之后才调本方法，
     * 所以"重复退号导致重复还号"这条路已经被掐掉；这里的上界是第二道保险。
     */
    @Update("UPDATE schedule SET remaining_slots = remaining_slots + 1 "
            + "WHERE id = #{scheduleId} AND deleted = 0 AND remaining_slots < total_slots")
    int releaseSlot(@Param("scheduleId") Long scheduleId);

    /**
     * 按 id 批量读排班，**故意不过滤软删行**（T25）。
     *
     * <p><b>为什么这里要绕过 {@code @TableLogic}</b>：T11 的取消排班有 2007 守卫
     * （班下还有未取消的预约就撤不掉），所以在 T11 的世界里"已软删的班"永远没有活预约，
     * 读它没关系。T25 的<b>停诊</b>恰好造出了这个前所未有的状态：
     * 班被撤了（deleted=1），但那些预约作为历史事实还在，而且患者和后台都还要继续看它们。
     * 此时若沿用 {@code selectBatchIds}，被停的那个班下的预约在列表里就会
     * <b>集体丢掉就诊日期与时段</b>（实测就是详情页那两格变成「— —」）——
     * 停诊这个动作本身不该改写历史的显示。
     *
     * <p><b>为什么不去 {@code @TableLogic} 上动刀</b>：那个注解管的是"这个班还能不能排号、
     * 还在不在排班列表里"，那些读法全部必须继续过滤，改一处就会漏十处。
     * 手写 SQL 不受逻辑删注入影响（{@link #reviveSoftDeleted} 的注释里有完整推导），
     * 所以只在这一个读口上开口子，口子边界由调用点决定。
     *
     * <p>与 {@link #selectIdsByDateRange} 成对：一个供"按 id 反查名字"，一个供"按日期筛预约"。
     */
    @Select("<script>SELECT * FROM schedule WHERE id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
            + "</script>")
    List<Schedule> selectByIdsIncludingDeleted(@Param("ids") Collection<Long> ids);

    /**
     * 日期区间内的排班 id，**含已停诊的班**（T25）。理由同
     * {@link #selectByIdsIncludingDeleted}：管理员按就诊日期筛预约时，
     * 被停那天的预约不该因为班没了就从结果里蒸发——那正是他最该看一眼的一批。
     *
     * <p>两个参数各自可为 null（{@code &gt;=} / {@code &lt;} 在 {@code <if>} 里，
     * XML 里的比较号必须转义）。
     */
    @Select("<script>SELECT id FROM schedule WHERE 1 = 1"
            + "<if test=\"dateFrom != null\"> AND `date` &gt;= #{dateFrom}</if>"
            + "<if test=\"dateTo != null\"> AND `date` &lt;= #{dateTo}</if>"
            + "</script>")
    List<Long> selectIdsByDateRange(@Param("dateFrom") LocalDate dateFrom,
                                    @Param("dateTo") LocalDate dateTo);
}
