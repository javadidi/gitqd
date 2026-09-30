package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 角色新增/修改入参（T28 卡片 763 行「角色管理：CRUD + 权限配置」/ PRD 4.6.2 的 454 行
 * 「创建角色并配置权限」）。
 *
 * <p><b>「权限配置」配的就是 modules 这一项，落点是 role.permissions（V1:392 JSON）</b>：
 * PRD 596 行「角色 | 角色ID、名称、权限列表」给这一列的定义就是"模块名的列表"，
 * V2 也是照这个写的四行 JSON；登录时由
 * {@code PermissionService.resolveModules} 读它（T28 之前只读代码里的静态表，
 * 于是页面上的勾选没有去处）。
 *
 * <p>取值只能是 {@code PermissionService.ALL_MODULES} 里那 8 个键，未知键在服务层丢弃：
 * 不发明模块，也不让一个手写的多余键把整个请求打回。
 * 这里刻意<b>不是</b> {@code @Enum}式的枚举校验，因为那 8 个键是字符串常量、不是枚举类型，
 * 服务层用同一个 {@code ALL_MODULES.contains} 判，两处不会漂移。
 */
public class RoleSaveRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称过长")
    private String name;

    @NotEmpty(message = "至少勾选一个可见模块")
    private List<String> modules;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getModules() { return modules; }
    public void setModules(List<String> modules) { this.modules = modules; }
}
