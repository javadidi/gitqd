package com.hospital;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.entity.AuditLog;
import com.hospital.entity.PaymentRecord;
import com.hospital.exception.BizException;
import com.hospital.mapper.AuditLogMapper;
import com.hospital.mapper.PaymentRecordMapper;
import com.hospital.security.LoginUser;
import com.hospital.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * J8: 审计同事务 — 业务抛异常时 audit_log 不得留下记录
 */
@SpringBootTest
class AuditLogTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsAdmin() {
        LoginUser user = new LoginUser(2L, "admin", "admin", Collections.emptyList(), Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private long countByAction(String action) {
        Long count = auditLogMapper.selectCount(
                new LambdaQueryWrapper<AuditLog>().eq(AuditLog::getAction, action));
        return count == null ? 0L : count;
    }

    @Test
    void j8_auditRollsBackWithBusiness() {
        loginAsAdmin();
        long before = countByAction("J8_ROLLBACK_PROBE");

        BizException ex = assertThrows(BizException.class,
                () -> paymentService.crashAfterAudit(999999L, "J8 回滚验证"));
        assertEquals(500, ex.getCode());

        assertEquals(before, countByAction("J8_ROLLBACK_PROBE"),
                "业务回滚后 audit_log 不得新增记录 —— 证明审计写入落在业务事务内部");
        assertNull(paymentRecordMapper.selectOne(
                        new LambdaQueryWrapper<PaymentRecord>()
                                .eq(PaymentRecord::getOrderNo, "JF-CRASH-TEST")),
                "业务自身的写入也必须一起回滚");
    }

    @Test
    void auditWrittenWithOperatorTargetAndReason() {
        loginAsAdmin();
        PaymentRecord record = new PaymentRecord();
        record.setOrderNo("JF-AUDIT-OK");
        record.setPatientId(1L);
        record.setItems("[]");
        record.setAmountFen(100L);
        record.setPayMethod("MOCK");
        record.setStatus("PENDING");
        paymentRecordMapper.insert(record);

        long before = countByAction("APPROVE_REFUND");
        try {
            paymentService.approveRefund(record.getId(), "患者申请退款");

            AuditLog log = auditLogMapper.selectOne(
                    new LambdaQueryWrapper<AuditLog>()
                            .eq(AuditLog::getAction, "APPROVE_REFUND")
                            .orderByDesc(AuditLog::getId)
                            .last("LIMIT 1"));
            assertNotNull(log, "业务成功后必须留下审计记录");
            assertEquals(2L, log.getOperatorId(), "operatorId 取自 SecurityContext 的 adminId");
            assertEquals("ADMIN", log.getOperatorType(), "admin 角色应映射为 ADMIN");
            assertEquals("payment_record", log.getTargetType());
            assertEquals(record.getId(), log.getTargetId(), "targetId 来自 @AuditTarget 标注的入参");
            assertEquals("患者申请退款", log.getReason(), "reason 来自 @AuditReason 标注的入参");
            assertNotNull(log.getDetail(), "detail 应记录入参 JSON");
            assertTrue(log.getDetail().contains("paymentId"), "detail JSON 应含参数名");
            assertEquals(before + 1, countByAction("APPROVE_REFUND"));

            assertEquals("REFUND_APPROVED",
                    paymentRecordMapper.selectById(record.getId()).getStatus(), "业务本身应已生效");
        } finally {
            paymentRecordMapper.deleteById(record.getId());
        }
    }

    @Test
    void auditRejectsWhenNoOperator() {
        SecurityContextHolder.clearContext();

        BizException ex = assertThrows(BizException.class,
                () -> paymentService.approveRefund(1L, "无登录态"));
        assertEquals(401, ex.getCode(), "无登录态不允许留痕，也不允许执行业务");
    }
}
