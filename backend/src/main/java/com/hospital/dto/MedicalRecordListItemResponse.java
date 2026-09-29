package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 病历列表行（T18 卡片 565 行「病历列表：展示历史病历」/ PRD §3.5 第 176 行
 * 「病历查询 — 展示历史病历列表」，接口出处 PRD §9.1 第 614 行「病历查询 | 病历列表、病历详情」）。
 *
 * <h2>字段取自 PRD §10 数据字典那一行</h2>
 * PRD 590 行逐字：{@code | 病历 | 病历ID、就诊人ID、诊断、处方、医生、时间 |}。
 * 六个字段里本行放四个：{@code recordId}、{@code patientName}（就诊人给名字不给 id，
 * 沿用 T13/T15/T17）、{@code doctorName}（对应「医生」）、{@code recordTime}。
 *
 * <p><b>不放 {@code diagnosis}（诊断）与 {@code prescription}（处方）</b>：两列都是 TEXT
 * （V1:225/226），一屏二十行会把整段病历铺开。与 T17「列表不放 items/result」、
 * T15「记录列表不带 items」同一条纪律：<strong>列表是清单，正文留给详情</strong>。
 *
 * <p>{@code recordNo}（V1:222 的 {@code record_no}，注释「病历编号」）同 T17 的
 * {@code reportNo}：数据字典里没有这一项，但列在、且患者会拿病历号去窗口问话 ——
 * <strong>属我的选择，依据是列存在，不是规格写了要显示</strong>。
 *
 * <p><b>没有「科室」</b>：{@code medical_record} 只有 {@code doctor_id}（V1:224），
 * 科室要再经 {@code doctor.department_id} 跳一次；PRD 590 行的病历清单里没有科室，
 * 卡片 565 行也只说「展示历史病历」。与 T17 同一取舍：不为"看着顺眼"加字段。
 */
public class MedicalRecordListItemResponse {

    private Long recordId;
    private String recordNo;
    private String patientName;
    private String doctorName;
    private LocalDateTime recordTime;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getRecordNo() { return recordNo; }
    public void setRecordNo(String recordNo) { this.recordNo = recordNo; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public LocalDateTime getRecordTime() { return recordTime; }
    public void setRecordTime(LocalDateTime recordTime) { this.recordTime = recordTime; }
}
