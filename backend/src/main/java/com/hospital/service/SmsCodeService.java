package com.hospital.service;

import com.hospital.common.ErrorCode;
import com.hospital.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * 短信验证码：任务卡 T07「添加其他号码：用户可绑定其他手机号（短信验证码）」。
 *
 * <p>存储与消费方式照搬 T06 的 CaptchaService：Redis 带 TTL，校验时无论对错都立即删除，
 * 保证一个码只能用一次。差别是这里按手机号定位（不是前端回传的 key），
 * 所以 Redis key 用手机号哈希——手机号是敏感信息，不该明文躺在 Redis 里。
 */
@Service
public class SmsCodeService {

    static final String KEY_PREFIX = "sms:";
    static final String LIMIT_PREFIX = "sms:limit:";
    static final Duration TTL = Duration.ofMinutes(5);
    /** 同一手机号两次发送的最小间隔，防止被当成免费短信炸弹 */
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);

    private static final int CODE_LENGTH = 6;

    private final StringRedisTemplate redisTemplate;
    private final SmsSender smsSender;
    private final SecureRandom random = new SecureRandom();

    public SmsCodeService(StringRedisTemplate redisTemplate, SmsSender smsSender) {
        this.redisTemplate = redisTemplate;
        this.smsSender = smsSender;
    }

    public void send(String phone) {
        String limitKey = LIMIT_PREFIX + hash(phone);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", RESEND_INTERVAL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BizException(ErrorCode.SMS_SEND_TOO_FREQUENT);
        }

        String code = generateCode();
        redisTemplate.opsForValue().set(KEY_PREFIX + hash(phone), code, TTL);
        smsSender.send(phone, code);
    }

    /** 一次性校验：取出即删，避免同一个码被反复试 */
    public boolean verifyAndConsume(String phone, String input) {
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(input)) {
            return false;
        }
        String key = KEY_PREFIX + hash(phone);
        String stored = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        return stored != null && stored.equals(input.trim());
    }

    /** 集成测试直接从 Redis 取码用（同 CaptchaIntegrationTest 的做法），生产代码不调用 */
    static String redisKey(String phone) {
        return KEY_PREFIX + hash(phone);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private static String hash(String phone) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(phone.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("JRE 缺少 SHA-256", e);
        }
    }
}
