package com.hospital.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 报告详情（T17 卡片 550 行「报告详情：查看报告详细内容」/ PRD §3.4.1 第 163 行
 * 「报告详情 — 查看报告详细内容」，接口出处 PRD §9.1 第 613 行）。
 *
 * <h2>字段就是 PRD §10 数据字典那一行</h2>
 * PRD 589 行逐字：{@code | 报告 | 报告ID、就诊人ID、类型、检查项目、结果、时间 |}。
 * 六个字段全在这里：{@code reportId}、{@code patientName}（就诊人给名字不给 id）、
 * {@code type}、{@code items}（检查项目）、{@code result}（结果）、{@code reportTime}。
 * 另带 {@code reportNo}，理由与列表 DTO 相同（V1:204 有这一列、患者会拿号去窗口问）。
 *
 * <h2>{@code items} 是<strong>原样透传</strong>，因为没有任何规格定义过它的形状</h2>
 * V1:207 只写了 {@code `items` JSON DEFAULT NULL COMMENT '检查项目'}——一列 JSON，
 * 没有键名约定、没有生成列、没有 CHECK，{@code seed.sql} 里也没有一行报告可抄。
 * T15 的 {@code payment_record.items} 之所以能解析成强类型 {@code Item}，是因为
 * {@code seed.sql:192} 摆着真实形状 {@code [{"name":…,"amountFen":…}]}；这里没有那个证据。
 *
 * <p>所以本 DTO 的处理是：<b>后端不解释、不重命名、不挑字段</b>，把 JSON 列的内容解析成
 * {@link JsonNode} 后原样回吐。三个理由：
 * <ul>
 *   <li> 自造一套 {@code {name, value}} 结构等于给一张没有生产者的表编造契约，
 *       等真实生产者来了必然对不上（谁来写这张表规格从没说过，见 {@code ReportService} 类注释）；</li>
 *   <li> 绑成强类型会在多出一个键时抛 {@code UnrecognizedPropertyException}
 *       ——T15 的注释里已经写明了这个"一条脏数据让整页 500"的风险；</li>
 *   <li> 前端只做<strong>兜底渲染</strong>（见 {@code utils/format.js} 的
 *       {@code reportItemsText}），它不声称知道形状，只保证"不管什么形状都不会白屏"。</li>
 * </ul>
 *
 * <p>之所以仍要在后端解析而不是把 {@code report.items} 这段字符串直接扔给前端：
 * 那等于让小程序自己 {@code JSON.parse}，违反 T15 定下的"明细由后端解析、前端不碰 JSON 文本"。
 * 透传 {@code JsonNode} 两头都满足——前端拿到的是结构化值，后端也没有假装理解它。
 *
 * <p>{@code result} 是 TEXT（V1:208），原样回吐，前端按纯文本渲染。
 */
public class ReportDetailResponse {

    private Long reportId;
    private String reportNo;
    private String type;
    private String patientName;
    private LocalDateTime reportTime;
    private JsonNode items;
    private String result;

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
    public JsonNode getItems() { return items; }
    public void setItems(JsonNode items) { this.items = items; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
