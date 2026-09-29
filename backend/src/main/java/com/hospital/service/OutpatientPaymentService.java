package com.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.OutpatientPaymentResponse;
import com.hospital.entity.Patient;
import com.hospital.entity.PaymentRecord;
import com.hospital.exception.BizException;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PaymentRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门诊自助缴费（T15 卡片 509–514 行：待缴费项目列表 / 确认缴费信息 / 缴费 / 缴费记录）。
 *
 * <h2>本卡不发单号、不建新单</h2>
 * 与 T14 充值最大的结构差别：充值的单是<b>本系统造的</b>（患者点一次充值 → 插一行 {@code recharge_record}），
 * 而缴费的单是<b>医院侧推过来的待缴账</b>——{@code payment_record} 里 {@code status='PENDING'} 的行
 * （本项目里来自 {@code seed.sql} 第 10 段，真实部署来自 HIS）。所以本卡只做一件事：<b>推进状态 + 扣余额</b>，
 * 全程不碰 {@code SerialNumberService}，{@code SerialType.JF} 在这里用不上。
 * 这也解释了 T12 为什么不会污染待缴费列表：{@code AppointmentPaymentService.bookPayment} 只在支付
 * <b>成功后</b>写流水（直接 {@code SUCCESS}），从不写 PENDING 行。
 *
 * <h2>缴费是一个事务里的两步写 + 一次读</h2>
 * 推进单据 → 扣余额 → 回读余额出参，审计由切面在同事务内写。顺序刻意是"先改单据再扣钱"：
 * 患者连点两次时，输家会在第一道 {@code WHERE status='PENDING'} 就被挡下拿到 3004，
 * 而不是先扣了钱再发现单据不对。<b>两条 UPDATE 的判据都是受影响行数，不是先查后写</b>——
 * 这与 {@code addBalance} / {@code occupySlot} / {@code markSuccess} 是同一族纪律。
 *
 * <h2>三个失败码各有分工（卡片 516 行红线「余额不足拒绝缴费」）</h2>
 * <ul>
 *   <li>{@code 3002 BALANCE_INSUFFICIENT}：余额不够。<b>只有这一条是 J36 要的拒绝</b>；</li>
 *   <li>{@code 3004 PAYMENT_STATUS_ERROR}：这张单不是待缴状态（已缴/已退）。必须与 3001 分开——
 *       "支付失败"会诱导患者再点一次，而这一单早就缴过了；</li>
 *   <li>{@code 5001 DATA_NOT_FOUND}：单不存在与单不属于你都回这个码，不给枚举机会
 *       （沿用 T14 详情端点的口径；本表归属要跳 {@code patient.user_id}，"存在但不是你的"
 *       在业务上没有可区分的意义）。</li>
 * </ul>
 *
 * <h2>支付方式写 {@code BALANCE}，交易号留 NULL</h2>
 * V1:162 的 {@code pay_method} 注释只有「支付方式」四个字、没有封闭值域，所以"余额支付"这个事实
 * 必须有个值能表达，否则缴完费之后这张单在财务表里还写着 WECHAT，等于记了一笔假账。
 * 而 {@code trade_no}（V1:164「第三方交易号」）留 NULL：余额支付不出本院系统，<b>没有第三方</b>。
 */
@Service
public class OutpatientPaymentService {

    private static final String PENDING = "PENDING";
    private static final String SUCCESS = "SUCCESS";
    /** 就诊卡余额支付；与 seed 里的 WECHAT/ALIPAY/CASH 同一列，见类注释。 */
    private static final String PAY_METHOD_BALANCE = "BALANCE";

    private final PaymentRecordMapper paymentRecordMapper;
    private final PatientMapper patientMapper;
    private final ObjectMapper objectMapper;

    public OutpatientPaymentService(PaymentRecordMapper paymentRecordMapper,
                                    PatientMapper patientMapper,
                                    ObjectMapper objectMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.patientMapper = patientMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 缴费（J35 缴费 → 余额扣减 + 缴费记录；J36 余额不足 → 被拒）。
     *
     * <p>{@code @AuditTarget} 标在 id 入参上，所以审计行的 {@code target_id} 就是这张缴费单——
     * 与 T14 充值不同（充值是新建、当时还没有 id，{@code target_id} 只能是 NULL）。
     */
    @AuditLog(action = "PAY_OUTPATIENT_PAYMENT", targetType = "payment_record")
    @Transactional
    public OutpatientPaymentResponse pay(@AuditTarget Long paymentId, Long userId) {
        PaymentRecord record = paymentRecordMapper.selectById(paymentId);
        if (record == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Patient patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getId, record.getPatientId())
                .eq(Patient::getUserId, userId));
        if (patient == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        // 预检只为给准确文案；真正的判据是下面两条 UPDATE 的受影响行数
        if (!PENDING.equals(record.getStatus())) {
            throw new BizException(ErrorCode.PAYMENT_STATUS_ERROR);
        }

        if (paymentRecordMapper.markPaidByBalance(paymentId, PAY_METHOD_BALANCE) == 0) {
            throw new BizException(ErrorCode.PAYMENT_STATUS_ERROR);
        }
        long amountFen = record.getAmountFen();
        if (patientMapper.deductBalance(record.getPatientId(), userId, amountFen) == 0) {
            // 抛异常 → 上面那次状态推进一起回滚，不会留下"单据已缴但钱没扣"的假账
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT);
        }

        Patient after = patientMapper.selectById(record.getPatientId());
        record.setStatus(SUCCESS);
        record.setPayMethod(PAY_METHOD_BALANCE);
        return toResponse(record, patient.getName(), after.getBalanceFen(), true);
    }

