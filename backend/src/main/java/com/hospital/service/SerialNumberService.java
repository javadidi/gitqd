package com.hospital.service;

import com.hospital.enums.SerialType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class SerialNumberService {

    private static final String KEY_PREFIX = "serial:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;

    public SerialNumberService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String next(SerialType type) {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String key = KEY_PREFIX + type.getPrefix() + ":" + dateStr;

        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            redisTemplate.expire(key, Duration.ofDays(2));
        }

        return format(type, dateStr, seq);
    }

    static String format(SerialType type, String dateStr, long seq) {
        return type.getPrefix() + dateStr + "-" + String.format("%04d", seq);
    }
}
