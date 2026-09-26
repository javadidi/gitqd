package com.hospital.dto;

import com.hospital.common.ErrorCode;
import com.hospital.enums.OperatorType;
import com.hospital.exception.BizException;
import com.hospital.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 任务内核的操作者上下文。内核不读 ThreadLocal，由入口层构造后传入，
 * 使定时任务、内部触发等非 HTTP 场景也能留痕，且审计记录的人与实际操作的人不会错位。
 */
public record TaskContext(Long operatorId, String operatorType) {

    /**
     * HTTP 入口必须用这个方法构造，避免操作者由请求体自报。
     */
    public static TaskContext ofCurrent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return new TaskContext(user.getAdminId(), OperatorType.fromRole(user.getRoleName()).name());
    }
}