    /**
     * 待缴费项目列表（卡片 511 行）。只回 {@code status='PENDING'} 的行，
     * 并带上 {@code items} 明细——列表页要显示"这笔里有几项、都是什么"。
     */
    public List<OutpatientPaymentResponse> pendingList(Long userId) {
        return selectOwned(userId, PENDING, true);
    }

    /**
     * 缴费记录（卡片 514 行 / PRD 288 行「缴费记录列表 — 展示门诊缴费历史」）：
     * 本人全部就诊人的全部流水，含 T12 挂号费那几笔（同一张财务表，天然一起出）。
     * 不带明细、不带余额——见 DTO 的字段来源表。
     */
    public List<OutpatientPaymentResponse> list(Long userId) {
        return selectOwned(userId, null, false);
    }

    /** 确认缴费信息页 / 缴费详情（PRD 115 行 + 289 行）：带明细，<b>并带该就诊人此刻的余额</b>。 */
    public OutpatientPaymentResponse detail(Long userId, Long paymentId) {
        PaymentRecord record = paymentRecordMapper.selectById(paymentId);
        if (record == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Patient patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getId, record.getPatientId())
                .eq(Patient::getUserId, userId));
        if (patient == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return toResponse(record, patient.getName(), patient.getBalanceFen(), true);
    }

    private List<OutpatientPaymentResponse> selectOwned(Long userId, String statusOrNull,
                                                        boolean withItems) {
        List<Long> myPatientIds = patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
        if (myPatientIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<PaymentRecord> query = new LambdaQueryWrapper<PaymentRecord>()
                .in(PaymentRecord::getPatientId, myPatientIds)
                .orderByDesc(PaymentRecord::getId);
        if (statusOrNull != null) {
            query.eq(PaymentRecord::getStatus, statusOrNull);
        }
        List<PaymentRecord> rows = paymentRecordMapper.selectList(query);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = patientMapper.selectBatchIds(myPatientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getName));
        List<OutpatientPaymentResponse> result = new ArrayList<>(rows.size());
        for (PaymentRecord row : rows) {
            // 列表一律传 null 余额：见 DTO 注释里「历史行重复显示实时余额会被误读」那条纪律
            result.add(toResponse(row, names.get(row.getPatientId()), null, withItems));
        }
        return result;
    }

    private OutpatientPaymentResponse toResponse(PaymentRecord record, String patientName,
                                                 Long balanceFen, boolean withItems) {
        OutpatientPaymentResponse response = new OutpatientPaymentResponse();
        response.setId(record.getId());
        response.setOrderNo(record.getOrderNo());
        response.setPatientId(record.getPatientId());
        response.setPatientName(patientName);
        response.setAmountFen(record.getAmountFen());
        response.setStatus(record.getStatus());
        response.setPayMethod(record.getPayMethod());
        response.setTradeNo(record.getTradeNo());
        response.setCreatedAt(record.getCreatedAt());
        // balanceFen 为 null 时这个键被 Jackson 的 NON_NULL 整个省掉
        response.setBalanceFen(balanceFen);
        if (withItems) {
            response.setItems(parseItems(record.getItems()));
        }
        return response;
    }

    /**
     * {@code payment_record.items} 是 JSON 列（V1:160），形状 {@code [{"name":…,"amountFen":…}]}
     * （抄 {@code seed.sql:192}）。这里解析成 Map 再挑字段而不是绑定 {@code Item.class}：
     * 列的形状没有任何约束（不是生成列、没有 CHECK），多出键时绑类会直接抛
     * {@code UnrecognizedPropertyException}，表现为"一条脏数据让整页 500"。
     */
    /**
     * 包级可见而不是 private：T19 的票据详情要显示"这张票开的是哪几项"，
     * 明细必须与缴费详情走<b>同一个解析路径</b>（同一套键名、同一套跳过规则），
     * 否则两处显示会漂移。与 T16 复用 {@code AppointmentQueryService.namesOf} 同一条理由。
     */
    List<OutpatientPaymentResponse.Item> parseItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<OutpatientPaymentResponse.Item> items = new ArrayList<>(raw.size());
            for (Object element : raw) {
                if (!(element instanceof Map<?, ?> map)) {
                    continue;
                }
                OutpatientPaymentResponse.Item item = new OutpatientPaymentResponse.Item();
                Object name = map.get("name");
                item.setName(name == null ? null : String.valueOf(name));
                Object amount = map.get("amountFen");
                item.setAmountFen(amount instanceof Number number ? number.longValue() : null);
                items.add(item);
            }
            return items;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
    }
}
