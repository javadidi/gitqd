package com.hospital.service;

import com.hospital.common.ErrorCode;
import com.hospital.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/**
 * 图形验证码：任务卡 T03 第 2 项「账号密码 + 验证码」，T03 当时顺延到 T06（见 docs/WORK_LOG.md）。
 * 验证码存 Redis 带 TTL，校验时无论对错都立即删除，保证一次性。
 */
@Service
public class CaptchaService {

    static final String KEY_PREFIX = "captcha:";
    static final Duration TTL = Duration.ofMinutes(5);

    /** 去掉 0/O/1/I/l，人眼分不清的字符不参与出题 */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 4;

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Captcha generate() {
        String code = generateCode(random);
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaKey, code, TTL);
        return new Captcha(captchaKey, renderPngBase64(code, random));
    }

    /** 一次性校验：取出即删，避免同一张图被反复试 */
    public boolean verifyAndConsume(String captchaKey, String input) {
        if (!StringUtils.hasText(captchaKey) || !StringUtils.hasText(input)) {
            return false;
        }
        String key = KEY_PREFIX + captchaKey;
        String stored = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        return matches(stored, input);
    }

    static String generateCode(Random random) {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    static boolean matches(String stored, String input) {
        return stored != null && stored.equalsIgnoreCase(input.trim());
    }

    static String renderPngBase64(String code, Random random) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 噪点与干扰线都画在文字之下，避免把笔画打出洞来影响肉眼识别
            for (int i = 0; i < 150; i++) {
                image.setRGB(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        new Color(180 + random.nextInt(60), 180 + random.nextInt(60), 180 + random.nextInt(60)).getRGB());
            }

            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(160 + random.nextInt(70), 160 + random.nextInt(70), 160 + random.nextInt(70)));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            FontMetrics fm = g.getFontMetrics();
            int step = WIDTH / (code.length() + 1);
            int baseline = (HEIGHT - fm.getHeight()) / 2 + fm.getAscent();
            for (int i = 0; i < code.length(); i++) {
                int x = step / 2 + i * step;
                double angle = (random.nextDouble() - 0.5) * 0.5;
                g.setColor(new Color(random.nextInt(110), random.nextInt(110), random.nextInt(110)));
                g.rotate(angle, x, baseline);
                g.drawString(String.valueOf(code.charAt(i)), x, baseline);
                g.rotate(-angle, x, baseline);
            }
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // 返回 false 表示当前 JRE 里没有 png 编码器，此时 out 是空的，必须显式失败
            if (!ImageIO.write(image, "png", out)) {
                throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "验证码图片生成失败");
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "验证码图片生成失败");
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** captchaKey 回传给前端，登录时随用户名密码一起提交 */
    public record Captcha(String captchaKey, String imageBase64) {}
}
