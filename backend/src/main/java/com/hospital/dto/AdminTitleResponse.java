package com.hospital.dto;

/**
 * 管理端职称出参（T28 卡片 764 行 / PRD 4.6.3 的 457 行「职称列表 — 展示医生职称」）。
 *
 * <p>{@code doctorCount} 不在 PRD 那句话里，但本卡<b>没有</b>给职称开删除，
 * 页面必须让管理员看见为什么：这一列写着"3 位医生在用"，
 * 比在 WORK_LOG 里放一段"我们决定不做删除"的解释有用。
 * 它与 T27 给套餐类型不开删除是同一条取舍（见 {@code AdminTitleService} 类注释）。
 */
public class AdminTitleResponse {

    private Long id;
    private String name;
    private Integer sortOrder;
    private Long doctorCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getDoctorCount() { return doctorCount; }
    public void setDoctorCount(Long doctorCount) { this.doctorCount = doctorCount; }
}
