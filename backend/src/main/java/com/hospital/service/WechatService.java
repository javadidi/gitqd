package com.hospital.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hospital.common.ErrorCode;
import com.hospital.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 小程序 wx.login() 拿到的 code 换 openid（PRD 3.1.1 微信授权登录）。
 *
 * <p>appid/appsecret 配齐就走微信真接口 jscode2session；留空则进 mock 模式，
 * 由 code 稳定派生 openid——本地开发和 J14/J15 自动化测试没有微信凭据，
 * 但「同一 openid 重复登录不重复建号」这条逻辑必须能被真实验证，所以派生必须是确定的。
 */
@Service
public class WechatService {

    private static final Logger log = LoggerFactory.getLogger(WechatService.class);

    private static final String JSCODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";
    private static final int TIMEOUT_MS = 5000;
    private static final String MOCK_PREFIX = "MOCK_OPENID_";

    private final String appid;
    private final String appsecret;

    public WechatService(@Value("${wechat.appid:}") String appid,
                         @Value("${wechat.appsecret:}") String appsecret) {
        this.appid = appid;
        this.appsecret = appsecret;
    }

    public boolean isMock() {
        return !StringUtils.hasText(appid) || !StringUtils.hasText(appsecret);
    }

    public String code2openid(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ErrorCode.WECHAT_LOGIN_FAILED);
        }
        return isMock() ? mockOpenid(code) : realOpenid(code);
    }

    private String realOpenid(String code) {
        Map<String, Object> params = new HashMap<>();
        params.put("appid", appid);
        params.put("secret", appsecret);
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");

        String body;
        try {
            body = HttpUtil.get(JSCODE2SESSION, params, TIMEOUT_MS);
        } catch (Exception e) {
            // 微信侧网络异常不回显给用户，只落日志：里面可能带 secret
            log.warn("jscode2session 调用失败: {}", e.getMessage());
            throw new BizException(ErrorCode.WECHAT_LOGIN_FAILED);
        }

        JSONObject json;
        try {
            json = JSONUtil.parseObj(body);
        } catch (Exception e) {
            log.warn("jscode2session 返回非 JSON: {}", body);
            throw new BizException(ErrorCode.WECHAT_LOGIN_FAILED);
        }

        String openid = json.getStr("openid");
        if (!StringUtils.hasText(openid)) {
            log.warn("jscode2session 未返回 openid, errcode={}, errmsg={}",
                    json.getStr("errcode"), json.getStr("errmsg"));
            throw new BizException(ErrorCode.WECHAT_LOGIN_FAILED);
        }
        return openid;
    }

    private String mockOpenid(String code) {
        log.warn("微信登录处于 mock 模式（未配置 wechat.appid/appsecret），code={} → 派生 openid", code);
        return MOCK_PREFIX + sha256Hex(code).substring(0, 32);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
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
