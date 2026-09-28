package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.PayNotifyRequest;
import com.hospital.dto.PaymentResultResponse;
import com.hospital.entity.Appointment;
import com.hospital.entity.Patient;
import com.hospital.entity.PaymentRecord;
import com.hospital.enums.OperatorType;
import com.hospital.exception.BizException;
import com.hospital.mapper.AppointmentMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PaymentRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付回调与支付流水（T12 B 段，卡片 455-456 行：①验证微信签名 ②更新预约状态 ③写支付记录 ④审计）。
 *
 * <h2>全系统唯一一处「推进预约状态」的地方</h2>
 *
 * <p>卡片 458 行红线「除本方法外禁止任何地方更新预约状态」。本卡有两个入口会走到这一步
 * （微信回调 + 小程序那侧的支付动作），但它们<b>共用同一个方法</b>
 * {@link #confirmAndBook}，所以"一处"这条守得住：状态跃迁的 SQL 只有一条
 * （{@code AppointmentMapper.confirmIfPending}），支付流水的写法只有一处。
 * 将来 T13 退号会新增一个"已确认 → 已取消"的跃迁，那时它是卡片里的另一张卡，
 * 而本卡没给任何外部代码留下改 {@code appointment.status} 的口子（没有 setter 暴露、没有通用 update 方法）。
 *
 * <h2>为什么患者侧的支付不直接打回调接口</h2>
 *
 * <p>回调接口按微信的要求必须 permitAll（微信服务器不带我们的 token），
 * 而首版没有真实商户凭据，验签只能读成功标记（见 {@code MockWechatPayService} 类注释里的敞口清单）。
 * 如果小程序也走那个无凭据的口子，等于把"任何人都能把别人的待支付单点成已支付"这个敞口
 * 从开发环境搬进真实用户流程。所以本卡给小程序的是 {@link #payByPatient}：
 * <b>要患者 token、且校验这笔预约确实属于他</b>，验不过和"没这条单"同回 2004（不给枚举机会，同 T08/T09）；
 * 它内部与回调共用同一套状态推进和记账逻辑，因此"链路打通"这件事验的是真代码，不是另一条捷径。
 * 真实微信支付落地后，小程序改回 {@code wx.requestPayment} + 由微信服务器打回调，
 * 这个患者侧入口就该随之下线（已记进 WORK_LOG 的遗留清单）。
 *
 * <h2>金额只信自己账上的</h2>
 *
 * <p>写流水用的 {@code amount_fen} 取 {@code appointment.fee_fen}，
 * 不取回调里的任何金额字段（卡片 458 行「支付金额禁篡改」在回调侧的那一半）。
 * 真实通道落地时要补的是"回调金额与本地金额不一致就报警并拒绝入账"，
 * 首版 mock 的载荷里根本没有金额，所以这一步只能留到那天。
 */
@Service
public class AppointmentPaymentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentPaymentService.class);

    /** 状态字面量不在这里重复定义，统一取自 {@link AppointmentService}（全仓唯一出处）。 */
    private static final String CONFIRMED = AppointmentService.CONFIRMED;
    private static final String PAY_METHOD = "WECHAT";
    private static final String PAY_SUCCESS = "SUCCESS";
    /**
     * 流水明细里那一行的名字。取"门诊挂号费"这个不带主体的通名，有两个理由：
     * 拼成"消化内科门诊挂号费"（{@code seed.sql:193} 的 {@code 消化内科门诊诊查费} 是那个格式）
     * 需要在这里再跳一次 doctor → department，而这两张表的信息在创建预约时已经查过一遍；
     * 更关键的是"诊查费"和"挂号费"是两种费用，PRD 546 行说的是<b>挂号费</b>，
     * 所以本行只借用种子的 JSON 形状（{@code seed.sql:192} 的 {@code [{"name":...,"amountFen":...}]}），
     * 名称用 PRD 自己的词，不冒充种子那条诊查费的语义。
     */
    private static final String ITEM_NAME = "门诊挂号费";

    private final AppointmentMapper appointmentMapper;
    private final PatientMapper patientMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final WechatPayService wechatPayService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AppointmentPaymentService(AppointmentMapper appointmentMapper,
                                     PatientMapper patientMapper,
                                     PaymentRecordMapper paymentRecordMapper,
                                     WechatPayService wechatPayService,
                                     AuditLogService auditLogService,
                                     ObjectMapper objectMapper) {
        this.appointmentMapper = appointmentMapper;
        this.patientMapper = patientMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.wechatPayService = wechatPayService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 卡片 B 段：微信服务器的支付结果通知。
     *
     * <p>第①步失败（验不过签）就抛出去让整个请求被拒——<b>绝不能把验签失败的请求当"重复回调"ACK 掉</b>，
     * 那等于对伪造者回一句"收到，我不改数据"，对方换个单号还能接着试。
     */
    @Transactional
    public PaymentResultResponse handleNotify(PayNotifyRequest request) {
        if (!wechatPayService.verifyNotify(request.getOrderNo(), request.getReturnCode(), request.getSignature())) {
            throw new BizException(ErrorCode.PAYMENT_FAILED.getCode(), "支付回调验签未通过");
        }
        Appointment appointment = findByOrderNo(request.getOrderNo());
        // 验签通过但这笔没付成：状态不动、不写流水，正常返回让调用方 ACK（微信对 FAIL 也会重推，
        // 若这里抛异常就会永远重推下去）。
        if (!"SUCCESS".equalsIgnoreCase(request.getReturnCode())) {
            log.info("支付回调结果非成功，状态不变 orderNo={} returnCode={}",
                    appointment.getOrderNo(), request.getReturnCode());
            return result(appointment, false);
        }
        return confirmAndBook(appointment, request.getTradeNo(), "NOTIFY");
    }

    /**
     * 患者侧的支付动作（首版给小程序用的受控入口，理由见类注释）。
     *
     * <p>归属校验必须在这条路上：<b>它绕过了验签</b>（自己的服务不必验自己的签），
     * 所以它唯一的身份来源就是 token 里的 userId。少了这一道，
     * 任何人拿别人的 appointment id 就能把别人的单点成已支付。
     */
    @Transactional
    public PaymentResultResponse payByPatient(Long userId, Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null || !ownsPatient(userId, appointment.getPatientId())) {
            // 不是你的 和 根本没有这条 同码，不给枚举机会（与 T08/T09 的 requireOwned 同一条规矩）
            throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        return confirmAndBook(appointment, null, "PATIENT_PAY");
    }

    /**
     * 状态推进 + 支付流水 + 审计，三件事一个事务，<b>全系统唯一的预约状态跃迁点</b>。
     *
     * @param tradeNo 微信支付流水号；患者侧入口没有真流水号，留空（该列 V1:164 可空）
     * @param source  只进审计 detail，用于事后区分是微信推的还是小程序点的
     */
    private PaymentResultResponse confirmAndBook(Appointment appointment, String tradeNo, String source) {
        int moved = appointmentMapper.confirmIfPending(appointment.getId());
        if (moved == 0) {
            // 幂等命中或状态本就不对。重读一次拿真实状态——这一步只在"没推进成"的少见路径上跑，
            // 常态（第一次回调）不付这次额外读的钱。
            Appointment current = appointmentMapper.selectById(appointment.getId());
            if (current == null) {
                throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
            }
            if (!CONFIRMED.equals(current.getStatus())) {
                // 已取消/已完成的单收到支付成功通知是真异常（钱可能已经扣了），
                // 不能静默 ACK；2006 让调用方和日志都看得见。
                throw new BizException(ErrorCode.APPOINTMENT_STATUS_ERROR);
            }
            return result(current, false);
        }

        bookPayment(appointment, tradeNo);
        writeAudit(appointment, tradeNo, source);
        appointment.setStatus(CONFIRMED);
        return result(appointment, true);
    }

    /** 第③步：写支付记录（{@code payment_record} 是财务单据，V1 没有 deleted 列，只增不删）。 */
    private void bookPayment(Appointment appointment, String tradeNo) {
        PaymentRecord record = new PaymentRecord();
        // 单号直接复用预约的 order_no 做关联：payment_record 没有指向预约的列，
        // 而 V1:134 的 idx_order_no 与 V1:156-167 都没有唯一约束，
        // 加一列 appointment_id 属凭空发明 schema（没有任何规格要求过），
        // 所以用"同一笔业务的商户订单号"这一层既有语义来串——它也正是回调送进来的那个号。
        record.setOrderNo(appointment.getOrderNo());
        record.setPatientId(appointment.getPatientId());
        record.setItems(itemsJson(appointment.getFeeFen()));
        record.setAmountFen(appointment.getFeeFen());
        record.setPayMethod(PAY_METHOD);
        record.setStatus(PAY_SUCCESS);
        record.setTradeNo(StringUtils.hasText(tradeNo) ? tradeNo : null);
        paymentRecordMapper.insert(record);
    }

    /**
     * {@code items} 是 {@code JSON NOT NULL} 列（V1:160），形状抄 {@code seed.sql:192} 的
     * {@code [{"name":"...","amountFen":N}]}；用 ObjectMapper 生成而不是拼字符串，
     * 因为拼出来的东西一旦含引号就会被 JSON 列直接拒掉（写不进去只表现为一次 500，很难往这看）。
     */
    private String itemsJson(Long amountFen) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", ITEM_NAME);
        item.put("amountFen", amountFen);
        try {
            return objectMapper.writeValueAsString(List.of(item));
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "支付流水明细序列化失败");
        }
    }

    /**
     * 第④步：审计。
     *
     * <p><b>为什么手工写而不用 {@code @AuditLog} 切面</b>：这条流水的操作人是微信服务器，
     * 不是任何自然人，而切面在认不出主体时抛 401——这条"没身份就不许留痕"的安全性质
     * 不能为了一个回调而放宽（放宽之后所有匿名路径都能往流水里塞 SYSTEM 记录）。
     * 所以这里直接调 {@link AuditLogService}，写一条 {@code operator_type = SYSTEM}、
     * {@code operator_id = 0} 的记录：0 在 {@code admin} 与 {@code user} 两张表里都不可能真实存在
     * （都是 AUTO_INCREMENT 从 1 起），所以这个哨兵不会被误认成某个真人。
     *
     * <p>{@link AuditLogService#write} 是 {@code REQUIRED} 传播，因此它跟着本方法的事务走：
     * 后面任何一步失败，流水和状态一起回滚（附录 B「新写操作有没有写 audit_log？在同一事务内吗？」）。
     */
    private void writeAudit(Appointment appointment, String tradeNo, String source) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderNo", appointment.getOrderNo());
        detail.put("tradeNo", tradeNo);
        detail.put("amountFen", appointment.getFeeFen());
        detail.put("source", source);
        String detailJson;
        try {
            detailJson = objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            detailJson = null;
        }
        auditLogService.write(0L, OperatorType.SYSTEM.name(), "APPOINTMENT_PAID",
                "appointment", appointment.getId(), null, detailJson);
    }

    /**
     * 按商户订单号找预约。
     *
     * <p>用 {@code selectList} 而不是 {@code selectOne}：{@code appointment.order_no} 上只有
     * 普通索引 {@code idx_order_no}（V1:134），<b>没有唯一约束</b>，
     * 真出现两行时 {@code selectOne} 会抛 {@code TooManyResultsException}，
     * 把一个本可以处理的数据问题炸成 500；这里取第一条并打 WARN，把异常留给日志和告警去发现。
     */
    private Appointment findByOrderNo(String orderNo) {
        List<Appointment> found = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getOrderNo, orderNo)
                .orderByAsc(Appointment::getId));
        if (found.isEmpty()) {
            throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        if (found.size() > 1) {
            log.warn("单号 {} 命中 {} 行预约（order_no 无唯一索引），按最早一条处理", orderNo, found.size());
        }
        return found.get(0);
    }

    /** 这笔预约的就诊人是否属于当前登录患者（appointment 表没有 user_id，归属要经 patient 跳一次） */
    private boolean ownsPatient(Long userId, Long patientId) {
        Long count = patientMapper.selectCount(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getId, patientId)
                .eq(Patient::getUserId, userId));
        return count != null && count > 0;
    }

    private PaymentResultResponse result(Appointment appointment, boolean processed) {
        PaymentResultResponse response = new PaymentResultResponse();
        response.setOrderNo(appointment.getOrderNo());
        response.setStatus(appointment.getStatus());
        response.setProcessed(processed);
        return response;
    }
}
