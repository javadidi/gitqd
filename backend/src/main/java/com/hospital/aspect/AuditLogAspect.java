package com.hospital.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditReason;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.enums.OperatorType;
import com.hospital.exception.BizException;
import com.hospital.security.LoginPatient;
import com.hospital.security.LoginUser;
import com.hospital.service.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
@Order(100)
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.hospital.annotation.AuditLog)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        AuditLog ann = sig.getMethod().getAnnotation(AuditLog.class);

        Operator operator = currentOperator();
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        Long targetId = findTargetId(sig, joinPoint.getArgs());
        String reason = findReason(sig, joinPoint.getArgs());

        auditLogService.write(operator.id(), operator.type().name(),
                ann.action(), ann.targetType(), targetId, reason, buildDetail(sig, joinPoint.getArgs()));

        return joinPoint.proceed();
    }

    /**
     * 审计主体。切面原本直接拿 {@code LoginUser.getAdminId()}，只能覆盖员工；
     * T12 卡片 453 行第⑨步要求患者侧的预约动作也留痕，所以这里改成"主体 → (id, type)"的一层映射。
     *
     * <p>{@code operator_id} 从此是按 type 分流的多态列（{@link OperatorType} 的注释里有完整说明），
     * 消费这条流水的管理端页面在按 id 关联人名时必须先看 type。
     */
    private record Operator(Long id, OperatorType type) {}

    /**
     * <b>认不出主体就抛 401，这条不放</b>：匿名主体走到这里说明有该留痕的写操作在没人格的身份下执行，
     * 静默记成 SYSTEM 会让流水失去追责价值。
     * 唯一"没有自然人"的合法审计是微信支付回调，它不走本切面，
     * 由 {@code AppointmentService} 用 {@code AuditLogService} 显式写一条 {@code SYSTEM} 记录。
     */
    private Operator currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser staff) {
            return new Operator(staff.getAdminId(), OperatorType.fromRole(staff.getRoleName()));
        }
        if (principal instanceof LoginPatient patient) {
            return new Operator(patient.getUserId(), OperatorType.PATIENT);
        }
        return null;
    }

    private Long findTargetId(MethodSignature sig, Object[] args) {
        Annotation[][] paramAnnos = sig.getMethod().getParameterAnnotations();
        for (int i = 0; i < paramAnnos.length; i++) {
            if (i >= args.length) {
                break;
            }
            for (Annotation a : paramAnnos[i]) {
                if (a instanceof AuditTarget && args[i] instanceof Long) {
                    return (Long) args[i];
                }
            }
        }
        return null;
    }

    private String findReason(MethodSignature sig, Object[] args) {
        Annotation[][] paramAnnos = sig.getMethod().getParameterAnnotations();
        for (int i = 0; i < paramAnnos.length; i++) {
            if (i >= args.length) {
                break;
            }
            for (Annotation a : paramAnnos[i]) {
                if (a instanceof AuditReason && args[i] instanceof String) {
                    return (String) args[i];
                }
            }
        }
        return null;
    }

    private String buildDetail(MethodSignature sig, Object[] args) {
        String[] names = sig.getParameterNames();
        Map<String, Object> detail = new LinkedHashMap<>();
        for (int i = 0; i < names.length && i < args.length; i++) {
            detail.put(names[i], args[i]);
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return null;
        }
    }
}
