package com.hospital.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class LoginUser implements UserDetails {

    /**
     * {@code STAFF} 是"员工"这个**类别**的标记权限，与 {@code ROLE_<角色名>} 并列。
     *
     * <p>T28 之前 SecurityConfig 的兜底规则写的是
     * {@code hasAnyRole("system","admin","doctor","nurse")}，把四个角色名逐个列出来。
     * 卡片 763 行「角色管理：CRUD + 权限配置」一落地，这个写法就自相矛盾了：
     * 管理员在页面上新建一个「挂号员」角色、给它勾了模块、再建一个用它登录的账号——
     * 那个账号能拿到 token，但打不进任何一把管理端端点，
     * 在 Spring Security 那层就吃 403（连我们的 4001 都到不了，前端只显示"权限不足"）。
     * 于是「权限配置」是一件不发生的事。
     *
     * <p>换成类别标记以后规则是 {@code hasRole("STAFF")}：员工 token 一律进得来，
     * 具体能看哪个模块、能不能按写入键，仍然由 {@code @RequireCap} 和前端模块裁剪决定。
     * 患者侧不受影响：{@link LoginPatient} 只有 {@code ROLE_patient}，没有 STAFF。
     */
    public static final String STAFF_ROLE = "STAFF";

    private final Long adminId;
    private final String username;
    private final String roleName;
    private final List<String> modules;
    private final List<String> caps;

    public LoginUser(Long adminId, String username, String roleName, List<String> modules, List<String> caps) {
        this.adminId = adminId;
        this.username = username;
        this.roleName = roleName;
        this.modules = modules;
        this.caps = caps;
    }

    public Long getAdminId() { return adminId; }
    public String getRoleName() { return roleName; }
    public List<String> getModules() { return modules; }
    public List<String> getCaps() { return caps; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleName),
                new SimpleGrantedAuthority("ROLE_" + STAFF_ROLE));
    }

    @Override
    public String getPassword() { return null; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
