package com.hospital.dto;

import java.time.LocalDateTime;

/**
 * 管理端管理员列表出参（T28 卡片 762 行 / PRD 4.6.1 的 449 行「管理员列表 — 展示所有管理员账号」）。
 *
 * <p><b>phone 是脱敏值，且可能是 null</b>：admin.phone（V1:379）存 AES-GCM 密文，
 * 读出来过 {@code MaskUtil.maskPhone} → 「138****0001」。
 * null 有两种来源，都在这里合成"未填写"这一个显示：一是这一列本来就空
 * （V8 把 V2 写的四个明文号码清空了，理由见那条迁移的注释），
 * 二是历史遗留的 {@code SEED_ENC:} 占位串解密后为 null。前端按空渲染，不显示星号串。
 *
 * <p>没有 passwordHash：这一列永远不出 DTO。admin 表在后台是可读的，
 * 而 BCrypt 串一旦被任何一份列表带出去，公开仓库里就等于把哈希挂在了公告栏上。
 */
public class AdminResponse {

    private Long id;
    private String username;
    private Long roleId;
    private String roleName;
    private String phone;
    private LocalDateTime createdAt;

    /** true = V2__init_admin.sql 建的四个演示账号之一，前端据此不渲染删除按钮。 */
    private Boolean builtIn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getBuiltIn() { return builtIn; }
    public void setBuiltIn(Boolean builtIn) { this.builtIn = builtIn; }
}
