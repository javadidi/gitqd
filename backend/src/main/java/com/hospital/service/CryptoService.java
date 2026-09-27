package com.hospital.service;

import com.hospital.common.ErrorCode;
import com.hospital.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加密：手机号、身份证号（PRD 5.2「用户敏感信息加密存储」、附录 B 红线第 812 条）。
 *
 * <p>AES-256-GCM，每次加密随机 12 字节 IV，落库格式 Base64(IV || 密文 || tag)。
 * 用 GCM 而不是 CBC：它自带完整性校验，密文被改过会直接解密失败，不会吐出错乱的手机号。
 * 代价是同一明文每次密文都不同，所以**不能**用它做「按手机号查用户」的等值检索——
 * 真有这个需求时要另加一列确定性哈希，T07 没有这个需求。
 */
@Service
public class CryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    /**
     * 与 application.yml 里 `crypto.key: ${CRYPTO_KEY:...}` 的默认值**必须逐字一致**，
     * 改一处就要改另一处，否则下面的告警会静默失效。
     *
     * <p>仓库是公开的，这个默认串等于公开：谁都能拿它解密用默认配置写进库的手机号/身份证。
     * 本地开发和自动化测试用它没问题（数据是假的），但真部署必须换成 CRYPTO_KEY 环境变量。
     */
    static final String INSECURE_DEFAULT_KEY = "change-me-hospital-crypto-key-32bytes!";

    /**
     * seed.sql 里的占位前缀（如 SEED_ENC:user_phone:001）。种子数据刻意不写真手机号，
     * 所以这些值不是本类产出的密文，解密时按「未绑定」处理而不是报错。
     */
    static final String SEED_PREFIX = "SEED_ENC:";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(@Value("${crypto.key}") String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("crypto.key 为空：正在用空字符串派生 AES 密钥，任何人都能解密这些字段。"
                    + "请设置环境变量 CRYPTO_KEY（至少 32 字节随机串）后重启。");
        } else if (INSECURE_DEFAULT_KEY.equals(configuredKey)) {
            log.warn("crypto.key 仍是 application.yml 里的内置默认值：该仓库公开，默认密钥等于公开，"
                    + "用它加密的手机号/身份证可被任何克隆仓库的人解密。"
                    + "本地开发可忽略；部署前必须设置环境变量 CRYPTO_KEY。");
        }
        this.key = new SecretKeySpec(sha256(configuredKey == null ? "" : configuredKey), "AES");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "敏感字段加密失败");
        }
    }

    /** 返回 null 表示「没有绑定过」；密文损坏则抛错，不静默当成未绑定 */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty() || stored.startsWith(SEED_PREFIX)) {
            return null;
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            if (in.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度不足");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(in, IV_LENGTH, in.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "敏感字段解密失败");
        }
    }

    /** 配置的密钥长度任意，统一过一遍 SHA-256 得到 AES-256 需要的 32 字节 */
    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JRE 缺少 SHA-256", e);
        }
    }
}
