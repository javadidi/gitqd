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

    /**
     * 员工侧接口的"操作人 id"只能从这里取，**不接受请求参数传的 reviewerId**。
     *
     * <p>T26 之前没人需要它：审计的 operator 是切面自己从 {@code SecurityContextHolder} 里读的，
     * 业务表里没有"经手人"这一列。{@code refund_record.reviewer_id}（V1:180）是第一个
     * 要把审核人**存成业务数据**的列——写了这条，"谁批的款"就不再只活在审计流水里，
     * 而会显示在财务页面上。所以它必须和 {@link #currentUserId()} 一样是服务端强制的：
     * 若从 body 里取，任何能调审核接口的人都能把单据签在别人名下。
     */
    public static Long currentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser staff) {
            return staff.getAdminId();
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }
}
