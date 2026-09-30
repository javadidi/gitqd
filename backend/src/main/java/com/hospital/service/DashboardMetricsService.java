package com.hospital.service;

import com.hospital.dto.DashboardMetricNote;
import com.hospital.dto.DashboardPendingItem;
import com.hospital.dto.DashboardResponse;
import com.hospital.mapper.DashboardMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 数据看板的指标口径（T28 卡片 767 行「数据看板：今日预约量/就诊量/收入统计/待处理事项」，
 * 出处 PRD 4.2 的 335–340 行；PRD 9.2 的 629 行那句「数据看板 | 今日统计数据、图表数据」里
 * 的"图表数据"本卡不做，理由见类注释末尾）。
 *
 * <h2>本类是全项目唯一的指标定义处（卡片 769 行的红线，附录 B 第 803 行的检查项）</h2>
 * 下面这张表就是"口径"这件事的原文。SQL 在 {@code DashboardMapper}，
 * 文字在这里，前端拿到的口径在 {@link DashboardResponse#getMetricNotes()} 里——
 * 三处一份，没有第四处。<b>任何页面都不许自己 SUM/COUNT 业务表。</b>
 *
 * <table border="1">
 *   <caption>七个数字各自的口径</caption>
 *   <tr><th>字段</th><th>SQL 条件</th><th>规格出处</th><th>为什么是这个口径</th></tr>
 *   <tr><td>{@code todayAppointmentCount}</td>
 *       <td>{@code appointment}：{@code deleted=0} 且 {@code DATE(created_at)=CURDATE()}</td>
 *       <td>PRD 337 行「今日预约量统计」/ 卡片 767 行</td>
 *       <td>按<b>下单时间</b>算，不按就诊时间算。"预约量"量的是一天里发生的预约动作；
 *           而且 {@code appointment} 没有"预约日期"这一列，只有 {@code appointment_time}（V1:125）
 *           与 {@code created_at}（V1:126），要按就诊日算就得跟就诊量共用一个窗口，两个数会打起来。
 *           <b>含之后被取消的那几笔</b>——取消不注销"今天有人约过"这个事实，
 *           退号路径（T13）自己另有单据可查。</td></tr>
 *   <tr><td>{@code todayVisitCount}</td>
 *       <td>{@code appointment}：{@code deleted=0} 且 {@code status='COMPLETED'}
 *           且 {@code DATE(appointment_time)=CURDATE()}</td>
 *       <td>PRD 338 行「今日就诊量统计」</td>
 *       <td>四个状态里只有 COMPLETED 说"看过病了"（V1:124 注释：
 *           PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED）。CONFIRMED 只是挂了号。</td></tr>
 *   <tr><td>{@code outpatientConsumeFen}</td>
 *       <td>{@code payment_record}：{@code status='SUCCESS'} 且 {@code DATE(created_at)=CURDATE()}</td>
 *       <td>PRD 339 行「收入统计（门诊/住院/体检等）」的"门诊"</td>
 *       <td>只算 SUCCESS：PENDING 的单子钱还没到账，REFUNDED 已经退出去。
 *           与 T14/T15 那条"只有 SUCCESS 才算余额变动"的口径同一把尺子
 *           （seed 的 9b 段回填余额时用的也正是这一条）。</td></tr>
 *   <tr><td>{@code outpatientRechargeFen}</td>
 *       <td>{@code recharge_record}：{@code status='SUCCESS'} 且 {@code patient_id IS NOT NULL}
 *           且今天</td>
 *       <td>同上，"门诊"里的充值那一半</td>
 *       <td>靠 {@code patient_id}/{@code inpatient_id} 两个列分门诊还是住院（V1:143-144），
 *           这是库里唯一给出的区分方式，T23 的住院充值也照这一条落列。</td></tr>
 *   <tr><td>{@code inpatientRechargeFen}</td>
 *       <td>{@code recharge_record}：{@code status='SUCCESS'} 且 {@code inpatient_id IS NOT NULL}
 *           且今天</td>
 *       <td>同上，"住院"</td>
 *       <td><b>住院"消费"没有数字，而且给不出</b>：库里没有一张按住院人挂账的流水表
 *           （T26 的住院消费页因此是零端点、只有说明页），这一档只有充值。</td></tr>
 * </table>
 *
 * <h2>体检收入为什么在页面上是一句说明而不是 0</h2>
 * PRD 339 行的括号里点名了"体检"，但体检在库里的唯一单据 {@code physical_appointment}（V1:280-292）
 * <b>没有任何金额列</b>——T22 建预约时就是"费用只读不扣"（它的类注释原话：预约表根本没有价格列，
 * 钱表四个数字不变）。套餐价格（V1:255）是标价不是收入：没人缴过这笔钱。
 * 所以这一档不填 0（0 会读成"今天体检收入是零"这个业务结论），
 * 页面写"规格要求，库里无落点"，并与 T22/T26 的同一处空白对齐。核酸同理。
 *
 * <h2>待处理事项的两条入选判据</h2>
 * 判据不是"状态看着像待办"，而是<b>本卡之外真有一把写端点能把它消掉</b>：
 * <ul>
 *   <li>{@code REFUND_REVIEW} = {@code refund_record.status='PENDING'}，消掉它的是 T26 的两把审核端点；</li>
 *   <li>{@code FEEDBACK_REPLY} = {@code feedback.status='PENDING'}（软删的不算），
 *       消掉它的是 T27 的反馈处理端点。</li>
 * </ul>
 * 没进来的三类，各有各的原因，写在这里而不是散落在三张卡的注释里：
 * <b>病案配送 PENDING</b>——T26 只给了列表和详情两把读端点，没有任何"发货"端点
 * （{@code case_delivery.tracking_no} 从 V1 起没有生产者），管理员点进去什么也做不了；
 * <b>预约 PENDING_PAYMENT</b>——那是等患者付钱，不是等医院做事，T13/T25 的退号入口是另一回事；
 * <b>task 表 OPEN 待办</b>——T05 的四方法内核完整、卡片 308 行红线明写"不写业务触发"，
 * 首版没有任何地方调用 {@code dispatchTask}，所以那张表恒为空。
 * 顶栏红点（卡片 323 行 T06-E 欠下的那一项）用的就是上面两个数之和，不是 task 表的行数——
 * 用一张恒空的表做红点，红点永远不亮，那才是把欠项做成假账。
 *
 * <h2>PRD 629 行的"图表数据"不做</h2>
 * 那是 PRD 9.2 接口概览表里的一列举例，不是需求条目：PRD 4.2（335–340 行）四条里没有一条提图表，
 * 卡片 767 行也只有四个指标。画什么轴、按天还是按科室聚合、要不要折线，规格里一处都没有，
 * 所以这里不发明一份"看起来像仪表盘"的东西。
 */
@Service
public class DashboardMetricsService {

    private final DashboardMapper dashboardMapper;

    public DashboardMetricsService(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    public DashboardResponse metrics() {
        LocalDate statDate = dashboardMapper.statDate();

        DashboardResponse response = new DashboardResponse();
        response.setStatDate(statDate == null ? null : statDate.toString());
        response.setTodayAppointmentCount(dashboardMapper.countTodayAppointments());
        response.setTodayVisitCount(dashboardMapper.countTodayVisits());
        response.setOutpatientConsumeFen(dashboardMapper.sumTodayOutpatientConsumeFen());
        response.setOutpatientRechargeFen(dashboardMapper.sumTodayOutpatientRechargeFen());
        response.setInpatientRechargeFen(dashboardMapper.sumTodayInpatientRechargeFen());

        long pendingRefunds = dashboardMapper.countPendingRefundReviews();
        long pendingFeedback = dashboardMapper.countPendingFeedback();
        List<DashboardPendingItem> pending = new ArrayList<>();
        pending.add(pendingItem("REFUND_REVIEW", "退款待审核", pendingRefunds));
        pending.add(pendingItem("FEEDBACK_REPLY", "反馈待回复", pendingFeedback));
        response.setPendingItems(pending);

        response.setMetricNotes(metricNotes(statDate));
        return response;
    }

    private DashboardPendingItem pendingItem(String type, String label, long count) {
        DashboardPendingItem item = new DashboardPendingItem();
        item.setType(type);
        item.setLabel(label);
        item.setCount(count);
        return item;
    }

    /** 口径文字与数字同批产出（附录 B 第 803 行要求的"且返回口径文字"）。 */
    private List<DashboardMetricNote> metricNotes(LocalDate statDate) {
        String today = statDate == null ? "数据库当前日" : statDate.toString();
        List<DashboardMetricNote> notes = new ArrayList<>(Arrays.asList(
                note("todayAppointmentCount", "今日预约量",
                        "appointment 表里 " + today + " 当天创建的预约条数，含之后被取消的"),
                note("todayVisitCount", "今日就诊量",
                        "appointment 表里就诊日期为 " + today
                                + " 且状态为 COMPLETED 的条数（CONFIRMED 只算挂号、不算就诊）"),
                note("outpatientConsumeFen", "今日门诊消费收入",
                        "payment_record 表 " + today + " 当天 status=SUCCESS 的金额之和（分）"),
                note("outpatientRechargeFen", "今日门诊充值收入",
                        "recharge_record 表 " + today + " 当天 status=SUCCESS、挂在就诊人名下的金额之和（分）"),
                note("inpatientRechargeFen", "今日住院充值收入",
                        "recharge_record 表 " + today + " 当天 status=SUCCESS、挂在住院人名下的金额之和（分）"
                                + "；住院消费无表可算，本卡不出该数字"),
                note("pendingItems.REFUND_REVIEW", "退款待审核",
                        "refund_record 表 status=PENDING 的条数；能消掉它的是 T26 的两把审核端点"),
                note("pendingItems.FEEDBACK_REPLY", "反馈待回复",
                        "feedback 表 status=PENDING 且未删除的条数；能消掉它的是 T27 的反馈处理端点")
        ));
        return notes;
    }

    private DashboardMetricNote note(String field, String label, String definition) {
        DashboardMetricNote note = new DashboardMetricNote();
        note.setField(field);
        note.setLabel(label);
        note.setDefinition(definition);
        return note;
    }
}
