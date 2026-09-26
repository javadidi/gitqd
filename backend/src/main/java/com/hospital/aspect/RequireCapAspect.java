package com.hospital.aspect;

import com.hospital.common.ErrorCode;
import com.hospital.enums.Capability;
import com.hospital.exception.BizException;
import com.hospital.security.LoginUser;
import com.hospital.service.PermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class RequireCapAspect {

    private final PermissionService permissionService;

    public RequireCapAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(com.hospital.annotation.RequireCap)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        Capability cap = method.getAnnotation(com.hospital.annotation.RequireCap.class).value();

        LoginUser loginUser = currentLoginUser();
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!permissionService.hasCap(loginUser.getRoleName(), cap)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        return joinPoint.proceed();
    }

    private LoginUser currentLoginUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
    }
}
