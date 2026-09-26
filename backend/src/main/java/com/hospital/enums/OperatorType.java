package com.hospital.enums;

/**
 * audit_log.operator_type 的取值，由角色名映射而来。
 */
public enum OperatorType {

    ADMIN,
    DOCTOR,
    NURSE;

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
