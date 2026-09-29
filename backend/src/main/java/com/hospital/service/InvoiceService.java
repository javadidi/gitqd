package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.common.ErrorCode;
import com.hospital.dto.InvoiceDetailResponse;
import com.hospital.dto.InvoiceListItemResponse;
import com.hospital.dto.InvoicePendingResponse;
import com.hospital.entity.Invoice;
import com.hospital.entity.Patient;
import com.hospital.entity.PaymentRecord;
import com.hospital.enums.SerialType;
import com.hospital.exception.BizException;
import com.hospital.mapper.InvoiceMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PaymentRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 电子发票（T19 卡片 578–592 行：待开具 / 开票申请 / 已开具 / 票据详情）。
 *
 * <h2>首版是<strong>模拟开票</strong>，这是卡片给的红线而不是偷懒</h2>
 * 卡片 586 行逐字：「不做真实开票（二期做）；首版仅模拟开票流程」。
 * 落到代码上就是三件事：
 * <ul>
 *   <li>{@code invoice_code}（V1:241「发票代码」）写一个明显是假的
 *       {@code MOCK-<发票编号>}，<b>不编一个看起来能查验的 12 位数字</b> ——
 *       真发票代码会被患者拿去税务平台验真，编一个像真的就是造假凭证。
 *       这与 T14 给模拟支付通道号 {@code MOCK_TXN_RC_*} 是同一条做法。</li>
 *   <li>申请即成功（一步写 {@code ISSUED}）：真实通道会有"受理中"，
 *       而规格没定义过受理态、也没有回调可等。{@code PENDING} 这个值
 *       （V1:243 列注释给了 PENDING/ISSUED）在本卡<strong>不产生</strong>，
 *       但列表与详情仍按状态回原值，二期接通道时只改写入侧。</li>
 *   <li>PRD 152 行的「及下载」不做：没有文件可下（表里没有文件列，也不产生文件）。</li>
 * </ul>
 *
 * <h2>金额只有一个出处</h2>
 * {@code invoice.amount_fen} 由服务端从 {@code payment_record.amount_fen} 抄过来，
 * 客户端<b>没有任何途径</b>影响它（入参只有一个 {@code paymentId}，见
 * {@link com.hospital.dto.InvoiceCreateRequest}）。发票是要拿去报销的凭证，
 * "缴 40 开 400"必须是结构上不可能，而不是"前端没给按钮"。
 *
 * <h2>一张缴费单只能开一次：双层守卫</h2>
 * service 先查一次给友好提示（3005），{@code uk_payment_id}（V5）兜住并发。
 * 与 R1（T08 卡号）、R2（T11 排班三元组）完全同一个套路；
 * 撞索引的输家会被 {@link #apply} 末尾的 catch 翻译回同一个 3005，
 * 所以"连点两次"和"两台设备同时点"都只有一张票。
 *
 * <h2>归属要跳两跳</h2>
 * {@code invoice} 表既没有 {@code user_id} 也没有 {@code patient_id}（V1:237-246 只有七列），
 * 所以"这张票是不是你的"必须走 {@code invoice.payment_id → payment_record.patient_id →
 * patient.user_id}。少一跳，改一个发票 id 就能看见别人的消费金额与项目明细。
 */
@Service
public class InvoiceService {

    private static final String PENDING = "PENDING";
    private static final String SUCCESS = "SUCCESS";
    /** V1:243 列注释给的两个状态里的"已开具"。模拟阶段开票即成功，所以本卡只写这一个。 */
    private static final String ISSUED = "ISSUED";

    private final InvoiceMapper invoiceMapper;
    private final PaymentRecordMapper paymentMapper;
    private final PatientMapper patientMapper;
    private final SerialNumberService serialNumberService;
    private final OutpatientPaymentService paymentQueryHelper;

    public InvoiceService(InvoiceMapper invoiceMapper,
                          PaymentRecordMapper paymentMapper,
                          PatientMapper patientMapper,
                          SerialNumberService serialNumberService,
                          OutpatientPaymentService paymentQueryHelper) {
        this.invoiceMapper = invoiceMapper;
        this.paymentMapper = paymentMapper;
        this.patientMapper = patientMapper;
        this.serialNumberService = serialNumberService;
        // 只借它一个 items 解析路径（T15 的私有方法为本卡开成包级可见）。
        // 不叫它去做写操作：本服务自己管全部写。
        this.paymentQueryHelper = paymentQueryHelper;
    }

    /**
     * 待开具电子发票（卡片 581 行）：本人就诊人名下、已缴成功、且还没开过票的缴费单。
     *
     * <p>为什么以"缴费单"为主体而不是"发票"：还没开票的发票根本不存在，
     * 拿发票表去 LEFT 缴费表会得到一堆空行；反过来（缴费单减去已开票的）
     * 才是"可开票"这三个字的直译。
     */
    public List<InvoicePendingResponse> listPending(Long userId) {
        List<Long> myPatientIds = myPatientIds(userId);
        if (myPatientIds.isEmpty()) {
            return List.of();
        }
        List<PaymentRecord> paid = paymentMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                .in(PaymentRecord::getPatientId, myPatientIds)
                .eq(PaymentRecord::getStatus, SUCCESS)
                .orderByDesc(PaymentRecord::getCreatedAt)
                .orderByDesc(PaymentRecord::getId));
        if (paid.isEmpty()) {
            return List.of();
        }
        Set<Long> invoicedPaymentIds = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                        .in(Invoice::getPaymentId, paid.stream().map(PaymentRecord::getId).toList()))
                .stream().map(Invoice::getPaymentId).collect(Collectors.toSet());
        Map<Long, String> patientNames = patientNamesOf(paid.stream()
                .map(PaymentRecord::getPatientId).filter(Objects::nonNull).distinct().toList());

        return paid.stream()
                .filter(row -> !invoicedPaymentIds.contains(row.getId()))
                .map(row -> {
                    InvoicePendingResponse item = new InvoicePendingResponse();
                    item.setPaymentId(row.getId());
                    item.setOrderNo(row.getOrderNo());
                    item.setPatientName(patientNames.get(row.getPatientId()));
                    item.setAmountFen(row.getAmountFen());
                    item.setPaidAt(row.getCreatedAt());
                    return item;
                })
                .toList();
    }

    /**
     * 开票申请（卡片 582 行，J43「开票申请 → 发票记录创建」）。
     *
     * <p>一个事务里两步：建发票行 + 写审计。中间任何一步失败都不能只留下半张票。
     * 缴费单本身<b>不改</b>——发票与缴费单是两个实体，开票不等于缴费状态变化
     * （{@code payment_record} 里没有"已开票"这个状态，V1:163 只有 PENDING/SUCCESS/REFUNDED）。
     */
    @AuditLog(action = "CREATE_INVOICE", targetType = "invoice")
    @Transactional
    public InvoiceDetailResponse apply(Long userId, Long paymentId) {
        PaymentRecord payment = paymentMapper.selectById(paymentId);
        // 不是你的、没这条、还没缴 → 一律 5001。
        // "还没缴"不给单独错误码是有意的：那意味着客户端在提交一张根本不在待开具列表里的单，
        // 与"这张单不是你的"同样不可解释，回 5001 不泄露信息也不诱导重试。
        if (payment == null || !SUCCESS.equals(payment.getStatus())
                || !myPatientIds(userId).contains(payment.getPatientId())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        if (findInvoiceByPaymentId(paymentId) != null) {
            throw new BizException(ErrorCode.INVOICE_EXISTS_FOR_PAYMENT);
        }

        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(serialNumberService.next(SerialType.FP));
        invoice.setPaymentId(payment.getId());
        // 模拟发票代码：一眼假，不给"拿去验真"留任何余地。
        invoice.setInvoiceCode("MOCK-" + invoice.getInvoiceNo());
        invoice.setAmountFen(payment.getAmountFen());
        invoice.setStatus(ISSUED);
        try {
            invoiceMapper.insert(invoice);
        } catch (DuplicateKeyException e) {
            // 并发输家：前置查没查到，但 uk_payment_id（V5）已经被人占了。
            // 翻译回同一个 3005，患者看到的与手点两次完全一致。
            throw new BizException(ErrorCode.INVOICE_EXISTS_FOR_PAYMENT);
        }
        // 回读一次拿数据库里的时间戳（MP 插入后 createdAt 是 JVM 算的值，
        // 而详情里 issuedAt 与列表口径必须一致 —— 统一以读路径为准）。
        return detail(userId, requireId(invoice));
    }

    /** 已开具电子发票（卡片 583 行）：本人全部发票，按开票时间倒序。 */
    public List<InvoiceListItemResponse> list(Long userId) {
        List<PaymentRecord> myPayments = myPayments(userId);
        if (myPayments.isEmpty()) {
            return List.of();
        }
        List<Invoice> invoices = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                .in(Invoice::getPaymentId, myPayments.stream().map(PaymentRecord::getId).toList())
                .orderByDesc(Invoice::getCreatedAt)
                .orderByDesc(Invoice::getId));
        if (invoices.isEmpty()) {
            return List.of();
        }
        Map<Long, PaymentRecord> paymentsById = myPayments.stream()
                .collect(Collectors.toMap(PaymentRecord::getId, p -> p));
        Map<Long, String> patientNames = patientNamesOf(paymentsById.values().stream()
                .map(PaymentRecord::getPatientId).filter(Objects::nonNull).distinct().toList());

        return invoices.stream().map(invoice -> {
            PaymentRecord payment = paymentsById.get(invoice.getPaymentId());
            InvoiceListItemResponse item = new InvoiceListItemResponse();
            item.setInvoiceId(invoice.getId());
            item.setInvoiceNo(invoice.getInvoiceNo());
            item.setInvoiceCode(invoice.getInvoiceCode());
            item.setStatus(invoice.getStatus());
            item.setAmountFen(invoice.getAmountFen());
            item.setPaymentOrderNo(payment == null ? null : payment.getOrderNo());
            item.setPatientName(payment == null ? null : patientNames.get(payment.getPatientId()));
            item.setIssuedAt(invoice.getCreatedAt());
            return item;
        }).toList();
    }

    /** 票据详情（卡片 584 行，J44「票据详情 → 内容正确」）。越权与不存在同为 5001。 */
    public InvoiceDetailResponse detail(Long userId, Long invoiceId) {
        Invoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        PaymentRecord payment = paymentMapper.selectById(invoice.getPaymentId());
        if (payment == null || !myPatientIds(userId).contains(payment.getPatientId())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Patient patient = patientMapper.selectById(payment.getPatientId());

        InvoiceDetailResponse detail = new InvoiceDetailResponse();
        detail.setInvoiceId(invoice.getId());
        detail.setInvoiceNo(invoice.getInvoiceNo());
        detail.setInvoiceCode(invoice.getInvoiceCode());
        detail.setStatus(invoice.getStatus());
        detail.setAmountFen(invoice.getAmountFen());
        detail.setPatientName(patient == null ? null : patient.getName());
        detail.setPaymentOrderNo(payment.getOrderNo());
        detail.setIssuedAt(invoice.getCreatedAt());
        detail.setItems(paymentQueryHelper.parseItems(payment.getItems()));
        return detail;
    }

    // ============================================================
    // 助手
    // ============================================================

    private Invoice findInvoiceByPaymentId(Long paymentId) {
        return invoiceMapper.selectOne(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getPaymentId, paymentId)
                .orderByAsc(Invoice::getId)
                .last("LIMIT 1"));
    }

    /**
     * 插入后取回主键。
     *
     * <p>MP 在 {@code @TableId(AUTO)} 下会把 MySQL 回填的自增 id 写回实体，
     * 但"应该会回填"不是证据 —— 一旦哪天注解丢了（雪花 id 那条 T14 教训），
     * 这里会拿到 null 而不是静默把 null 传进详情查询。所以显式判空并抛 5001。
     */
    private Long requireId(Invoice invoice) {
        if (invoice.getId() == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return invoice.getId();
    }

    private List<PaymentRecord> myPayments(Long userId) {
        List<Long> myPatientIds = myPatientIds(userId);
        if (myPatientIds.isEmpty()) {
            return List.of();
        }
        return paymentMapper.selectList(new LambdaQueryWrapper<PaymentRecord>()
                .in(PaymentRecord::getPatientId, myPatientIds));
    }

    private List<Long> myPatientIds(Long userId) {
        return patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
    }

    private Map<Long, String> patientNamesOf(List<Long> patientIds) {
        if (patientIds.isEmpty()) {
            return Map.of();
        }
        return patientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getName));
    }
}
