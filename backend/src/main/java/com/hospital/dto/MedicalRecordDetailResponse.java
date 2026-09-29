package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 病历详情（T18 卡片 566 行「病历详情：查看病历详细内容（诊断、处方、医嘱等）」/
 * PRD §3.5 第 177 行同一句，接口出处 PRD §9.1 第 614 行）。
 *
 * <h2>七个字段，逐个可追</h2>
 * PRD 590 行数据字典：{@code 病历ID、就诊人ID、诊断、处方、医生、时间} 六项全在
 * （{@code recordId}/{@code patientName}/{@code diagnosis}/{@code prescription}/
 * {@code doctorName}/{@code recordTime}），第七个 {@code recordNo} 同列表 DTO 的理由。
 *
 * <h2>卡片与 PRD 177 行点名的「医嘱」<b>没有落点，本卡有意不显示</b></h2>
 * 这是一处真实的规格冲突，两路原文互相矛盾：
 * <ul>
 *   <li>PRD 177 行 / 卡片 566 行：{@code 查看病历详细内容（诊断、处方、医嘱等）} —— 点名了医嘱；</li>
 *   <li>PRD 590 行数据字典：{@code | 病历 | 病历ID、就诊人ID、诊断、处方、医生、时间 |}
 *       —— <b>没有医嘱</b>；{@code V1__init.sql:220-232} 的建表语句与这一行逐字对齐，
 *       同样没有 {@code advice} 之类的列。</li>
 * </ul>
 *
 * <p>取舍：<strong>以数据字典为准，不加列</strong>。三条理由：
 * ① 数据字典是描述结构的权威位置，V1 就是照它建的，改结构等于改一处契约；
 * ② 177 行的括号带「等」字，是举例式描述而不是字段清单（同句把「诊断、处方」也并列在里面，
 * 而这两项恰好都在字典里 —— 说明它是"举其要"，不是"列其全"）；
 * ③ 与 T14 加 {@code balance_fen} 的情形不同：那一列有三处出处且功能非它不可
 * （充值必须能表达余额），而医嘱只此一处、且 {@code medical_record} 与 {@code report} 一样
 * <b>首版没有任何生产者</b> —— 给一张没人写的表加一列，页面上就是一条永远空着的栏目，
 * 那是假装有功能（[[no-speculative-additions]]）。
 *
 * <p>这条冲突已写进 WORK_LOG 的证据表与遗留 TODO 第一条：真要支持医嘱，
 * 要么补 V5 迁移加列并等生产者配合，要么由产品确认它并入 {@code diagnosis} 正文。
 *
 * <p>{@code diagnosis} 与 {@code prescription} 都是 TEXT 且可空（V1:225/226），
 * 所以两者都可能整个键消失（Jackson NON_NULL），前端必须 {@code || '—'} 兜底。
 * 与之相反，{@code record_time} 是 NOT NULL（V1:227），所以病历详情永远有就诊时间，
 * 不需要 T17 报告页那种"时间未定"分支。
 */
public class MedicalRecordDetailResponse {

    private Long recordId;
    private String recordNo;
    private String patientName;
    private String doctorName;
    private LocalDateTime recordTime;
    private String diagnosis;
    private String prescription;

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
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getPrescription() { return prescription; }
    public void setPrescription(String prescription) { this.prescription = prescription; }
}
