package com.hospital.security;

import com.hospital.common.ErrorCode;
import com.hospital.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * 小程序端接口的 userId 只能从这里取，**绝不接受请求参数传入的 userId**
     * （附录 B 红线「小程序端新接口是否强制注入 userId 归属校验」）。
     * 否则改一个 query 参数就能看别人的病历和账单。
     */
    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginPatient patient) {
            return patient.getUserId();
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }
}
