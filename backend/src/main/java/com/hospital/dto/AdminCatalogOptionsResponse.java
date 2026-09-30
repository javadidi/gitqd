package com.hospital.dto;

import java.util.List;

/**
 * 医生表单要的两份选项（T27）。科室与职称都只回 id + 名字。
 *
 * <p><b>为什么不新开 {@code GET /admin/titles}</b>：职称的管理页在 T28（卡片 764 行），
 * 本卡只需要"给医生表单一份可选项"。把它做成一条独立的列表端点，
 * 等于在 T27 就开了 T28 的一半接口，后面那张卡反而要为"已经存在"的接口补权限与审计。
 * 与 T25 的 {@code /admin/appointments/filters} 同一条做法：选项挂在需要它的那一页上。
 */
public class AdminCatalogOptionsResponse {

    private List<DepartmentOption> departments;
    private List<TitleOption> titles;

    public static class DepartmentOption {
        private Long id;
        private String name;

        public DepartmentOption() { }

        public DepartmentOption(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class TitleOption {
        private Long id;
        private String name;
        private Integer sortOrder;

        public TitleOption() { }

        public TitleOption(Long id, String name, Integer sortOrder) {
            this.id = id;
            this.name = name;
            this.sortOrder = sortOrder;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public List<DepartmentOption> getDepartments() { return departments; }
    public void setDepartments(List<DepartmentOption> departments) { this.departments = departments; }
    public List<TitleOption> getTitles() { return titles; }
    public void setTitles(List<TitleOption> titles) { this.titles = titles; }
}
