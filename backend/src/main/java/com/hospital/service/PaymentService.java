package com.hospital.service;

import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditReason;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.entity.PaymentRecord;
import com.hospital.exception.BizException;
import com.hospital.mapper.PaymentRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRecordMapper paymentRecordMapper;

    public PaymentService(PaymentRecordMapper paymentRecordMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
    }

    @AuditLog(action = "APPROVE_REFUND", targetType = "payment_record")
    @Transactional
    public void approveRefund(@AuditTarget Long paymentId, @AuditReason String reason) {
        PaymentRecord record = paymentRecordMapper.selectById(paymentId);
        if (record == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        record.setStatus("REFUND_APPROVED");
        paymentRecordMapper.updateById(record);
    }

    @AuditLog(action = "J8_ROLLBACK_PROBE", targetType = "payment_record")
    @Transactional
    public void crashAfterAudit(Long paymentId, String reason) {
        PaymentRecord record = new PaymentRecord();
        record.setOrderNo("JF-CRASH-TEST");
        record.setPatientId(paymentId);
        record.setItems("[]");
        record.setAmountFen(1L);
        record.setPayMethod("MOCK");
        record.setStatus("PENDING");
        paymentRecordMapper.insert(record);

        throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "模拟业务失败");
    }
}
