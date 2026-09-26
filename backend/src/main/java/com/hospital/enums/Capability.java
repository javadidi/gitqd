package com.hospital.enums;

public enum Capability {

    APPROVE_REFUND("审批退款"),
    EDIT_SETTINGS("修改系统设置"),
    MANAGE_DOCTOR("管理医生排班");

    private final String label;

    Capability(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
