package com.hospital.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 数据看板的聚合查询（T28 卡片 767 行 / PRD 4.2 的 335–340 行）。
 *
 * <p><b>这一份 SQL 是全项目唯一的指标定义处</b>（卡片 769 行的红线、附录 B 第 803 行的检查项），
 * 所以每个方法的口径、为什么这么算、以及规格里哪一句撑着它，都写在
 * {@code DashboardMetricsService} 的类注释表里——那才是给人读的那一份；
 * 这里只保证"除本接口之外没有第二处 SUM/COUNT"。
 *
 * <p>不继承 {@code BaseMapper}：本接口没有实体，八条查询全是聚合，
 * 用 MyBatis-Plus 的条件构造器反而写不出 SUM。{@code SeedCheckService} 里那四条自检 SQL
 * 是同类先例（它用的是 JdbcTemplate，因为那四条要打印给人看）。
 *
 * <p><b>"今天"以数据库的时钟为准</b>：八条查询全部用 {@code CURDATE()}，
 * {@link #statDate()} 也回同一个 {@code CURDATE()}，所以页面显示的日期和数字的窗口必然同一天。
 * 如果改成 Java 侧 {@code LocalDate.now()}，就出现两个时钟：MySQL 容器的时区是
 * Asia/Shanghai（docker-compose.yml:15 的 TZ），而 {@code mvn test} 跑在宿主机本地时区上，
 * 两者在深夜那几个小时会差一天，"今日"就会变成一个测不准的数。
 */
@Mapper
public interface DashboardMapper {

    /** 窗口边界本身，与下面七条查询共用同一个 CURDATE()。 */
    @Select("SELECT CURDATE()")
    LocalDate statDate();

    /**
     * 今日预约量：今天<b>下单</b>的预约，含之后被取消的。
     * 口径理由见 DashboardMetricsService 的「今日预约量」一行。
     */
    @Select("SELECT COUNT(*) FROM appointment "
            + "WHERE deleted = 0 AND DATE(created_at) = CURDATE()")
    long countTodayAppointments();

    /**
     * 今日就诊量：今天就诊且状态已经是 COMPLETED 的预约。
     */
    @Select("SELECT COUNT(*) FROM appointment "
            + "WHERE deleted = 0 AND status = 'COMPLETED' AND DATE(appointment_time) = CURDATE()")
    long countTodayVisits();

    /** 今日门诊消费收入：今天缴成功的缴费单金额之和。 */
    @Select("SELECT COALESCE(SUM(amount_fen), 0) FROM payment_record "
            + "WHERE status = 'SUCCESS' AND DATE(created_at) = CURDATE()")
    long sumTodayOutpatientConsumeFen();

    /** 今日门诊充值收入：今天充值成功、挂在就诊人名下的那几笔。 */
    @Select("SELECT COALESCE(SUM(amount_fen), 0) FROM recharge_record "
            + "WHERE status = 'SUCCESS' AND patient_id IS NOT NULL AND DATE(created_at) = CURDATE()")
    long sumTodayOutpatientRechargeFen();

    /** 今日住院充值收入：今天充值成功、挂在住院人名下的那几笔。 */
    @Select("SELECT COALESCE(SUM(amount_fen), 0) FROM recharge_record "
            + "WHERE status = 'SUCCESS' AND inpatient_id IS NOT NULL AND DATE(created_at) = CURDATE()")
    long sumTodayInpatientRechargeFen();

    /** 待审核退款单数（待处理事项之一，消费方是 T26 的两把审核端点）。 */
    @Select("SELECT COUNT(*) FROM refund_record WHERE status = 'PENDING'")
    long countPendingRefundReviews();

    /** 待回复反馈数（待处理事项之一，消费方是 T27 的反馈处理端点）。 */
    @Select("SELECT COUNT(*) FROM feedback WHERE deleted = 0 AND status = 'PENDING'")
    long countPendingFeedback();
}
