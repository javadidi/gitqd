package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 候诊排队状态（T16 卡片 529 行「候诊查询页：展示当前排队人数、叫号进度」/
 * PRD §9.1 第 612 行「候诊查询 | 获取当前排队状态」）。
 *
 * <h2>一行 = 一次预约，而不是一行 = 一条队列记录</h2>
 * 这是本 DTO 的形状之所以这样的根因。{@code queue_status} 表（V1:188-197）只有
 * {@code appointment_id}、{@code current_number}、{@code waiting_count}、{@code status} 四列，
 * <b>它自己说不清"这是谁的、在排谁的队"</b>。而 PRD 103 行要的是"实时查看当前候诊叫号状态"——
 * 患者手上的实体是<b>一次就诊预约</b>，不是一条队列记录。所以这一行以预约为骨架，
 * 排队信息缺了就是缺了（{@code queueStatus == null} 表示"还没进入叫号队列"），
 * 而不是把没有队列的预约整条抹掉：那样患者会以为自己的预约也不存在了。
 *
 * <h2>为什么这张表首版可能一行都没有（这不是实现缺陷）</h2>
 * 全仓检索过：任务卡里"候诊/叫号/排队"只出现在 T16 与色板、组件表、路线图；
 * PRD 的 §4 后台章节、§6.2 后台页面清单、§9.2 后台接口里<b>一次都没有叫号管理</b>，
 * {@code seed.sql} 里也没有 {@code queue_status} 的行。也就是说首版<b>没有任何生产者</b>——
 * 真实系统里这张表由院内叫号系统（HIS）写入（PRD 662 行「医院排队叫号系统」）。
 * 所以本卡一律只读，也绝不为了"页面好看"自己造一个假叫号生成器。
 *
 * <h2>后端只回原值，进度条口径在前端组件里</h2>
 * 卡片 531 行要的 {@code <QueueProgress>} 进度条需要一个百分比，但<b>规格从没定义过
 * 进度怎么算</b>（PRD 105-108 只有"排队人数""叫号进度"两个词）。口径是前端组件自己定义的
 * （见 {@code components/queue-progress/queue-progress.js} 的注释，已标注为"无规格出处的设计选择"）。
 * 正因为这个公式可能被产品推翻，它<b>不能</b>由后端算成数字回给前端——否则一次改口径就要
 * 前后端各改一处、还得防着两边漂移。
 *
 * <p>字段来源逐条可追溯：{@code appointmentId}/{@code orderNo}/{@code appointmentStatus}/
 * {@code appointmentTime} 来自 PRD 581 行数据字典「预约记录 = 预约ID、就诊人ID、医生ID、排班ID、
 * 状态、预约时间、费用」；三个名字字段沿用 T13 已建立的同源批量解析（同一个预约在两个页面
 * 显示的医生名必须来自同一处）；{@code queueStatus}/{@code currentNumber}/{@code waitingCount}
 * 即 V1:191-193 的三个列；<b>没有 feeFen</b>——候诊页与费用无关，带上只会让患者以为要在这里付钱。
 *
 * <p><b>没有 {@code queueId}</b>：{@code queue_status.id} 是流水表主键，T14 已经证明
 * "会被客户端回传的 id 必须是自增且在 2^53 以内"。本卡客户端只用 {@code appointmentId}
 * 认队列（{@code uk_appointment_id} 保证一预约至多一行，V1:196），所以根本不需要外放这个 id。
 */
public class QueueStatusResponse {

    private Long appointmentId;
    private String orderNo;
    private String patientName;
    private String departmentName;
    private String doctorName;
    private String timeSlot;
    private LocalDateTime appointmentTime;
    private String appointmentStatus;
    private String queueStatus;
    private Integer currentNumber;
    private Integer waitingCount;
    private LocalDateTime queueUpdatedAt;

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public LocalDateTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalDateTime appointmentTime) { this.appointmentTime = appointmentTime; }
    public String getAppointmentStatus() { return appointmentStatus; }
    public void setAppointmentStatus(String appointmentStatus) { this.appointmentStatus = appointmentStatus; }
    public String getQueueStatus() { return queueStatus; }
    public void setQueueStatus(String queueStatus) { this.queueStatus = queueStatus; }
    public Integer getCurrentNumber() { return currentNumber; }
    public void setCurrentNumber(Integer currentNumber) { this.currentNumber = currentNumber; }
    public Integer getWaitingCount() { return waitingCount; }
    public void setWaitingCount(Integer waitingCount) { this.waitingCount = waitingCount; }
    public LocalDateTime getQueueUpdatedAt() { return queueUpdatedAt; }
    public void setQueueUpdatedAt(LocalDateTime queueUpdatedAt) { this.queueUpdatedAt = queueUpdatedAt; }
}
