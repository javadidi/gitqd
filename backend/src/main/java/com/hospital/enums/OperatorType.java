package com.hospital.enums;

/**
 * audit_log.operator_type 的取值，由角色名映射而来。
 *
 * <p><b>{@code ADMIN/DOCTOR/NURSE} 三个值写进了 V1:404 的列注释；那两个值不动</b>——
 * V1 已被 Flyway 校验过，改一版迁移文件的任何一个字节都会让启动直接失败，
 * 而且这个列是 {@code VARCHAR(32)}，多几个取值物理上零成本。所以列注释与代码取值从此不完全相等，
 * 新增的两个各有一层来由：
 * <ul>
 *   <li><b>{@code PATIENT}</b>：T12 卡片 453 行第⑨步把「审计」写进了<b>患者侧</b>的预约事务里，
 *       而 PRD 485 行说审计范围是「管理后台操作」——卡片在这里比 PRD 更具体，按"取宽不取窄"照做，
 *       于是审计流水第一次出现了非员工的操作人；</li>
 *   <li><b>{@code SYSTEM}</b>：支付回调（卡片 B 段）的操作人是微信服务器，不是任何自然人，
 *       而 {@code audit_log.operator_id} 是 {@code NOT NULL}（V1:403），
 *       必须有值。该取值<b>不走切面</b>，由 {@code AppointmentService} 显式写流水，
 *       原因见那里的注释（切面对匿名主体保持抛 401，这条安全性质不能为了一个回调而放宽）。</li>
 * </ul>
 *
 * <p><b>{@code operator_id} 因此是一个"按 type 分流的多态列"</b>，读它的唯一正确方式是先读 type：
 * {@code ADMIN/DOCTOR/NURSE} → 指向 {@code admin.id}；{@code PATIENT} → 指向 {@code user.id}；
 * {@code SYSTEM} → 恒为 0，而 0 在 {@code admin} 和 {@code user} 两张表里都不可能真实存在
 * （两表都是 AUTO_INCREMENT，起始于 1），所以这个哨兵值不会被误认成某个真人。
 */
public enum OperatorType {

    ADMIN,
    DOCTOR,
    NURSE,
    PATIENT,
    SYSTEM;

    public static OperatorType fromRole(String roleName) {
        if (roleName == null) {
            return ADMIN;
        }
        return switch (roleName) {
            case "doctor" -> DOCTOR;
            case "nurse" -> NURSE;
            default -> ADMIN;
        };
    }
}
