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
    // T27 删除守卫。沿用 2xxx 段（T02 预留的排班/预约段），接在 2007 之后，
    // 与 T11 加 2007、T15 加 3004、T19 加 3005、T26 加 3006 同一条规矩：新码只追加不复用。
    // 为什么要守卫而不是直接软删：appointment.doctor_id / schedule.department 都是 NOT NULL 无外键，
    // 删一个还有活的班或还有未取消预约的医生，等于让"患者还约在someone名下"这件事失去主体——
    // 卡片 736/737 行只写了「编辑/删除」，没写"删掉之后别人的号怎么办"，
    // 所以规格给不出"允许删"的授权，只能拒绝并告诉管理员先处理什么。
    DEPARTMENT_HAS_DOCTORS(2008, "该科室下仍有医生，请先调整医生所属科室"),
    DOCTOR_HAS_ACTIVE_SCHEDULES(2009, "该医生仍有排班或未取消的预约，请先停诊或退号"),
    // T27 体检套餐的删除守卫，与上面 2008/2009 同族，也接在 2xxx 段末尾。
    // physical_appointment.package_id 是 NOT NULL（V1:283），删掉一个还有人的套餐，
    // 患者那条记录就指着一个不存在的套餐名。
    PHYSICAL_PACKAGE_HAS_APPOINTMENTS(2010, "该套餐仍有未取消的体检预约，请先处理预约记录"),

    PAYMENT_FAILED(3001, "支付失败"),
    BALANCE_INSUFFICIENT(3002, "余额不足"),
    REFUND_NOT_ALLOWED(3003, "不允许退款"),
    // T15 重复缴费（已缴过/已退的单再点一次）。沿用 T02 预留的 3xxx 支付段，接在 3003 之后，
    // 与 T11 加 2007 的做法同一条规矩。必须与 3001「支付失败」分开：3001 会诱导患者再试一次，
    // 而"这一单早就缴过了"恰恰要让他别再试。
    PAYMENT_STATUS_ERROR(3004, "该缴费单已缴过或状态不允许缴费"),
    // T19 重复开票（同一张缴费单第二次申请）。沿用 T02 预留的 3xxx 支付段，接在 3004 之后，
    // 与 T11 加 2007、T15 加 3004 同一条规矩。为什么不复用 3004：那句文案是「已缴过或状态
    // 不允许缴费」，说给刚点"缴费"的人听；点"开票"的人需要知道的是"这张票已经开过了，
    // 去已开具列表看"，两句不是一回事，混用会把患者引回缴费流程。
    INVOICE_EXISTS_FOR_PAYMENT(3005, "该缴费单已开具过发票，可在已开具列表查看"),
    // T26 重复审核（退款单已被人审过，第二次点通过/拒绝）。沿用 3xxx 支付段，接在 3005 之后，
    // 与 T11 加 2007、T15 加 3004、T19 加 3005 同一条规矩。为什么不复用 3003「不允许退款」：
    // 那句是说给刚点"申请退款"的人听的（这单本来就不该退），而 3006 说的是"这张单已经有人表过态了，
    // 去看结果"——两个动作、两个人，混用会让第二个审核人以为是自己点错了而再点一次。
    REFUND_ALREADY_REVIEWED(3006, "该退款单已审核过，请刷新列表查看结果"),

    PERMISSION_DENIED(4001, "权限不足"),
    ROLE_NOT_FOUND(4002, "角色不存在"),
    CAPTCHA_INVALID(4003, "验证码错误或已失效"),
    WECHAT_LOGIN_FAILED(4004, "微信登录失败，请重试"),
    SMS_CODE_INVALID(4005, "短信验证码错误或已失效"),
    SMS_SEND_TOO_FREQUENT(4006, "短信发送过于频繁，请稍后再试"),
    // T28 系统设置沿用 4xxx 段（T03 预留的权限/认证段），接在 4006 之后，
    // 与 T11 加 2007、T15 加 3004、T19 加 3005、T26 加 3006 同一条规矩：新码只追加不复用。
    // 为什么不复用 5002「数据已存在」：那句是给"业务对象重了"用的（如 T27 的重名类型），
    // 而账号名重了管理员需要知道的是"换一个名，或者把删掉的那个复活"，两句话不一样。
    ADMIN_USERNAME_EXISTS(4007, "该用户名已被占用"),
    // 删除守卫。admin 表是后台唯一登录主体来源（V2 之后没有别的注册通道），
    // 删掉自己 = 当前 token 还活着但账号从此登不回来；删掉 V2 的四个 = 四个角色的
    // 演示入口和每一条集成测试的登录凭据一起消失。规格里没有一句教管理员"误删了怎么办"，
    // 所以两处都只能拒绝并指路。
    ADMIN_CANNOT_DELETE_SELF(4008, "不能删除当前登录的账号"),
    ADMIN_IS_BUILT_IN(4009, "系统内置账号不可删除（V2 权限地基，四个角色的登录入口）"),
    // 角色：role_id 在 admin 表是 NOT NULL（V1:378），删一个还挂着人的角色 = 那些人的角色列没有主语。
    ROLE_IN_USE(4010, "该角色下仍有管理员，请先调整他们的角色"),
    // 内置四个角色（system/admin/doctor/nurse）的名字是 PermissionService 里 caps 表的键，
    // 改名会让那个角色的写权限静默清空（caps 不落库，见 Capability 枚举注释），
    // 而管理员在页面上看不出任何异常——这种"改了名字就少掉一半权限"的坑不能开放。
    ROLE_IS_BUILT_IN(4011, "内置角色的名称与权限由权限地基定义，不可修改或删除"),

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
