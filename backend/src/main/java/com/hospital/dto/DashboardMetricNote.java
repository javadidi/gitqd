package com.hospital.dto;

/**
 * 看板的一个指标口径（附录 B 第 803 行原文要求的两件事里的第二件：
 * 「指标口径有没有在别处重算？（只在 MetricsService，<b>且返回口径文字</b>）」）。
 *
 * <p>{@code field} 是 {@link DashboardResponse} 上的字段名，{@code definition} 是那句口径文字。
 * 两者都由 {@code DashboardMetricsService} 与它自己的 SQL 同时产出，
 * 所以前端拿到的每个数字都自带"我是怎么算出来的"，页面上一行公式都不写。
 *
 * <p>口径文字为什么值得占一次响应体：卡片 769 行把"指标口径全局唯一定义处"列成本卡红线，
 * 而"唯一定义"这件事如果只存在于 Java 注释里，管理员看不见、前端就会想再算一份。
 * 数字旁边摆着定义，重算的动机才会消失。
 */
public class DashboardMetricNote {

    private String field;
    private String label;
    private String definition;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
}
