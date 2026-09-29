package com.hospital.service;

import com.hospital.entity.Appointment;
import com.hospital.entity.RefundRecord;
import com.hospital.enums.SerialType;
import com.hospital.mapper.RefundRecordMapper;
import org.springframework.stereotype.Service;

/**
 * 退款挂单的唯一实现（T25 抽出，T13 定下的规则不变）。
 *
 * <p><b>为什么抽出来</b>：T13 的退号要挂一张退款单，T25 的停诊要同时退掉一批号、
 * 每张也要挂退款单。两处规则一字不差：只有已支付（CONFIRMED）的单挂单、
 * 金额取预约账上的 {@code fee_fen} 不接受任何外部传入、状态只到 PENDING、
 * {@code reviewer_id} 留空。财务规则复制两份迟早会漂移——
 * 与 T08 抽 {@code MaskUtil} 是同一条理由（那边是打码逻辑，漂了就有人看见明文）。
 *
 * <p><b>本服务不开事务</b>：它是被调用方，事务边界在
 * {@code AppointmentService.cancel} 与 {@code ScheduleService.suspend} 上，
 * 挂单必须和退号同生共死。这里加 {@code @Transactional} 只会制造"看起来能独立提交"的错觉。
 */
@Service
public class RefundTicketService {

    /** V1:176 的 related_type 三个取值之一。 */
    public static final String RELATED_TYPE_APPOINTMENT = "APPOINTMENT";
    /** 卡片 479 行红线「退款需审核（二期做）」→ 只挂单到 PENDING，不做任何真实出款。 */
    public static final String REFUND_PENDING = "PENDING";
    /** appointment.status 的已支付态（V1:124）；只有它进过钱，才有款可退。 */
    private static final String CONFIRMED = "CONFIRMED";

    private final RefundRecordMapper refundRecordMapper;
    private final SerialNumberService serialNumberService;

    public RefundTicketService(RefundRecordMapper refundRecordMapper,
                               SerialNumberService serialNumberService) {
        this.refundRecordMapper = refundRecordMapper;
        this.serialNumberService = serialNumberService;
    }

    /**
     * 为一张已支付的预约挂退款单。
     *
     * @return 挂出来的退款单；未支付（待支付/已完成/已取消）的单返回 null——
     *         没收过钱就无款可退，挂一张 0 元退款单只会污染 T26 的对账。
     */
    public RefundRecord issueForAppointment(Appointment appointment, String reason) {
        if (!CONFIRMED.equals(appointment.getStatus())) {
            return null;
        }
        RefundRecord refund = new RefundRecord();
        refund.setOrderNo(serialNumberService.next(SerialType.TK));
        refund.setRelatedId(appointment.getId());
        refund.setRelatedType(RELATED_TYPE_APPOINTMENT);
        refund.setAmountFen(appointment.getFeeFen());
        refund.setStatus(REFUND_PENDING);
        refund.setReason(reason);
        // reviewer_id 留空：审核是二期的事，现在没有任何人审过这笔
        refundRecordMapper.insert(refund);
        return refund;
    }
}
