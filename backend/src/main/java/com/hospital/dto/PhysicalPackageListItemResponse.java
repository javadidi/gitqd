package com.hospital.dto;

/**
 * 体检套餐列表行（T22 卡片 638 行「体检套餐列表：展示可预约的体检套餐」/
 * PRD §3.8 第 211 行「体检预约 — 展示可预约的体检套餐列表」，接口出处 PRD §9.1 第 617 行「套餐列表」）。
 *
 * <h2>四个字段，逐个可追 PRD 585 行数据字典</h2>
 * {@code | 体检套餐 | 套餐ID、名称、类型ID、价格、适用人群、项目列表 |} 六项里：
 * 套餐ID → {@code packageId}；名称 → {@code name}；价格 → {@code priceFen}；适用人群 → {@code targetAudience}。
 * 剩下两项刻意不要：
 * <ul>
 *   <li><b>{@code type_id} 不外放也不显示</b>：V1:254 只有 {@code `type_id` BIGINT DEFAULT NULL
 *       COMMENT '套餐类型'}，而<b>全仓 28 张表里没有任何一张套餐类型表</b>
 *       （PRD 417 行「新增套餐类型（如入职体检、全面体检等）」是 T27 后台的一句话，schema 没跟上）。
 *       所以既不能显示类型名（无处可查），也不该把裸 id 塞给患者看。</li>
 *   <li><b>{@code items} 留给详情页</b>：与 T15 缴费记录、T17 报告、T18 病历同一条纪律——
 *       明细不进列表。也<strong>不派生一个"含 N 项"</strong>：那是给同一事实造第二个出处，
 *       而且 {@code items} 的形状本身就没有规格（见 {@link PhysicalPackageDetailResponse}）。</li>
 * </ul>
 *
 * <h2>价格必须出现，且必须是分</h2>
 * 卡片 640 行「确认预约信息：确认体检时间、套餐、费用等」点名了费用，
 * 而附录 B「严禁前端隐藏金额」要求该看的看得到——患者看自己将要买的套餐价格，属于该看的。
 * 所以这里回 {@code priceFen}（BIGINT 分，V1:255），前端用 {@code formatMoney} 换算，
 * 全链路不出现浮点。
 */
public class PhysicalPackageListItemResponse {

    private Long packageId;
    private String name;
    private Long priceFen;
    private String targetAudience;

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getPriceFen() { return priceFen; }
    public void setPriceFen(Long priceFen) { this.priceFen = priceFen; }
    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
}
