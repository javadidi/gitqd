package com.hospital.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 首版没有短信通道商账号（签名与模板都需报备），所以默认实现只把验证码打到日志，
 * 供本地开发和人工验收使用。
 *
 * <p>TODO(待通道商凭据): 换成真实通道实现（阿里云/腾讯云短信等），
 * 只需另写一个 SmsSender 实现并用 @Primary 覆盖，调用方无需改动。
 * 上线前必须替换：日志里的验证码等于把登录凭据写进了日志文件。
 */
@Component
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String phone, String code) {
        log.warn("[短信未接通道] 向 {} 发送验证码 {}（5 分钟内有效，仅打印不真发）", mask(phone), code);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() != 11) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
