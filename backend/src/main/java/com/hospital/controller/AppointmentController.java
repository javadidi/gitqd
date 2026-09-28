package com.hospital.controller;

import com.hospital.common.ErrorCode;
import com.hospital.common.Result;
import com.hospital.dto.AppointmentCreateRequest;
import com.hospital.dto.AppointmentResponse;
import com.hospital.dto.PaymentResultResponse;
import com.hospital.exception.BizException;
import com.hospital.security.SecurityUtils;
import com.hospital.service.AppointmentPaymentService;
import com.hospital.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者侧的预约接口（T12）：{@code /user/appointments}。
 *
 * <p><b>只有两个端点，卡片与 PRD 各自划的边界</b>：
 * <ul>
 *   <li>{@code POST /} = 卡片 A 段「创建预约 + 发起支付」那一个事务；</li>
 *   <li>{@code POST /{id}/pay} = 首版没有真实商户凭据时，小程序完成支付的受控入口
 *       （为什么要给它一个独立入口而不是让小程序直接打回调，见
 *       {@link AppointmentPaymentService} 的类注释）；</li>
 *   <li><b>没有列表、没有详情、没有取消</b>：PRD §9.1 第 610 行那一格写的是
 *       「创建预约、取消预约、预约列表、预约详情」，后三样属 T13「预约管理 + 退号」，
 *       页面也在那里（§6.1 第 527 行的「预约挂号记录」「预约挂号详情」在个人中心模块下）。</li>
 * </ul>
 *
 * <p>不需要 {@code @RequireCap}：那是员工侧的能力位，本路径 {@code /user/**} 在
 * {@code SecurityConfig} 里已经限死 {@code hasRole("patient")}，userId 一律从 token 取
 * （{@link SecurityUtils#currentUserId()}），请求体里没有任何 userId 字段。
 */
@RestController
@RequestMapping("/user/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentPaymentService paymentService;

    public AppointmentController(AppointmentService appointmentService,
                                 AppointmentPaymentService paymentService) {
        this.appointmentService = appointmentService;
        this.paymentService = paymentService;
    }

    /** 创建预约（卡片 A 段九步）；同一个人重复提交同一时段 → 2005 */
    @PostMapping
    public Result<AppointmentResponse> create(@Valid @RequestBody AppointmentCreateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        try {
            return Result.success(appointmentService.create(userId, request));
        } catch (DuplicateKeyException | ConcurrencyFailureException e) {
            // uk_patient_schedule(patient_id, schedule_id)（V1:130）兜住的就是并发重复提交：
            // 前端连点两次、或者两个设备同时提交同一时段。和 T11 一样，
            // 翻译必须发生在 controller——service 那个事务里 catch 会把事务标成 rollback-only，
            // 最后炸成一个看不懂的 500（AppointmentService 的类注释里有这段推导）。
            throw new BizException(ErrorCode.APPOINTMENT_DUPLICATE);
        }
    }

    /**
     * 完成支付（首版 mock 通道下的患者侧入口）。
     *
     * <p>返回的是"推进后的状态"，幂等：重复点第二次拿到 {@code processed=false} 而不是报错，
     * 因为患者手抖连点两下不该看到一句"操作失败"。
     */
    @PostMapping("/{id}/pay")
    public Result<PaymentResultResponse> pay(@PathVariable Long id) {
        return Result.success(paymentService.payByPatient(SecurityUtils.currentUserId(), id));
    }
}
