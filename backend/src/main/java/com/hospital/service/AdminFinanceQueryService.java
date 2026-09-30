package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminCaseDeliveryResponse;
import com.hospital.dto.AdminPaymentResponse;
import com.hospital.dto.AdminRechargeResponse;
import com.hospital.dto.AdminRefundResponse;
import com.hospital.entity.CaseDelivery;
import com.hospital.entity.Inpatient;
import com.hospital.entity.Patient;
import com.hospital.entity.PaymentRecord;
import com.hospital.entity.RechargeRecord;
import com.hospital.entity.RefundRecord;
import com.hospital.exception.BizException;
import com.hospital.mapper.AdminMapper;
import com.hospital.mapper.AppointmentMapper;
import com.hospital.mapper.CaseDeliveryMapper;
import com.hospital.mapper.InpatientMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PaymentRecordMapper;
import com.hospital.mapper.RechargeRecordMapper;
import com.hospital.mapper.RefundRecordMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端费用查询（T26 卡片 716–721 行的六组「记录/详情」里<b>只读</b>的那五组）。
 *
 * <h2>五个列表一个筛选参数都不接，这是算过账的</h2>
 * PRD §4.4（369–390 行）通篇只有两种句子：「展示××记录」「查看××明细/详情」。
 * 对照 §4.3.1（347 行）明写「支持按日期/科室/医生/状态筛选」——T25 就照那四个加了四个入参。
 * <b>同一份文档里作者会筛的时候是会说筛的</b>，费用这一章没说，就不给他补上没要的筛子。
 * 唯一保留的是列表本身按 id 倒序（最新一笔在前），这是排序不是筛选。
 * 需要按人/按状态找一笔钱时，管理员翻表头（{@code DataTable} 自带前端翻页）。
 *
 * <h2>「住院消费记录/详情」在本类里不存在，而且不是漏写</h2>
 * 卡片 719 行有这一句，但 V1 十七张表里没有一张能装它：
 * {@code payment_record} 只有 {@code patient_id NOT NULL}（V1:159），没有住院人列、
 * 没有费用类别列，硬塞住院消费等于把住院的钱记到某个门诊就诊人头上去。
 * T23 为小程序侧的同一件事（PRD 308 行「住院费用清单」）已经下过同一个结论——
 * 「零端点零页面」。后台这边一并执行：不建端点，页面用一行说明代替，
 * 缺的那张表挂在跨卡 TODO 里（与 {@code 住院费用表 producer} 同一条）。
 *
 * <h2>姓名列为什么会读软删行，以及为什么不</h2>
 * 就诊人可以被本人删（T08），流水表不能删（V1:138 注释「财务单据，不软删」），
 * 于是"钱还在、人已被删"是正常状态 → {@code patient} 走
 * {@link PatientMapper#selectByIdsIncludingDeleted}，否则管理员会看到一批不认识是谁的钱
 * （与 T25 停诊后 {@code schedule} 的同一条教训一致）。
 * 住院人反过来：{@code InpatientController} 只有 list/detail/bind 三把端点，
 * 全系统没有任何代码把 {@code inpatient.deleted} 置 1，所以这里用默认的 {@code selectBatchIds}
 * 就够了，不提前给一个没有生产者的状态修管道。
 *
 * <h2>金额裁剪不归本类管</h2>
 * 护士看不到金额是序列化层（{@code MoneyMaskingModifier}）按字段名做的，
 * 与 T04 定下时一样：service 照常填值，出口处按角色裁。
 * 本卡唯一与此有关的设计决定是 {@link AdminPaymentResponse} 的明细用强类型而不是裸 JSON——
 * 理由写在那个类的注释里（裸 JSON 会让裁剪层找不到 {@code amountFen} 这个 bean 属性）。
 *
 * <h2>没有归属跳</h2>
 * 与 {@code AdminAppointmentQueryService} 同理：后台看全院的钱本来就是需求，
 * 边界由 {@code /admin/**} 的角色规则和 {@code @RequireCap} 承担。
 */
@Service
public class AdminFinanceQueryService {

    /** V1:176 注释里的三个 related_type 取值，一个不多一个不少。 */
    private static final String RELATED_TYPE_APPOINTMENT = "APPOINTMENT";
    private static final String RELATED_TYPE_RECHARGE = "RECHARGE";
    private static final String RELATED_TYPE_PAYMENT = "PAYMENT";

    private final PaymentRecordMapper paymentRecordMapper;
    private final RechargeRecordMapper rechargeRecordMapper;
    private final CaseDeliveryMapper caseDeliveryMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final PatientMapper patientMapper;
    private final InpatientMapper inpatientMapper;
    private final AppointmentMapper appointmentMapper;
    private final AdminMapper adminMapper;
    private final OutpatientPaymentService outpatientPaymentService;

    public AdminFinanceQueryService(PaymentRecordMapper paymentRecordMapper,
                                    RechargeRecordMapper rechargeRecordMapper,
                                    CaseDeliveryMapper caseDeliveryMapper,
                                    RefundRecordMapper refundRecordMapper,
                                    PatientMapper patientMapper,
                                    InpatientMapper inpatientMapper,
                                    AppointmentMapper appointmentMapper,
                                    AdminMapper adminMapper,
                                    OutpatientPaymentService outpatientPaymentService) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.rechargeRecordMapper = rechargeRecordMapper;
        this.caseDeliveryMapper = caseDeliveryMapper;
        this.refundRecordMapper = refundRecordMapper;
        this.patientMapper = patientMapper;
        this.inpatientMapper = inpatientMapper;
        this.appointmentMapper = appointmentMapper;
        this.adminMapper = adminMapper;
        this.outpatientPaymentService = outpatientPaymentService;
    }

    // ============================================================
    // 门诊消费记录（卡片 716 行 / PRD 369–370 行）
    // ============================================================

    public List<AdminPaymentResponse> listPayments() {
        List<PaymentRecord> rows = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>().orderByDesc(PaymentRecord::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Patient> patients = patientsByIds(rows.stream().map(PaymentRecord::getPatientId).toList());
        List<AdminPaymentResponse> result = new ArrayList<>();
        for (PaymentRecord row : rows) {
            result.add(toPaymentResponse(row, patients.get(row.getPatientId()), false));
        }
        return result;
    }

    public AdminPaymentResponse paymentDetail(Long id) {
        PaymentRecord row = paymentRecordMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Patient patient = row.getPatientId() == null ? null
                : patientsByIds(List.of(row.getPatientId())).get(row.getPatientId());
        return toPaymentResponse(row, patient, true);
    }

    private AdminPaymentResponse toPaymentResponse(PaymentRecord row, Patient patient, boolean withItems) {
        AdminPaymentResponse response = new AdminPaymentResponse();
        response.setId(row.getId());
        response.setOrderNo(row.getOrderNo());
        response.setPatientId(row.getPatientId());
        response.setPatientName(patient == null ? null : patient.getName());
        response.setCardNo(patient == null ? null : patient.getCardNo());
        response.setAmountFen(row.getAmountFen());
        response.setPayMethod(row.getPayMethod());
        response.setStatus(row.getStatus());
        response.setTradeNo(row.getTradeNo());
        response.setCreatedAt(row.getCreatedAt());
        if (withItems) {
            response.setItems(outpatientPaymentService.parseItems(row.getItems()));
        }
        return response;
    }

    // ============================================================
    // 门诊 / 住院充值记录（卡片 717–718 行 / PRD 372–378 行）
    // ============================================================

    /**
     * 充值列表。{@code inpatient} 这个布尔决定这一把端点是「门诊充值记录」还是「住院充值记录」，
     * 判据就是 V1:143-144 那两列注释写明的分工：门诊充值填 {@code patient_id}、
     * 住院充值填 {@code inpatient_id}，同一张表两笔账。
     *
     * <p>用 {@code IS NULL} / {@code IS NOT NULL} 切而不是让调用方传 status/patientId，
     * 是因为 PRD 4.4.2 与 4.4.3 是<b>两个独立小节、两个独立页面</b>，
     * 而这两页之间唯一能区分数据的东西就是这个空/非空。
     */
    public List<AdminRechargeResponse> listRecharges(boolean inpatient) {
        LambdaQueryWrapper<RechargeRecord> query = new LambdaQueryWrapper<RechargeRecord>()
                .orderByDesc(RechargeRecord::getId);
        if (inpatient) {
            query.isNotNull(RechargeRecord::getInpatientId);
        } else {
            query.isNull(RechargeRecord::getInpatientId);
        }
        List<RechargeRecord> rows = rechargeRecordMapper.selectList(query);
        if (rows.isEmpty()) {
            return List.of();
        }
        return toRechargeResponses(rows);
    }

    /**
     * 充值详情。仍然带 {@code inpatient} 开关：门序列表里的 id 拿到住院那把端点上去读，
     * 回 5001 而不是"反正同表就读给你"。两把端点各自只认自己那一族，
     * 前端两页才不会因为误传 id 而串数据。
     */
    public AdminRechargeResponse rechargeDetail(Long id, boolean inpatient) {
        RechargeRecord row = rechargeRecordMapper.selectById(id);
        boolean isInpatientRow = row != null && row.getInpatientId() != null;
        if (row == null || isInpatientRow != inpatient) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return toRechargeResponses(List.of(row)).get(0);
    }

    private List<AdminRechargeResponse> toRechargeResponses(List<RechargeRecord> rows) {
        Map<Long, Patient> patients = patientsByIds(rows.stream()
                .map(RechargeRecord::getPatientId).filter(java.util.Objects::nonNull).toList());
        Map<Long, Inpatient> inpatients = inpatientsByIds(rows.stream()
                .map(RechargeRecord::getInpatientId).filter(java.util.Objects::nonNull).toList());

        List<AdminRechargeResponse> result = new ArrayList<>();
        for (RechargeRecord row : rows) {
            AdminRechargeResponse response = new AdminRechargeResponse();
            Patient patient = row.getPatientId() == null ? null : patients.get(row.getPatientId());
            Inpatient inpatient = row.getInpatientId() == null ? null : inpatients.get(row.getInpatientId());
            response.setId(row.getId());
            response.setOrderNo(row.getOrderNo());
            response.setPatientId(row.getPatientId());
            response.setPatientName(patient == null ? null : patient.getName());
            response.setCardNo(patient == null ? null : patient.getCardNo());
            response.setInpatientId(row.getInpatientId());
            response.setInpatientName(inpatient == null ? null : inpatient.getName());
            response.setInpatientNo(inpatient == null ? null : inpatient.getInpatientNo());
            response.setAmountFen(row.getAmountFen());
            response.setPayMethod(row.getPayMethod());
            response.setStatus(row.getStatus());
            response.setTradeNo(row.getTradeNo());
            response.setCreatedAt(row.getCreatedAt());
            response.setUpdatedAt(row.getUpdatedAt());
            result.add(response);
        }
        return result;
    }

    // ============================================================
    // 病案配送记录（卡片 720 行 / PRD 384–386 行）
    // ============================================================

    public List<AdminCaseDeliveryResponse> listCaseDeliveries() {
        List<CaseDelivery> rows = caseDeliveryMapper.selectList(
                new LambdaQueryWrapper<CaseDelivery>().orderByDesc(CaseDelivery::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Inpatient> inpatients = inpatientsByIds(
                rows.stream().map(CaseDelivery::getInpatientId).toList());
        List<AdminCaseDeliveryResponse> result = new ArrayList<>();
        for (CaseDelivery row : rows) {
            result.add(toCaseDeliveryResponse(row, inpatients.get(row.getInpatientId())));
        }
        return result;
    }

    public AdminCaseDeliveryResponse caseDeliveryDetail(Long id) {
        CaseDelivery row = caseDeliveryMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Inpatient inpatient = row.getInpatientId() == null ? null : inpatientMapper.selectById(row.getInpatientId());
        return toCaseDeliveryResponse(row, inpatient);
    }

    private AdminCaseDeliveryResponse toCaseDeliveryResponse(CaseDelivery row, Inpatient inpatient) {
        AdminCaseDeliveryResponse response = new AdminCaseDeliveryResponse();
        response.setId(row.getId());
        response.setInpatientId(row.getInpatientId());
        response.setInpatientName(inpatient == null ? null : inpatient.getName());
        response.setInpatientNo(inpatient == null ? null : inpatient.getInpatientNo());
        response.setDepartment(inpatient == null ? null : inpatient.getDepartment());
        response.setBedNo(inpatient == null ? null : inpatient.getBedNo());
        response.setRecipientName(row.getRecipientName());
        response.setAddress(row.getAddress());
        response.setStatus(row.getStatus());
        response.setTrackingNo(row.getTrackingNo());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    // ============================================================
    // 退款记录（卡片 721 行 / PRD 388–390 行）——只读，审核在 RefundReviewService
    // ============================================================

    public List<AdminRefundResponse> listRefunds() {
        List<RefundRecord> rows = refundRecordMapper.selectList(
                new LambdaQueryWrapper<RefundRecord>().orderByDesc(RefundRecord::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        return toRefundResponses(rows);
    }

    public AdminRefundResponse refundDetail(Long id) {
        RefundRecord row = refundRecordMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return toRefundResponses(List.of(row)).get(0);
    }

    private List<AdminRefundResponse> toRefundResponses(List<RefundRecord> rows) {
        Map<String, String> relatedOrderNos = resolveRelatedOrderNos(rows);
        Set<Long> reviewerIds = new HashSet<>();
        for (RefundRecord row : rows) {
            if (row.getReviewerId() != null) {
                reviewerIds.add(row.getReviewerId());
            }
        }
        Map<Long, String> reviewerNames = new HashMap<>();
        if (!reviewerIds.isEmpty()) {
            adminMapper.selectBatchIds(reviewerIds).forEach(admin ->
                    reviewerNames.put(admin.getId(), admin.getUsername()));
        }

        List<AdminRefundResponse> result = new ArrayList<>();
        for (RefundRecord row : rows) {
            AdminRefundResponse response = new AdminRefundResponse();
            response.setId(row.getId());
            response.setOrderNo(row.getOrderNo());
            response.setRelatedId(row.getRelatedId());
            response.setRelatedType(row.getRelatedType());
            response.setRelatedOrderNo(row.getRelatedId() == null ? null
                    : relatedOrderNos.get(relatedKey(row.getRelatedType(), row.getRelatedId())));
            response.setAmountFen(row.getAmountFen());
            response.setReason(row.getReason());
            response.setStatus(row.getStatus());
            response.setReviewerId(row.getReviewerId());
            response.setReviewerName(row.getReviewerId() == null ? null
                    : reviewerNames.get(row.getReviewerId()));
            response.setCreatedAt(row.getCreatedAt());
            response.setUpdatedAt(row.getUpdatedAt());
            result.add(response);
        }
        return result;
    }

    /**
     * 按 related_type 分三堆，各去对应的流水表批量取回业务单号——一共最多三次查询，
     * 不在循环里按行查（T10 的 doctorNamesOf 同一条纪律）。
     *
     * <p><b>取不到就留空，不编</b>：库里现存一批指向已消失预约的退款行（前面几张卡的测试遗留），
     * 这些行退的是"哪一单"确实无从得知。前端那一格显示「—」。
     */
    private Map<String, String> resolveRelatedOrderNos(List<RefundRecord> rows) {
        Map<String, String> result = new HashMap<>();
        Set<Long> appointmentIds = new HashSet<>();
        Set<Long> rechargeIds = new HashSet<>();
        Set<Long> paymentIds = new HashSet<>();
        for (RefundRecord row : rows) {
            if (row.getRelatedId() == null || row.getRelatedType() == null) {
                continue;
            }
            switch (row.getRelatedType()) {
                case RELATED_TYPE_APPOINTMENT -> appointmentIds.add(row.getRelatedId());
                case RELATED_TYPE_RECHARGE -> rechargeIds.add(row.getRelatedId());
                case RELATED_TYPE_PAYMENT -> paymentIds.add(row.getRelatedId());
                default -> { }
            }
        }
        if (!appointmentIds.isEmpty()) {
            appointmentMapper.selectBatchIds(appointmentIds)
                    .forEach(row -> result.put(relatedKey(RELATED_TYPE_APPOINTMENT, row.getId()), row.getOrderNo()));
        }
        if (!rechargeIds.isEmpty()) {
            rechargeRecordMapper.selectBatchIds(rechargeIds)
                    .forEach(row -> result.put(relatedKey(RELATED_TYPE_RECHARGE, row.getId()), row.getOrderNo()));
        }
        if (!paymentIds.isEmpty()) {
            paymentRecordMapper.selectBatchIds(paymentIds)
                    .forEach(row -> result.put(relatedKey(RELATED_TYPE_PAYMENT, row.getId()), row.getOrderNo()));
        }
        return result;
    }

    /**
     * related_id 是三张表各自的自增 id，直接拿它当 map 的键会互相撞（APPOINTMENT 的 3 号和
     * PAYMENT 的 3 号是两笔不同的钱）。所以键是 {@code 类型#id} 拼出来的复合串。
     */
    private static String relatedKey(String relatedType, Long relatedId) {
        return relatedType + "#" + relatedId;
    }

    private Map<Long, Patient> patientsByIds(Collection<Long> ids) {
        Set<Long> unique = new HashSet<>(ids.stream().filter(java.util.Objects::nonNull).toList());
        Map<Long, Patient> result = new HashMap<>();
        if (unique.isEmpty()) {
            return result;
        }
        for (Patient patient : patientMapper.selectByIdsIncludingDeleted(unique)) {
            result.put(patient.getId(), patient);
        }
        return result;
    }

    private Map<Long, Inpatient> inpatientsByIds(Collection<Long> ids) {
        Set<Long> unique = new HashSet<>(ids.stream().filter(java.util.Objects::nonNull).toList());
        Map<Long, Inpatient> result = new HashMap<>();
        if (unique.isEmpty()) {
            return result;
        }
        for (Inpatient inpatient : inpatientMapper.selectBatchIds(unique)) {
            result.put(inpatient.getId(), inpatient);
        }
        return result;
    }
}
