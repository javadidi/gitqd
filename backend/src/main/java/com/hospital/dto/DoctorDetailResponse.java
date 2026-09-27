package com.hospital.dto;

import java.util.List;

/**
 * 医生详情（T10）。
 *
 * <p>字段出处：任务卡 415 行「医生详情：展示医生简介、排班时间」+ PRD 79 行
 * 「医生信息 — 展示医生简介、职称、擅长领域、排班时间」。
 * 所以 {@code intro}（简介）与 {@code schedules}（排班时间）是本 DTO 相对
 * {@link DoctorSummaryResponse} 多出来的两样，其余字段与列表页一致。
 *
 * <p><b>刻意不继承 {@link DoctorSummaryResponse}</b>：摘要里的 {@code availableCount}
 * 在详情页是冗余的（schedules 列表本身就是明细，数一下就有），
 * 继承会把一个无意义的键带进详情响应，还会让"改摘要字段"意外影响详情契约。
 *
 * <p>{@code departmentName} 与 {@code titleName} 都是 join 出来的中文名，
 * 与摘要同理由：患者端不出内部主键。{@code departmentId} 仍保留，
 * 因为详情页要能点回"该科室的其他医生"（PRD 77 行的科室详情页）。
 *
 * <p>{@code schedules} 的口径见 {@link ScheduleItemResponse} 与 CatalogService：
 * <b>只含今天及以后</b>、按 date + 时段升序。过去的排班对"我要挂号"没有意义，
 * 而 seed 里排班是 CURDATE()-7..+7 共 150 行，不过滤会把一半废数据推给小程序。
 * 永不为 null，无排班时回空数组（理由同 DepartmentDetailResponse.doctors）。
 */
public class DoctorDetailResponse {

    private Long id;
    private String name;
    private Long departmentId;
    private String departmentName;
    private String titleName;
    private String intro;
    private String specialty;
    private String avatar;
    private List<ScheduleItemResponse> schedules;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getTitleName() { return titleName; }
    public void setTitleName(String titleName) { this.titleName = titleName; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public List<ScheduleItemResponse> getSchedules() { return schedules; }
    public void setSchedules(List<ScheduleItemResponse> schedules) { this.schedules = schedules; }
}
