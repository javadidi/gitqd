package com.hospital.service;

/**
 * 微信支付接缝（T12）。
 *
 * <p>任务卡 A 段第⑦步「发起微信支付」和 B 段第①步「验证微信签名」都要碰外部世界，
 * 而真实微信支付商户号/API 密钥属附录 A「首版不做」清单里的"真实微信支付对接"。
 * 所以这里只定接缝，实现随配置切换，与 T07 的
 * {@link WechatService}（appid 为空即 mock）、{@link SmsSender}（{@code LoggingSmsSender} 打底）
 * 同一套模式，不新造第三种写法。
 *
 * <p><b>两个方法分别落在事务的哪一侧，是 T12 最容易被写错的地方</b>：
 * <ul>
 *   <li>{@link #prepay} 被 {@code AppointmentService.create} 在<b>事务内</b>调用——
 *       这不是偷懒，是 J27 的字面要求（「预约事务中让支付抛异常 → appointment/schedule 全部回滚」）。
 *       首版它是本地纯函数（无网络、无外部状态），放事务里没有任何持有连接的风险。
 *       <b>真实微信支付落地时必须按全局红线挪到 afterCommit</b>（外部调用不进事务），
 *       那时 J27 的语义要重述为"预下单失败由补偿任务把 PENDING_PAYMENT 单关掉"，
 *       不能一边留着事务内的真 HTTP 调用、一边说红线守住了。</li>
 *   <li>{@link #verifyNotify} 是回调进来后第一件事，<b>验不过就一个字都不改</b>：
 *       它决定"这个 HTTP 请求有没有资格推进订单状态"，与事务边界无关。</li>
 * </ul>
 */
public interface WechatPayService {

    /** 首版是否跑在 mock 模式（没有商户凭据）。启动时若为 true 会打一条 WARN 喊出来。 */
    boolean isMock();

    /**
     * 发起支付（卡片 A⑦），返回给小程序拉不起真实收银台也没关系的预下单号。
     *
     * @param orderNo   商户订单号，就是 {@code appointment.order_no}
     * @param amountFen 金额（分），<b>由服务端算出</b>，不接受客户端传值（卡片 458 行「支付金额禁篡改」）
     * @throws com.hospital.exception.BizException 支付发起失败；调用方的事务会因此把号源和预约一起回滚（J27）
     */
    String prepay(String orderNo, long amountFen);

    /**
     * 校验回调载荷的签名并判定这笔单是否真的支付成功（卡片 B①）。
     *
     * <p>三个入参分开传而不是塞一个 DTO：真实微信回调的原文里还有金额、时间戳、随机串与证书序列号，
     * 到时候是加参数还是换成传原始报文，由接真通道那张卡决定，本卡先把"要这三样才能判"的形状立起来。
     * mock 实现只用 {@code returnCode}，另外两个参数它无处校验（{@code MockWechatPayService} 的类注释
     * 记着这笔账与随之而来的 WARN）。
     *
     * <p><b>本方法只回答"请求是不是真的来自微信"，不回答"这笔钱付没付成"</b>——
     * 后者是载荷里的 {@code returnCode}，由调用方单独判（{@code AppointmentPaymentService.handleNotify}
     * 里那条"验签通过但没付成就原样 ACK、状态不动"的分支）。
     * 第一版这两件事被压在同一个返回值上，导致"签名正确的失败通知"没法表达，改开了。
     *
     * @param orderNo    商户订单号（= {@code appointment.order_no}）
     * @param returnCode 回调带回的支付结果码；验签本身不该看它，参数留着是给真实实现做摘要比对
     * @param signature  回调签名
     * @return true = 验签通过（这笔单到底成没成，由调用方再看 {@code returnCode}）；
     *         false = 验不过。调用方拿到 false 必须拒绝整个请求，
     *         而且<b>不能</b>把它当成"重复回调"ACK 掉，否则伪造者换个单号还能接着试。
     */
    boolean verifyNotify(String orderNo, String returnCode, String signature);
}
