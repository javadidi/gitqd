package com.hospital.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 创建核酸检测预约入参（T21 卡片 619–621 行「选择就诊人」「核酸检测申请：填写检测信息」
 * 「确认预约信息：确认检测时间、地点等」，接口出处 PRD §9.1 第 616 行「创建检测预约」）。
 *
 * <h2>两个字段，因为 {@code nucleic_appointment} 只收这两样</h2>
 * {@code V1__init.sql:296-307} 的业务列是 {@code order_no / patient_id / appointment_date /
 * status / report}：前两个由服务端自己定（单号走 {@code SerialType.HX}、状态写死 PENDING），
 * {@code report} 由检测侧回填，所以患者能提交的只有 {@code patientId} 与 {@code appointmentDate}。
 * PRD 588 行数据字典同形：{@code 核酸预约 | 预约ID、就诊人ID、预约日期、状态、报告}。
 *
 * <h2>卡片 621 行的「检测时间、地点」只有时间进得来</h2>
 * <ul>
 *   <li><b>时间</b>：{@code appointment_date} 是 {@code DATE}（V1:300），<b>没有时分列</b>，
 *       所以"检测时间"的粒度就是日期。前端确认页显示的也是日期，不假装能选上午/下午。</li>
 *   <li><b>地点</b>：表里没有采样点列，PRD 588 行数据字典也没有，全仓 28 张表没有采样点表。
 *       所以<b>本卡不显示地点、也不编一个地址</b>（四路证据与判定见
 *       {@link NucleicReportResponse} 类注释，与 T18「医嘱不加列」、T20「配药信息不给字段」同一口径）。
 *       卡片那句带「等」字，是举其要不是列其全。</li>
 * </ul>
 *
 * <h2>{@code @FutureOrPresent}：过去的日期当场拦下</h2>
 * 这不是给患者添麻烦，是"预约"这个词的最低限度自洽——一个已经过去的检测日永远不可能被采样，
 * 让它入库就是留一条永远停在 PENDING 的死数据。方向与 T12 挂号一致（只能约今天及以后的排班）。
 * 上限<b>不设</b>：规格从没给过"最多约几天内"，编一个 7 天/30 天就是发明规则。
 */
public class NucleicCreateRequest {

    @NotNull(message = "请选择就诊人")
    private Long patientId;

    @NotNull(message = "请选择检测日期")
    @FutureOrPresent(message = "检测日期不能早于今天")
    private LocalDate appointmentDate;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
}
