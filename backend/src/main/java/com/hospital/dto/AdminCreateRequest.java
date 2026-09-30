package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增管理员入参（T28 卡片 762 行「管理员管理：CRUD」/ PRD 4.6.1 的 450 行）。
 *
 * <p>字段清单就是 PRD 450 行括号里那四项：「用户名、密码、角色、联系方式等」——
 * 末尾那个「等」不展开：V1:374-384 的 admin 表除了这四列只有 id/时间戳/软删标记，
 * 没有邮箱、没有部门、没有状态位，规格和表结构两头都给不出第五个字段。
 *
 * <p>与 {@link AdminUpdateRequest} 分成两个类而不是一个类两处用：
 * PUT 里带 password 字段就等于允许"改角色时顺手把人家的密码改掉"，
 * 而那条动作在规格里没有出处，在审计里又必须单独留痕（见 4.6.5 与 AdminAccountService）。
 */
public class AdminCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名过长")
    private String username;

    /**
     * 密码只在本卡用于建账号，落库前过 BCrypt（与 V2 那四行同一把 {@code PasswordEncoder}）。
     *
     * <p><b>6 位这个下限没有任何规格来源，要打折看</b>：PRD 5.2 的 483–487 行五条安全要求
     * 没有一条讲密码策略，卡片 T28 的六行也没提。取"非空 + 不短于 6"是把仓库里唯一现存的
     * 凭据当参照（V2 的四个演示账号都是 admin123，8 位）而得到的下限，
     * 刻意<b>不</b>加大小写/数字/符号复杂度规则——那类规则一旦加进去，
     * 管理员建账号时就会被一条谁都没授权过的规则拒绝，而且没人能改。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6 到 64 位之间")
    private String password;

    @NotNull(message = "角色不能为空")
    private Long roleId;

    /** 联系方式：可空。格式沿用 T08 就诊人的那一条正则，两处不各写一份。 */
    @Pattern(regexp = "^$|" + PatientCreateRequest.PHONE_PATTERN, message = "手机号格式不正确")
    private String phone;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
