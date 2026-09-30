package com.hospital.dto;

import java.util.List;

/**
 * 数据看板出参（T28 卡片 767 行「数据看板：今日预约量/就诊量/收入统计/待处理事项」/
 * PRD 4.2 的 335–340 行四行，以及 PRD 9.2 的 629 行「数据看板 | 今日统计数据、图表数据」）。
 *
 * <h2>字段与规格的对应关系</h2>
 * <table border="1">
 *   <caption>PRD 4.2 的四行分别落在哪个字段</caption>
 *   <tr><td>今日预约量统计（337 行）</td><td>{@code todayAppointmentCount}</td></tr>
 *   <tr><td>今日就诊量统计（338 行）</td><td>{@code todayVisitCount}</td></tr>
 *   <tr><td>收入统计（门诊/住院/体检等）（339 行）</td><td>三个 {@code *Fen} 字段</td></tr>
 *   <tr><td>待处理事项提醒（340 行）</td><td>{@code pendingItems}</td></tr>
 * </table>
 *
 * <p><b>339 行那句括号里的"体检"没有对应字段，而且给不出</b>：
 * 体检在库里的唯一单据是 {@code physical_appointment}（V1:280-292），
 * 那张表<b>没有任何金额列</b>——T22 建预约时就是按"费用只读不扣"做的
 * （它的类注释原话：预约表根本没有价格列，钱表四个数字不变）。
 * 体检套餐的价格（V1:255 price_fen）是标价不是收入：没人缴过这笔钱，
 * 把它加进"今日收入"就是凭空造一笔流水。核酸（V1:296）同理。
 * 所以这一栏只有门诊消费、门诊充值、住院充值三个真数，
 * 体检那一档在页面上写成"规格要求，但库里无落点"的说明文字，不放 0。
 *
 * <p><b>金额字段一律 {@code *Fen} 命名，这不是风格是防线</b>：
 * 附录 B 第 800 行问的是"护士视角新接口会不会吐金额"，
 * 而裁剪层（{@code MoneyMaskingModifier}）是按<b>属性名的正则</b>决定要不要裁的
 * （{@code amount|price|fen|gross|...}）。取名 {@code revenue} 或 {@code total}
 * 就会绕过这道自动防线，护士视角出现真实金额，而且没有任何测试会失败。
 *
 * <p>{@code metricNotes} 不是装饰：附录 B 第 803 行原文是
 * 「指标口径有没有在别处重算？（只在 {@code DashboardMetricsService}，<b>且返回口径文字</b>）」——
 * 卡片 769 行那句"指标口径全局唯一定义处"在前端的表现形式就是这一段文字，
 * 看板上每个数字旁边都写着它是怎么算出来的，管理员不需要猜，前端也算不了第二份。
 *
 * <p>字段类型是包装类而不是 long：护士视角下裁剪层写 null，
 * 原始类型会把 null 变成 0，那才是最坏的一种"看起来没问题"。
 */
public class DashboardResponse {

    /** 统计窗口的"今天"，由服务器给出（不是浏览器本地日期），口径见 DashboardMetricsService。 */
    private String statDate;

    private Long todayAppointmentCount;
    private Long todayVisitCount;

    /** 今日门诊消费收入（payment_record，V1:156 的流水表）。 */
    private Long outpatientConsumeFen;

    /** 今日门诊充值收入（recharge_record 且 patient_id 非空）。 */
    private Long outpatientRechargeFen;

    /** 今日住院充值收入（recharge_record 且 inpatient_id 非空）。 */
    private Long inpatientRechargeFen;

    private List<DashboardPendingItem> pendingItems;

    /** 每个数字的口径原文，与 SQL 同一处产出（附录 B 第 803 行要求的"返回口径文字"）。 */
    private List<DashboardMetricNote> metricNotes;

    public String getStatDate() { return statDate; }
    public void setStatDate(String statDate) { this.statDate = statDate; }
    public Long getTodayAppointmentCount() { return todayAppointmentCount; }
    public void setTodayAppointmentCount(Long todayAppointmentCount) { this.todayAppointmentCount = todayAppointmentCount; }
    public Long getTodayVisitCount() { return todayVisitCount; }
    public void setTodayVisitCount(Long todayVisitCount) { this.todayVisitCount = todayVisitCount; }
    public Long getOutpatientConsumeFen() { return outpatientConsumeFen; }
    public void setOutpatientConsumeFen(Long outpatientConsumeFen) { this.outpatientConsumeFen = outpatientConsumeFen; }
    public Long getOutpatientRechargeFen() { return outpatientRechargeFen; }
    public void setOutpatientRechargeFen(Long outpatientRechargeFen) { this.outpatientRechargeFen = outpatientRechargeFen; }
    public Long getInpatientRechargeFen() { return inpatientRechargeFen; }
    public void setInpatientRechargeFen(Long inpatientRechargeFen) { this.inpatientRechargeFen = inpatientRechargeFen; }
    public List<DashboardPendingItem> getPendingItems() { return pendingItems; }
    public void setPendingItems(List<DashboardPendingItem> pendingItems) { this.pendingItems = pendingItems; }
    public List<DashboardMetricNote> getMetricNotes() { return metricNotes; }
    public void setMetricNotes(List<DashboardMetricNote> metricNotes) { this.metricNotes = metricNotes; }
}
