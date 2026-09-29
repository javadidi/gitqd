package com.hospital.dto;

import java.time.LocalDate;

/**
 * 核酸检测报告（T21 卡片 622 行「核酸检测报告：查看检测报告」/ PRD §3.7 第 203 行
 * 「核酸检测报告 —— 在个人中心查看检测报告」/ §3.11.7 第 306 行同一句，
 * 接口出处 PRD §9.1 第 616 行「检测报告」）。
 *
 * <h2>六个字段，逐个可追</h2>
 * PRD 588 行数据字典 {@code 预约ID、就诊人ID、预约日期、状态、报告} 五项全在
 * （{@code appointmentId}/{@code patientName}/{@code appointmentDate}/{@code status}/{@code report}），
 * 第六个 {@code orderNo} 出自 {@code V1:298}。
 *
 * <h2>本卡最大的判断：产品代码<b>永远不写 {@code report}</b></h2>
 * 卡片 624 行红线逐字：「不做真实检测（二期做）；首版仅模拟流程」。
 * "模拟"落在哪一步，是本卡最需要想清楚的一条线，四条证据：
 * <ul>
 *   <li>{@code report} 的内容是<strong>关于患者身体的检测结论</strong>（阴性/阳性），
 *       不是系统自己能签发的一张凭证。写一句「阴性」就是断言这个人没被感染，
 *       而背后没有任何检测 —— 患者可能据此赴约、停止隔离。</li>
 *   <li>这与 <b>T19 的模拟开票不是一回事</b>：发票是系统自己出的一张单据，
 *       所以可以出一个明标 {@code MOCK-} 前缀、去掉前缀不是纯数字的假代码（一眼假、不给验真留余地）；
 *       而检测报告的内容不由本系统决定，"模拟"它就等于伪造医学结论。</li>
 *   <li>这也与 <b>T20 的配药信息同源不同形</b>：{@code follow_up} 连一列都没有，所以是"不给字段"；
 *       {@code nucleic_appointment.report} <b>有列、有字典出处、有端点</b>，所以不能删字段，
 *       只能<strong>不写它</strong>。</li>
 *   <li>全仓没有任何一侧负责写它：28 张任务卡里 T25 只做后台「预约核酸检测列表/详情」（只读，
 *       卡片 699 行），{@code TaskTypeMeta.NUCLEIC_CONFIRM}「核酸采样确认」只是 T05 建的任务类型文案、
 *       没有处理器；逐字 grep 全仓 {@code nucleic}，本卡之前只有建表语句、实体、空 mapper 三处。</li>
 * </ul>
 *
 * <p>所以首版的真实形状是：<strong>预约能下、记录能查、报告页能打开，但报告永远显示「未出」</strong>，
 * 直到二期接了采样/检测侧把 {@code report} 与 {@code status=COMPLETED} 写进来。
 * 页面不假装有一份报告在里面（与 T18 不留「医嘱：」空标题、T20 不留「配药信息：」同一纪律）。
 *
 * <p>J48「检测报告 → 内容正确」因此这样证：<strong>库里有什么就回什么</strong>。
 * 门禁与真 HTTP 各有一条用人工裸插的探针行（标成「人工取证探针」，与 T16/T17/T18 同一口径）
 * 证明 {@code report} 有值时逐字回出、{@code status} 原样回出，
 * 另有一条证明产品代码写出来的行 {@code report} 必为 NULL。
 *
 * <h2>{@code report} 为空时整个键消失</h2>
 * {@code application.yml:36} 的 {@code default-property-inclusion: non_null} 会把 null 字段省掉，
 * 所以首版所有响应的 {@code report} 键都不存在。前端必须按"键不存在 = 报告未出"处理，
 * 不能写 {@code detail.report || '阴性'} 这种拿默认值冒充结论的兜底。
 */
public class NucleicReportResponse {

    private Long appointmentId;
    private String orderNo;
    private String patientName;
    private LocalDate appointmentDate;
    private String status;
    private String report;

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }
}
