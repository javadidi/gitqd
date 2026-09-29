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
    // T11 取消排班的前置守卫（任务卡 437 行「排班取消时，已预约的记录需处理」）。
    // 2001-2006 是 T02 建模时预留的排班/预约段，本码沿用同一段，编号接在 2006 后。
    SCHEDULE_HAS_APPOINTMENTS(2007, "该排班已有预约，请先退号后再取消"),

    PAYMENT_FAILED(3001, "支付失败"),
    BALANCE_INSUFFICIENT(3002, "余额不足"),
    REFUND_NOT_ALLOWED(3003, "不允许退款"),
    // T15 重复缴费（已缴过/已退的单再点一次）。沿用 T02 预留的 3xxx 支付段，接在 3003 之后，
    // 与 T11 加 2007 的做法同一条规矩。必须与 3001「支付失败」分开：3001 会诱导患者再试一次，
    // 而"这一单早就缴过了"恰恰要让他别再试。
    PAYMENT_STATUS_ERROR(3004, "该缴费单已缴过或状态不允许缴费"),

    PERMISSION_DENIED(4001, "权限不足"),
    ROLE_NOT_FOUND(4002, "角色不存在"),
    CAPTCHA_INVALID(4003, "验证码错误或已失效"),
    WECHAT_LOGIN_FAILED(4004, "微信登录失败，请重试"),
    SMS_CODE_INVALID(4005, "短信验证码错误或已失效"),
    SMS_SEND_TOO_FREQUENT(4006, "短信发送过于频繁，请稍后再试"),

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
