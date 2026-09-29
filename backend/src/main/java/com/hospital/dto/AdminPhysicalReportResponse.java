package com.hospital.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 管理端体检报告出参（T25 卡片 700 行「报告详情」/ PRD 357 行「查看/录入体检报告」）。
 *
 * <p>{@code report} 为 null 表示还没录入——这一页同时承担"查看"与"录入"两件事，
 * 前端据此决定显示正文还是录入表单。
 *
 * <p>{@code items} 沿用 T17/T22 的原样透传（{@link JsonNode}）：V1:257 那列没有键名约定，
 * 后端不解释它的形状。<b>本卡的录入表单不收 items</b>（见
 * {@link AdminPhysicalReportRequest} 的理由），所以新录的报告 items 恒为 null。
 */
public class AdminPhysicalReportResponse {

    private Long appointmentId;
    private Long reportId;
    private String reportNo;
    private JsonNode items;
    private String result;
    private LocalDateTime reportTime;

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public JsonNode getItems() { return items; }
    public void setItems(JsonNode items) { this.items = items; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public LocalDateTime getReportTime() { return reportTime; }
    public void setReportTime(LocalDateTime reportTime) { this.reportTime = reportTime; }
}
