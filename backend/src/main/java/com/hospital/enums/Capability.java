package com.hospital.enums;

public enum Capability {

    APPROVE_REFUND("审批退款"),
    EDIT_SETTINGS("修改系统设置"),
    MANAGE_DOCTOR("管理医生排班"),
    /**
     * T27 医院管理的内容写入（科室/医生/套餐/项目/类型/百科/指南/简介/须知/反馈处理）。
     *
     * <p><b>为什么不复用已有的三个</b>：{@code MANAGE_DOCTOR} 的中文 label 是"管理医生排班"，
     * 挂到"编辑医院简介"上等于让权限表说假话；{@code EDIT_SETTINGS} 的 label 是"修改系统设置"，
     * 那是 T28（角色/公告/看板）的领域。名字与职责对不上的权限，审计读起来就没有意义。
     *
     * <p>发放是自动的：{@code PermissionService.ROLE_CAPS} 给 system/admin 的是
     * {@code Capability.values()}，所以新枚举天然归这两个角色，doctor/nurse 仍然是空。
     * 能力列表<b>不落库</b>（V2 的 role 表没有 caps 列，登录时的 caps 来自代码），
     * 所以加这一个枚举不需要迁移文件。
     */
    MANAGE_HOSPITAL("管理医院信息");

    private final String label;

    Capability(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
