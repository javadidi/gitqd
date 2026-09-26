package com.hospital.enums;

public enum SerialType {

    YY("YY", "预约单号"),
    CF("CF", "充值单号"),
    JF("JF", "缴费单号"),
    TK("TK", "退款单号"),
    YJ("YJ", "报告编号"),
    TJ("TJ", "体检单号"),
    HX("HX", "核酸单号"),
    FP("FP", "发票编号");

    private final String prefix;
    private final String label;

    SerialType(String prefix, String label) {
        this.prefix = prefix;
        this.label = label;
    }

    public String getPrefix() { return prefix; }
    public String getLabel() { return label; }
}
