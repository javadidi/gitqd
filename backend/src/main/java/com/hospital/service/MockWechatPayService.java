package com.hospital.service;

import com.hospital.common.ErrorCode;
import com.hospital.exception.BizException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 微信支付的<b>桩实现</b>（首版唯一实现）：没有商户凭据时由它顶着，真实对接属附录 A 二期。
 *
 * <p>命名故意带 {@code Mock} 前缀——它不是"临时的默认实现"，而是"当前系统里根本没有真实支付通道"
 * 这件事的显式声明。真通道落地时新增一个实现类并让它 {@code @Primary}（或给本类加
 * {@code @ConditionalOnProperty}），与 T07 的 {@link LoggingSmsSender} 同一处置
 * （那时定的规矩是：换通道 = 换一个实现，不改调用方）。
 *
 * <p><b>mock 模式开了要喊出来</b>，理由和 T07-I 给 {@code CryptoService} 加默认密钥告警一样：
 * {@link #verifyNotify} 在 mock 下无从"真验签"，它只是把回调里的成功标记读出来。
 * 这等于<b>任何能打到此接口的人都能把一张 PENDING_PAYMENT 单推成 CONFIRMED（而没真付钱）</b>。
 * 首版可接受的依据有三条：① 系统里没有任何真实资金流；② 回调只会把状态向前推一格，
 * 不能退款、不能改金额、不能读任何隐私字段；③ 本类启动即打 WARN，
 * 让"带着这个桩上生产"在日志里留不下无声的账。
 * 因此本卡的验收走的是患者自己的 {@code /user/appointments/{id}/pay}（要 token、要校验归属），
 * 不是直接打这个无凭据的开放接口——那条路留给真实微信支付落地后由微信服务器来走。
 */
@Component
public class MockWechatPayService implements WechatPayService {

    private static final Logger log = LoggerFactory.getLogger(MockWechatPayService.class);
    private static final String PREPAY_PREFIX = "MOCK_PREPAY_";

    private final String mchId;
    private final String apiV3Key;
    private final String mockOutcome;

    public MockWechatPayService(@Value("${wechat.pay.mchid:}") String mchId,
                                @Value("${wechat.pay.apiv3key:}") String apiV3Key,
                                @Value("${wechat.pay.mock-outcome:success}") String mockOutcome) {
        this.mchId = mchId;
        this.apiV3Key = apiV3Key;
        this.mockOutcome = mockOutcome;
    }

    @PostConstruct
    void warnIfMock() {
        if (isMock()) {
            log.warn("""
                    微信支付跑在 MOCK 模式（wechat.pay.mchid / apiv3key 未配置）：\
                    预下单号是本类派生的假号，回调验签只是读成功标记，任何人都能把待支付单推成已确认。\
                    首版无真实资金流，可接受；上真实环境前必须接入真实微信支付并删掉本类的兜底行为。""");
        }
    }

    @Override
    public boolean isMock() {
        return !StringUtils.hasText(mchId) || !StringUtils.hasText(apiV3Key);
    }

    /**
     * 预下单号由订单号确定性派生（同一订单永远同一预下单号），这样重复提交不会拿到两个号，
     * 也和 {@code WechatService.mockOpenid} 的"可复现派生"保持同一套做法。
     *
     * <p>{@code wechat.pay.mock-outcome=failure} 时抛 {@code PAYMENT_FAILED}——
     * 这个开关存在的唯一理由就是 J27「预约事务中让支付抛异常 → 全部回滚」需要一个
     * 确定性的失败注入点：不去 mock bean 化生产服务，也不靠"某种金额会失败"这种魔法值。
     * 默认值 {@code success}，只有显式配置（测试用 {@code @TestPropertySource}）才会失败。
     */
    @Override
    public String prepay(String orderNo, long amountFen) {
        if (!isMock()) {
            // 真配了凭据却没换实现类，说明部署方式错了。宁可此刻炸，也不要"配了商户号却还在发假单号"。
            throw new IllegalStateException(
                    "检测到 wechat.pay.mchid 已配置，但当前生效的实现仍是 MockWechatPayService；"
                            + "真实微信支付通道尚未实现，请勿带着假支付上真实环境");
        }
        if ("failure".equalsIgnoreCase(mockOutcome)) {
            throw new BizException(ErrorCode.PAYMENT_FAILED.getCode(), "模拟支付发起失败（J27 注入）");
        }
        log.info("MOCK 预下单 orderNo={} amountFen={}", orderNo, amountFen);
        return PREPAY_PREFIX + sha256Hex(orderNo).substring(0, 16);
    }

    /**
     * mock 验签：<b>只看"有没有带签名串"，不看支付结果码</b>。
     *
     * <p>第一版这里写成过 {@code "SUCCESS".equals(returnCode)}，那是把两件正交的事混成一件：
     * 验签回答的是"这个请求真是微信发来的吗"，{@code returnCode} 回答的是"这笔钱付成了吗"。
     * 混起来的结果是<b>「签名正确但这笔没付成」这条微信真会发的回调没法表达</b>
     * （它会被当成验签失败整个拒掉），T12 的幂等分支也就无从测试。
     *
     * <p>真实实现要换成微信自己的验签（V3 用平台证书验 HTTP 头里的签名串，V2 是 MD5/HMAC），
     * 与 {@code returnCode} 完全无关；并且必须比对回调金额与本地账（卡片 458 行「支付金额禁篡改」
     * 在回调侧的那一半），mock 这边没有可比对的对象。
     */
    @Override
    public boolean verifyNotify(String orderNo, String returnCode, String signature) {
        if (!isMock()) {
            throw new IllegalStateException(
                    "检测到 wechat.pay.mchid 已配置，但当前生效的实现仍是 MockWechatPayService；"
                            + "回调验签必须换成真实实现，否则任何人都能伪造支付成功");
        }
        return StringUtils.hasText(signature);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JRE 缺少 SHA-256", e);
        }
    }
}
