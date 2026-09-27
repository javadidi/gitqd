package com.hospital.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 小程序端登录主体（患者/用户），与后台的 {@link LoginUser}（员工）严格分开。
 *
 * <p>分开的理由不只是语义：员工 token 带 role/modules/caps，患者 token 只有 userId+openid。
 * 如果混用一个类，患者 token 会带出一个空的 modules 列表，前端和 @RequireCap 都没法判断
 * 这到底是"没有权限"还是"根本不是员工"。
 */
public class LoginPatient implements UserDetails {

    /** Spring 的 hasRole("patient") 会拼成 ROLE_patient */
    public static final String ROLE = "patient";

    private final Long userId;
    private final String openid;

    public LoginPatient(Long userId, String openid) {
        this.userId = userId;
        this.openid = openid;
    }

    public Long getUserId() { return userId; }
    public String getOpenid() { return openid; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + ROLE));
    }

    @Override
    public String getPassword() { return null; }

    @Override
    public String getUsername() { return openid; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
