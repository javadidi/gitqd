package com.hospital.dto;

import java.util.List;

/**
 * 预约挂号列表的筛选选项（T25）。
 *
 * <p><b>为什么单独一把，而不是复用 {@code /user/departments} 与 {@code /user/doctors}</b>：
 * 那两把在 T10 建时挂在 {@code /user/**} 下，只对患者 token 开放（SecurityConfig 的
 * {@code hasRole(patient)}），管理员 token 过来是 403。后台列表页要两个下拉，
 * 于是有三种选择：把患者端点放开（错，等于给后台开患者身份）、
 * 让管理员先以患者身份登录一次（更错）、<b>开一把只回 id 与名字的筛选项</b>（本条）。
 *
 * <p><b>为什么不算越界到 T27 的「医生管理：CRUD」</b>：这里没有新增/修改/删除，
 * 也不回简介、擅长、职称、头像——只有下拉框要的 id 与姓名。
 * 医生与科室的完整管理仍然完整地在 T27。
 */
public class AdminFilterOptionsResponse {

    private List<Option> departments;
    private List<Option> doctors;

    public List<Option> getDepartments() { return departments; }
    public void setDepartments(List<Option> departments) { this.departments = departments; }
    public List<Option> getDoctors() { return doctors; }
    public void setDoctors(List<Option> doctors) { this.doctors = doctors; }

    /** 一个下拉项。{@code departmentId} 让前端可以只选科室时把该科室的医生排在前面。 */
    public static class Option {
        private Long id;
        private String name;
        private Long departmentId;

        public Option() {
        }

        public Option(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Option(Long id, String name, Long departmentId) {
            this(id, name);
            this.departmentId = departmentId;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    }
}
