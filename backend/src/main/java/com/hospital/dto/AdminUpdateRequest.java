package com.hospital.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 编辑管理员入参（T28 卡片 762 行 / PRD 4.6.1 只有「列表 + 新增」两句，
 * 「编辑」这一动词的来源是卡片 762 行的 CRUD，与 T27 给套餐类型开编辑是同一层取舍）。
 *
 * <p><b>没有 password 字段</b>：改密码是 PRD 4.6.5 的独立一件事（「管理员修改自身登录密码」），
 * 主语是"自身"。替别人重置密码在整本规格里都不存在，所以这里既不接受旧密码也不接受新密码，
 * 管理员能改的只有角色归属与联系方式两项。
 *
 * <p><b>没有 username 字段</b>：用户名是 uk_username（V1:383）也是登录凭据本身，
 * 改它等于换一个人——要换人应当新建 + 删除，而不是把老账号的名字换掉，
 * 否则审计流水里的 operator_id 指着的还是同一个人，名字却变了。
 *
 * <p>两个字段都必须整份提交：{@code LambdaUpdateWrapper.set} 显式 SET（含 null），
 * 与 T27 那批编辑端点同一条规矩——PUT 少带一个字段就是清掉那一列，不是忽略那一列。
 */
public class AdminUpdateRequest {

    @NotNull(message = "角色不能为空")
    private Long roleId;

    @Pattern(regexp = "^$|" + PatientCreateRequest.PHONE_PATTERN, message = "手机号格式不正确")
    private String phone;

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
