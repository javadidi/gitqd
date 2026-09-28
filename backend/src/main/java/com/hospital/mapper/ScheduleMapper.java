package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
