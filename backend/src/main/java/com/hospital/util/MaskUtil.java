package com.hospital.util;

/**
 * 敏感字段脱敏（附录 B「身份证/手机号是否加密存储」的展示侧配套）。
 *
 * <p>抽成工具类而不是各 Service 自己写一份：脱敏是安全边界，两处实现必然有一天漂移
 * （一处改了位数、另一处忘了），漂移的结果是某个接口开始往外吐明文。T07 的
 * {@code UserService.maskPhone} 已改为委托这里。
 *
 * <p>所有方法对 null / 长度不符的输入一律返回 null，不猜、不补位——
 * 库里 {@code SEED_ENC:} 占位值解密后就是 null，前端按「未填写」渲染。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 11 位手机号 → 前 3 后 4，中间 4 个星。非 11 位返回 null */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return null;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 18 位身份证 → 前 4 后 4，中间 10 个星（前 4 位含省市码、后 4 位含校验位，
     * 足够本人在列表里认出自己那张，不足以还原号码）。非 18 位返回 null。
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return null;
        }
        return idCard.substring(0, 4) + "**********" + idCard.substring(14);
    }
}
