package com.hospital.common;

public enum ErrorCode {

    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    PATIENT_NOT_FOUND(1003, "就诊人不存在"),
    PATIENT_CARD_NO_EXISTS(1004, "就诊卡号已存在"),
    INPATIENT_NOT_FOUND(1005, "住院人不存在"),
    INPATIENT_NO_EXISTS(1006, "住院号已存在"),

    SCHEDULE_NOT_FOUND(2001, "排班不存在"),
    SCHEDULE_CONFLICT(2002, "排班冲突"),
    SCHEDULE_NO_SLOTS(2003, "号源已满"),
    APPOINTMENT_NOT_FOUND(2004, "预约不存在"),
    APPOINTMENT_DUPLICATE(2005, "重复预约"),
    APPOINTMENT_STATUS_ERROR(2006, "预约状态错误"),

    PAYMENT_FAILED(3001, "支付失败"),
    BALANCE_INSUFFICIENT(3002, "余额不足"),
    REFUND_NOT_ALLOWED(3003, "不允许退款"),

    PERMISSION_DENIED(4001, "权限不足"),
    ROLE_NOT_FOUND(4002, "角色不存在"),
    CAPTCHA_INVALID(4003, "验证码错误或已失效"),

    DATA_NOT_FOUND(5001, "数据不存在"),
    DATA_ALREADY_EXISTS(5002, "数据已存在"),

    TASK_NOT_FOUND(6001, "任务不存在"),
    TASK_STATUS_ERROR(6002, "任务状态错误"),
    REVIEW_MUST_OPEN_DOC(6003, "审核类任务须打开单据后逐条处理"),
    TASK_TYPE_UNKNOWN(6004, "任务类型未知");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() { return code; }
    public String getMessage() { return message; }
}
