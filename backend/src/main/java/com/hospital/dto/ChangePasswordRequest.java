package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改自身密码入参（T28 卡片 766 行「修改密码」/ PRD 4.6.5 的 465 行
 * 「管理员修改自身登录密码」）。
 *
 * <p>两个字段，主语只有一个"自己"：PRD 那句话里没有"替别人重置"，
 * 所以这里<b>没有 adminId 字段</b>——主体一律从 token 取
 * （{@code SecurityUtils.currentAdminId()}，与 T26 的 {@code reviewer_id} 同一条取法），
 * 带 adminId 就等于把"改谁的密码"交给客户端。
 *
 * <p>{@code oldPassword} 是必填的：本接口挂在"任意员工"这一档（改密码不该需要
 * EDIT_SETTINGS，否则护士和医生永远改不了自己的密码），没有旧密码这一步，
 * 一个被盗用的 token 就能顺手把账号锁到攻击者手里。
 */
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 与建账号同一把下限，理由见 {@link AdminCreateRequest#password}。 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在 6 到 64 位之间")
    private String newPassword;

    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
