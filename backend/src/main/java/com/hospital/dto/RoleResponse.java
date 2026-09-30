package com.hospital.dto;

import java.util.List;

/**
 * 管理端角色出参（T28 卡片 763 行 / PRD 4.6.2 的 453 行「角色列表 — 展示所有角色」）。
 *
 * <p>{@code modules} 是从 role.permissions 解析出来的模块键列表（与 {@code ALL_MODULES} 同序），
 * 前端拿它渲染勾选框；{@code wildcard} 单独带出来，因为 {@code ["*"]} 解析后与"8 个全勾"
 * 在勾选框上长得一模一样，但内置角色的这一份配置是<b>不能改</b>的（4011），
 * 页面得区分"全勾且锁住"与"某人勾了 8 个"。
 *
 * <p>{@code adminCount} = 这个角色下还挂着几个管理员。PRD 453 行只说"展示所有角色"，
 * 但卡片 763 行要删除，而删除的第一条守卫就是"还有人在用"（4010）——
 * 不带这个数，管理员只能一把一把试。它与守卫读的是同一个查询，不是另算一份。
 */
public class RoleResponse {

    private Long id;
    private String name;
    private List<String> modules;
    private Boolean core;
    private Boolean wildcard;
    private Long adminCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getModules() { return modules; }
    public void setModules(List<String> modules) { this.modules = modules; }
    public Boolean getCore() { return core; }
    public void setCore(Boolean core) { this.core = core; }
    public Boolean getWildcard() { return wildcard; }
    public void setWildcard(Boolean wildcard) { this.wildcard = wildcard; }
    public Long getAdminCount() { return adminCount; }
    public void setAdminCount(Long adminCount) { this.adminCount = adminCount; }
}
