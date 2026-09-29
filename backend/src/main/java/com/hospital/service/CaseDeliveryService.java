package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.common.ErrorCode;
import com.hospital.dto.CaseDeliveryCreateRequest;
import com.hospital.dto.CaseDeliveryResponse;
import com.hospital.entity.CaseDelivery;
import com.hospital.entity.Inpatient;
import com.hospital.exception.BizException;
import com.hospital.mapper.CaseDeliveryMapper;
import com.hospital.mapper.InpatientMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 病案配送申请（T23 卡片 661 行「病案配送：填写邮寄申请信息…」+ PRD 237-245 行六页流程 +
 * PRD 314-316 行「病案申请邮寄记录：列表 / 申请详情」）。
 *
 * <h2>本卡写的是这张表的哪些列</h2>
 * {@code case_delivery}（V1:328-339）一共八列业务字段，患者提交时只填得动三列：
 * {@code inpatient_id}（先验归属）、{@code recipient_name}、{@code address}；
 * 服务端再固定写 {@code status = 'PENDING'}。剩下两列首版一律留 NULL，且都不是"忘了写"：
 * <ul>
 *   <li>{@code id_card_photo}——全系统没有上传通道（后端零 {@code MultipartFile}、
 *       小程序零 {@code wx.uploadFile}），编一个路径字符串进财务之外的这张表没有意义；</li>
 *   <li>{@code tracking_no}——快递单号是"医院已经把病案交寄"之后才存在的事实，
 *       它的生产者是有后台页面的 T26（卡片 720 行「病案配送记录/详情」），不是患者。</li>
 * </ul>
 *
 * <h2>「支付配送费」这一步为什么整段缺席</h2>
 * 卡片 661 行确实写了这三个字，但它无处安放：这张表没有费用列、PRD 591 行数据字典的
 * 病案配送一行也没有费用，系统里不存在配送费价目；而唯一记钱的 {@code payment_record}
 * 要求 {@code patient_id NOT NULL}（V1:159），住院人表（V1:41-53）没有任何指向就诊人的列。
 * <b>结论是"不收这笔钱"，而不是"收一笔我编出来的钱"</b>——一个凭空的 20 元配送费
 * 会同时污染财务表、发票（T19 按缴费单开票）与后台对账（附录 A 二期）。
 * 页面据此也不显示任何金额行，遗留 TODO 里写明补齐它需要的三样东西。
 *
 * <h2>不做「同一住院人只能有一份在途申请」这类判重</h2>
 * 卡片、PRD §3.9.3（237-245 行）、§7 流程（557-561 行）都没提过这个约束，
 * 表上也没有相应索引。自造一条"重复申请被拒"是给规格发明规则（同 T09 不给住院号编正则）。
 * 患者提交几份申请，后台就审几份。
 *
 * <h2>归属</h2>
 * 住院人必须属于当前用户，判法与 T09/{@link InpatientRechargeService} 完全一致
 * （{@code (id, user_id)} 双条件，查不到与不是本人同回 1005，不给枚举机会）。
 * 列表按「我的住院人 id 集合」过滤；详情按 {@code (id, 我的住院人)} 二次确认，
 * 别人的申请与没有这份申请同回 5001。
 */
@Service
public class CaseDeliveryService {

    private static final String PENDING = "PENDING";

    private final CaseDeliveryMapper caseDeliveryMapper;
    private final InpatientMapper inpatientMapper;

    public CaseDeliveryService(CaseDeliveryMapper caseDeliveryMapper, InpatientMapper inpatientMapper) {
        this.caseDeliveryMapper = caseDeliveryMapper;
        this.inpatientMapper = inpatientMapper;
    }

    /** 提交邮寄申请（J52：病案配送 → 申请创建）。 */
    @AuditLog(action = "CREATE_CASE_DELIVERY", targetType = "case_delivery")
    @Transactional
    public CaseDeliveryResponse create(Long userId, CaseDeliveryCreateRequest request) {
        Inpatient inpatient = requireOwned(userId, request.getInpatientId());

        CaseDelivery delivery = new CaseDelivery();
        delivery.setInpatientId(inpatient.getId());
        delivery.setRecipientName(request.getRecipientName());
        delivery.setAddress(request.getAddress());
        delivery.setStatus(PENDING);
        // trackingNo 与 idCardPhoto 不赋值：见类注释「本卡写的是这张表的哪些列」。
        caseDeliveryMapper.insert(delivery);
        return toResponse(delivery, inpatient);
    }

    /** 申请记录列表（PRD 315 行）。 */
    public List<CaseDeliveryResponse> list(Long userId) {
        List<Inpatient> mine = inpatientMapper.selectList(
                new LambdaQueryWrapper<Inpatient>().eq(Inpatient::getUserId, userId));
        if (mine.isEmpty()) {
            return List.of();
        }
        List<Long> myIds = mine.stream().map(Inpatient::getId).toList();
        List<CaseDelivery> rows = caseDeliveryMapper.selectList(new LambdaQueryWrapper<CaseDelivery>()
                .in(CaseDelivery::getInpatientId, myIds)
                .orderByDesc(CaseDelivery::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Inpatient> byId = mine.stream()
                .collect(Collectors.toMap(Inpatient::getId, row -> row));
        return rows.stream()
                .map(row -> toResponse(row, byId.get(row.getInpatientId())))
                .toList();
    }

    /** 申请详情（PRD 316 行「查看申请详情及物流状态」）。 */
    public CaseDeliveryResponse detail(Long userId, Long deliveryId) {
        CaseDelivery delivery = caseDeliveryMapper.selectById(deliveryId);
        if (delivery == null || delivery.getInpatientId() == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Inpatient inpatient = inpatientMapper.selectOne(new LambdaQueryWrapper<Inpatient>()
                .eq(Inpatient::getId, delivery.getInpatientId())
                .eq(Inpatient::getUserId, userId));
        if (inpatient == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return toResponse(delivery, inpatient);
    }

    private Inpatient requireOwned(Long userId, Long inpatientId) {
        Inpatient inpatient = inpatientMapper.selectOne(new LambdaQueryWrapper<Inpatient>()
                .eq(Inpatient::getId, inpatientId)
                .eq(Inpatient::getUserId, userId));
        if (inpatient == null) {
            throw new BizException(ErrorCode.INPATIENT_NOT_FOUND);
        }
        return inpatient;
    }

    private CaseDeliveryResponse toResponse(CaseDelivery delivery, Inpatient inpatient) {
        CaseDeliveryResponse response = new CaseDeliveryResponse();
        response.setId(delivery.getId());
        response.setInpatientId(delivery.getInpatientId());
        response.setInpatientName(inpatient == null ? null : inpatient.getName());
        response.setInpatientNo(inpatient == null ? null : inpatient.getInpatientNo());
        response.setRecipientName(delivery.getRecipientName());
        response.setAddress(delivery.getAddress());
        response.setStatus(delivery.getStatus());
        response.setTrackingNo(delivery.getTrackingNo());
        response.setCreatedAt(delivery.getCreatedAt());
        return response;
    }
}
