package com.hospital.entity;

public class Admin extends BaseEntity {

    private String username;
    private String passwordHash;
    private Long roleId;
    private String phone;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
