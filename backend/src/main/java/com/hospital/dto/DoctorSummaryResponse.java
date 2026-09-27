package com.hospital.dto;

/**
 * 医生摘要（T10）：科室详情页与医生列表页共用同一形状。
 *
 * <p>字段出处：任务卡 414 行「医生列表：展示医生信息（姓名/职称/擅长/头像）」。
 * {@code titleName} 是 join title 表解析出来的**中文名**而不是 {@code titleId}——
 * 医生表存的是 title_id（V1:88），患者端不该看见内部主键。
 *
 * <p>{@code availableCount} = 该医生「今天及以后、剩余号源 &gt; 0」的排班条数。
 * 加它的出处是 PRD 77 行「科室详情页 — 展示该科室下所有医生<b>及排班信息</b>」：
 * 卡片 413 行只写了「展示该科室下所有医生」，比 PRD 窄，
 * 按 T08-G 的教训（范围要读 DoD + §9.1 + §6.1 + PRD 正文，不能只读卡片动词清单）取宽的一侧。
 * PRD 没规定这个"排班信息"长什么样，所以口径由本卡自定并在此记明：
 * **只数条数，不吐具体日期时段**，具体排班留到医生详情页（卡片 415 行）再给，
 * 这样列表页不会把 150 行 seed 排班全捞出来。
 *
 * <p>本 DTO 里没有任何金额字段，所以附录 B「护士视角新接口会不会吐金额」在本卡 N/A。
 */
public class DoctorSummaryResponse {

    private Long id;
    private String name;
    private Long departmentId;
    private String titleName;
    private String specialty;
    private String avatar;
    private Integer availableCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getTitleName() { return titleName; }
    public void setTitleName(String titleName) { this.titleName = titleName; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Integer getAvailableCount() { return availableCount; }
    public void setAvailableCount(Integer availableCount) { this.availableCount = availableCount; }
}
