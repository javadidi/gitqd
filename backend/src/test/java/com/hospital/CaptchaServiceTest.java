package com.hospital.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证码纯函数测试：不连 Redis，只验证出题字符集、比对规则、图片是否真的是可读 PNG。
 * Redis 往返与登录链路在 AuthIntegrationTest 里跑。
 */
class CaptchaServiceTest {

    @Test
    void generateCode_length4_andNeverUsesConfusingChars() {
        Random random = new Random(20260926L);
        for (int i = 0; i < 200; i++) {
            String code = CaptchaService.generateCode(random);
            assertEquals(4, code.length(), "验证码应为 4 位");
            assertFalse(code.matches(".*[01OIil].*"),
                    "不应出现人眼难分辨的字符 0/1/O/I/i/l，实际=" + code);
            assertTrue(code.matches("[2-9A-Z]{4}"), "字符集应落在 2-9A-Z，实际=" + code);
        }
    }

    @Test
    void generateCode_isRandomNotConstant() {
        Random random = new Random();
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            codes.add(CaptchaService.generateCode(random));
        }
        assertTrue(codes.size() > 1, "20 次出题不应全部相同（33^4 组合空间下几乎不可能）");
    }

    @Test
    void matches_ignoresCaseAndSurroundingSpace() {
        assertTrue(CaptchaService.matches("A7K9", "a7k9"), "应忽略大小写");
        assertTrue(CaptchaService.matches("A7K9", "  A7K9  "), "应去掉首尾空格");
        assertFalse(CaptchaService.matches("A7K9", "A7K8"), "错一位就是错");
        assertFalse(CaptchaService.matches("A7K9", ""), "空输入不通过");
        assertFalse(CaptchaService.matches(null, "A7K9"), "验证码已过期（Redis 无值）不通过");
    }

    @Test
    void renderPngBase64_producesReadablePngOfDeclaredSize() throws IOException {
        byte[] bytes = Base64.getDecoder()
                .decode(CaptchaService.renderPngBase64("A7K9", new Random(1L)));

        // PNG 魔数，证明 ImageIO 真的写出了 png 而不是别的格式
        byte[] magic = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertArrayEquals(magic, java.util.Arrays.copyOf(bytes, 8), "应以 PNG 魔数开头");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image, "base64 应能反解成图片");
        assertEquals(120, image.getWidth());
        assertEquals(40, image.getHeight());
    }

    @Test
    void renderPngBase64_differentCodesProduceDifferentImages() {
        // 两个同种子的 Random，噪点/干扰线完全一致，唯一的变量就是验证码本身
        String a = CaptchaService.renderPngBase64("AAAA", new Random(7L));
        String b = CaptchaService.renderPngBase64("BBBB", new Random(7L));
        assertNotEquals(a, b, "同种子下不同验证码应渲染出不同图片");
    }
}
