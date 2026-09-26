package com.hospital.dto;

import java.util.List;

public class LoginResponse {

    private String token;
    private Long adminId;
    private String username;
    private String role;
    private List<String> modules;
    private List<String> caps;
    private String landingPage;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public List<String> getModules() { return modules; }
    public void setModules(List<String> modules) { this.modules = modules; }
    public List<String> getCaps() { return caps; }
    public void setCaps(List<String> caps) { this.caps = caps; }
    public String getLandingPage() { return landingPage; }
    public void setLandingPage(String landingPage) { this.landingPage = landingPage; }
}
