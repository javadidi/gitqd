package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.common.ErrorCode;
import com.hospital.dto.RechargeCreateRequest;
import com.hospital.dto.RechargeResponse;
import com.hospital.entity.Patient;
import com.hospital.entity.RechargeRecord;
import com.hospital.enums.SerialType;
import com.hospital.exception.BizException;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.RechargeRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 门诊充值（T14 卡片 493–496 行：充值页面 / 支付成功 / 充值记录）。
 *
 * <h2>一个事务里的四步</h2>
 * 建充值单 → 发起支付 → 单据置 SUCCESS → 余额加账，外加审计。四步必须同生共死：
 * 单据 SUCCESS 了但余额没加，患者充的钱进了虚空；余额加了但单据还 PENDING，
 * 对账时这笔钱在两张表里说法不一致，而 {@code recharge_record} 是财务单据表（V1:138，无软删）。
 *
 * <h2>余额只进不出</h2>
 * 卡片 498 行红线「不做缴费（T15）；不做退款（T19）」，所以本卡只有 {@code addBalance} 这一条加法。
 * 减法（含 {@code balance_fen >= ?} 下界守卫）留给 T15，理由写在
 * {@link PatientMapper#addBalance} 的注释里。
 *
 * <h2>金额与支付方式都不接受客户端"选择"</h2>
 * <ul>
 *   <li><b>金额</b>只有正数校验，<b>没有上限</b>：PRD 与卡片都没给过任何限额，
 *       自造一个"单笔最多 5000 元"就是替规格编数字（同 T09 不给住院号编正则、
 *       T11 不校验过去日期是同一条规矩）。列是 BIGINT，溢出不是现实风险。</li>
 *   <li><b>支付方式不在入参里</b>：卡片 494 行写的是「选择支付方式（微信支付）」，
 *       括号里已经把它钉成微信；而 {@code pay_method} 的四个取值里 ALIPAY/CASH
 *       在本系统没有任何通道。让客户端传这个字段，等于允许它声明一个系统根本不支持的支付方式，
 *       所以由服务端固定写 {@code WECHAT}。</li>
 * </ul>
 */
@Service
public class RechargeService {

    private static final String PENDING = "PENDING";
    private static final String SUCCESS = "SUCCESS";
    private static final String PAY_METHOD = "WECHAT";

    private final RechargeRecordMapper rechargeMapper;
    private final PatientMapper patientMapper;
    private final SerialNumberService serialNumberService;
    private final WechatPayService wechatPayService;

    public RechargeService(RechargeRecordMapper rechargeMapper,
                           PatientMapper patientMapper,
                           SerialNumberService serialNumberService,
                           WechatPayService wechatPayService) {
        this.rechargeMapper = rechargeMapper;
        this.patientMapper = patientMapper;
        this.serialNumberService = serialNumberService;
        this.wechatPayService = wechatPayService;
    }

    /**
     * 充值并即时到账（J33：充值 → 就诊卡余额增加 + 充值记录）。
     *
     * <p>写序是"先建单、再加钱"：单号要先存在，支付与流水才有 {@code out_trade_no} 可挂；
     * 而 {@code addBalance} 返回 0（卡不是你的 / 已被删）会让整笔事务回滚，
     * 不会留下一张成功了却没到账的充值单。
     */
    @AuditLog(action = "CREATE_RECHARGE", targetType = "recharge_record")
    @Transactional
    public RechargeResponse recharge(Long userId, RechargeCreateRequest request) {
        long amountFen = request.getAmountFen();

        RechargeRecord record = new RechargeRecord();
        record.setOrderNo(serialNumberService.next(SerialType.CF));
        record.setPatientId(request.getPatientId());
        record.setAmountFen(amountFen);
        record.setPayMethod(PAY_METHOD);
        record.setStatus(PENDING);
        rechargeMapper.insert(record);

        wechatPayService.prepay(record.getOrderNo(), amountFen);

        // 幂等守卫：只有还在 PENDING 的单能被推进。本方法内部一次性完成，
        // 但这条 WHERE 让"同一张单被重复置成功"在数据库层面不可能，
        // 也就让将来接真实回调时不必再补一层判重。
        //
        // 流水号先算进变量、再同时用于 UPDATE 与出参：第一版只在库里写了 trade_no，
        // 内存对象没同步，于是接口回了个 null——测试当场逮住。
        // 这类"内存与库不一致"没有别的解法，只能让同一个值恰好算一次。
        String tradeNo = mockTradeNo(record.getId());
        if (rechargeMapper.markSuccess(record.getId(), tradeNo) == 0) {
            throw new BizException(ErrorCode.PAYMENT_FAILED);
        }

        if (patientMapper.addBalance(request.getPatientId(), userId, amountFen) == 0) {
            // 不是自己的就诊人与没有这个就诊人同码（与 T08/T09/T13 一致，不给枚举机会）
            throw new BizException(ErrorCode.PATIENT_NOT_FOUND);
        }

        Patient patient = patientMapper.selectById(request.getPatientId());
        record.setStatus(SUCCESS);
        record.setTradeNo(tradeNo);
        return toResponse(record, patient);
    }

    /** 充值记录列表（卡片 496 行 / PRD 296 行）：本人全部就诊人的充值流水。 */
    public List<RechargeResponse> list(Long userId) {
        List<Long> myPatientIds = patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
        if (myPatientIds.isEmpty()) {
            return List.of();
        }
        List<RechargeRecord> rows = rechargeMapper.selectList(new LambdaQueryWrapper<RechargeRecord>()
                .in(RechargeRecord::getPatientId, myPatientIds)
                .orderByDesc(RechargeRecord::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> patientNames = patientMapper.selectBatchIds(myPatientIds).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getName));
        return rows.stream()
                .map(row -> toResponse(row, nameOnly(row.getPatientId(), patientNames)))
                .toList();
    }

    /** 账单详情（PRD 297 行「账单详情 — 查看单笔充值明细」）。 */
    public RechargeResponse detail(Long userId, Long rechargeId) {
        RechargeRecord record = rechargeMapper.selectById(rechargeId);
        if (record == null || record.getPatientId() == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Patient patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getId, record.getPatientId())
                .eq(Patient::getUserId, userId));
        if (patient == null) {
            // 别人的充值单与没这张单同码；充值单本身没有"存在但不属于你"这种可区分状态
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return toResponse(record, patient);
    }

    /**
     * mock 通道下的微信流水号占位。真实对接后由回调里的 {@code transaction_id} 提供，
     * 那时这个方法就该删掉——留着一个"自己编的流水号"在财务表里，比留空更容易骗过后面的对账。
     */
    private String mockTradeNo(Long rechargeId) {
        return "MOCK_TXN_RC_" + rechargeId;
    }

    private RechargeResponse toResponse(RechargeRecord record, Patient patient) {
        RechargeResponse response = new RechargeResponse();
        response.setId(record.getId());
        response.setOrderNo(record.getOrderNo());
        response.setPatientId(record.getPatientId());
        response.setPatientName(patient == null ? null : patient.getName());
        response.setAmountFen(record.getAmountFen());
        response.setStatus(record.getStatus());
        response.setPayMethod(record.getPayMethod());
        response.setTradeNo(record.getTradeNo());
        // 到账后的余额：PRD 98 行说"实时到账"，成功页要拿它当证据，否则患者只能相信
        response.setBalanceFen(patient == null ? null : patient.getBalanceFen());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private Patient nameOnly(Long patientId, Map<Long, String> names) {
        Patient stub = new Patient();
        stub.setId(patientId);
        stub.setName(names.get(patientId));
        return stub;
    }
}
