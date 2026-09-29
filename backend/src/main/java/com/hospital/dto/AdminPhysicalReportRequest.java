package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 录入体检报告入参（T25 / PRD 357 行「报告详情 — 查看/录入体检报告」）。
 *
 * <p><b>只有 {@code result} 一个字段，是刻意的</b>：{@code report} 表能承载一份体检报告的列只有
 * {@code result}（TEXT，V1:255）与 {@code items}（JSON，V1:257），而 items 全仓没有任何键名约定
 * （T17 因此选择原样透传、T22 因此让套餐 items 也只由后台维护）。
 * 给一个没有 schema 的 JSON 列做表单，等于由前端替医院定字段——所以 items 留 null，
 * 等 T27 的体检项目管理把项目定义出来再补。
 *
 * <p>没有 {@code reportNo}：报告编号由 {@code SerialType.YJ} 服务端发，不接受外部指定
 * （T12 挂号费、T14 支付方式、T19 发票号是同一条规矩：能进账本的关键标识不能由客户端给）。
 * 也没有 {@code patientId} / {@code type}：主语来自路径上的预约行，类型固定 PHYSICAL。
 */
public class AdminPhysicalReportRequest {

    @NotBlank(message = "报告结论不能为空")
    @Size(max = 2000, message = "报告结论过长")
    private String result;

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
