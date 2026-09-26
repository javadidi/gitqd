package com.hospital.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditReason;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.enums.OperatorType;
import com.hospital.exception.BizException;
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

        LoginUser operator = currentOperator();
        if (operator == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        Long targetId = findTargetId(sig, joinPoint.getArgs());
        String reason = findReason(sig, joinPoint.getArgs());

        auditLogService.write(operator.getAdminId(), OperatorType.fromRole(operator.getRoleName()).name(),
                ann.action(), ann.targetType(), targetId, reason, buildDetail(sig, joinPoint.getArgs()));

        return joinPoint.proceed();
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

    private LoginUser currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        return principal instanceof LoginUser ? (LoginUser) principal : null;
    }
}
