package com.hospital.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LoginUser implements UserDetails {

    private Long adminId;
    private String username;
    private String roleName;
    private List<String> modules;
    private List<String> caps;

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
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleName));
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
