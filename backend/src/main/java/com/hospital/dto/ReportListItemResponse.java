package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 报告列表行（T17 卡片 549 行「报告列表：展示报告列表」/ PRD §3.4.1 第 162 行
 * 「报告查询 — 展示报告列表」，接口出处 PRD §9.1 第 613 行「报告查询 | 报告列表、报告详情」）。
 *
 * <h2>字段取自 PRD §10 数据字典，且只取列表该有的那几个</h2>
 * PRD 589 行逐字：{@code | 报告 | 报告ID、就诊人ID、类型、检查项目、结果、时间 |}。
 * 六个字段里本行放四个：
 * <ul>
 *   <li>{@code reportId}/{@code type}/{@code reportTime} — 直接对应 报告ID/类型/时间；</li>
 *   <li>{@code patientName} — 数据字典写的是「就诊人ID」，但列表给患者看必须给名字，
 *       沿用 T13/T15 的既有做法（同一批量解析，见 {@code ReportService}）；</li>
 *   <li><b>不放 {@code items}（检查项目）与 {@code result}（结果）</b>：与 T15
 *       「待缴列表带 items、缴费记录列表不带」同一条纪律——列表是历史清单，
 *       明细留给详情。且 {@code result} 是 TEXT（V1:208），一屏二十行会把它整段铺开。</li>
 * </ul>
 *
 * <p>{@code reportNo} 是第五个字段，数据字典里没有它，但 V1:204 有 {@code report_no} 列
 * 且建了索引（V1:214 的 {@code idx_report_no}，列注释「报告编号」），而 {@code SerialType.YJ}
 * （报告编号）早就建好了。
 * 患者拿报告号去窗口问话是常态，所以列表带上；这一点属<strong>我的选择</strong>，
 * 依据是列本身存在，不是规格写了要显示。
 *
 * <p>类型只回码值（{@code LAB}/{@code IMAGING}），中文标签在前端
 * {@code REPORT_TYPE_LABELS}（T08 {@code relation}、T10 {@code timeSlot} 同一套取舍）。
 */
public class ReportListItemResponse {

    private Long reportId;
    private String reportNo;
    private String type;
    private String patientName;
    private LocalDateTime reportTime;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public LocalDateTime getReportTime() { return reportTime; }
    public void setReportTime(LocalDateTime reportTime) { this.reportTime = reportTime; }
}
