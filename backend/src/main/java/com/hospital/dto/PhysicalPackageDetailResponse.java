package com.hospital.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 体检套餐详情（T22 卡片 639 行「套餐详情：查看套餐详细内容」/
 * PRD §3.8 第 212 行「套餐详情 — 查看体检套餐详细内容（检查项目、价格等）」，
 * 接口出处 PRD §9.1 第 617 行「套餐详情」）。
 *
 * <h2>五个字段，同 PRD 585 行数据字典</h2>
 * 列表那四个加一个 {@code items}（字典里的「项目列表」）。
 *
 * <h2>{@code items} 原样透传，后端不解释形状</h2>
 * 这与 T17 的 {@code report.items} 是同一条判断，理由在这里同样成立：
 * {@code V1:257} 只写了 {@code `items` JSON DEFAULT NULL COMMENT '包含项目'}，
 * 没有键名约定、没有生成列、没有 CHECK，{@code seed.sql} 里<b>连一行套餐都没有</b>可抄形状。
 * 所以后端把 JSON 列解析成 {@link JsonNode} 原样回吐，不重命名、不挑字段、不套一个自造的结构。
 *
 * <p><b>为什么不自造 {@code {name, priceFen}} 强类型</b>：{@code physical_item} 表
 * （V1:266-275）确实有 {@code name}/{@code category}/{@code price_fen}/{@code description}，
 * 看着像 items 该长成的样子——但没有任何规格说 {@code package.items} 存的是项目 id、
 * 项目名快照，还是内嵌对象。套上强类型就是替 T27（后台「体检套餐管理」，
 * {@code App.tsx:73} 已占位）把它要写入的契约编出来，等真实生产者来了必然对不上。
 * 前端用 {@code format.js} 里 T17 那份 {@code reportItemsText} 容错渲染
 * （数组就逐项取 {@code name}、取不到就整项转字符串），拿不到内容时显示「—」而不是空白页。
 *
 * <p>{@code items} 为 null 时整个键消失（{@code application.yml:36} 的 NON_NULL），
 * 前端必须按"没有项目清单"处理，不能编一份常见体检项目上去。
 */
public class PhysicalPackageDetailResponse {

    private Long packageId;
    private String name;
    private Long priceFen;
    private String targetAudience;
    private JsonNode items;

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
    public JsonNode getItems() { return items; }
    public void setItems(JsonNode items) { this.items = items; }
}
