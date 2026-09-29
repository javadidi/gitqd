package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.common.ErrorCode;
import com.hospital.dto.NucleicCreateRequest;
import com.hospital.dto.NucleicListItemResponse;
import com.hospital.dto.NucleicReportResponse;
import com.hospital.entity.NucleicAppointment;
import com.hospital.entity.Patient;
import com.hospital.enums.SerialType;
import com.hospital.exception.BizException;
import com.hospital.mapper.NucleicAppointmentMapper;
import com.hospital.mapper.PatientMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 核酸检测（T21 卡片 616–630 行：选择就诊人 → 检测申请 → 确认预约信息 → 检测报告）。
 *
 * <h2>本卡写 {@code nucleic_appointment} 的行，但<strong>不写它的 {@code report}</strong></h2>
 * 与 T20 的 {@code follow_up} 一样，这张表在本卡之前没有任何生产者（逐字 grep 全仓 {@code nucleic}，
 * 只有 {@code V1:296-307} 建表、实体、空 mapper 三处），所以 J47「检测申请 → 记录创建」要求本卡当生产者。
 * 但<strong>只当一半的生产者</strong>：预约行由产品代码写，报告内容由院内检测侧回填，
 * 而首版没有那一条链路。为什么不模拟一份报告，四条证据记在
 * {@link NucleicReportResponse} 的类注释里，一句话版本是：
 * <b>发票可以模拟（系统自己出单据），医学结论不能模拟（它断言的是患者的身体）。</b>
 *
 * <h2>状态只有 {@code PENDING} 被写入</h2>
 * {@code V1:301} 列注释给了 {@code PENDING/COMPLETED}。COMPLETED 的语义是"检测做完、报告出了"，
 * 而本卡既不采样也不出报告，所以写它就是撒谎。读路径仍原样回 {@code status}
 * （与 T19 的 PENDING/ISSUED、T20 的三态同一处理：不假设写入侧只写过一种值）。
 *
 * <h2>三个方法对三个端点，其中列表是 PRD 撑出来的</h2>
 * PRD §9.1 第 616 行只给「创建检测预约、检测报告」两项，但 §3.7 第 203 行写着
 * 「核酸检测报告 —— <b>在个人中心查看</b>」、§3.11.7 第 304 行有「预约记录列表」、
 * §6.1 第 527 行把「核酸预约记录」列成个人中心的一个页面名。
 * 一个页面名的数据来源只能是端点，所以列表是第三个（与 T19 的待开具、T15 的待缴列表同一条判法）。
 * 而 §3.11.7 第 305 行的「预约详情」<b>不另开端点</b>：报告页本来就要显示
 * "这是哪一次检测"（单号/就诊人/日期/状态），再开一个详情端点就是同一份数据两个出处。
 *
 * <h2>归属一跳，越权与不存在同为 5001</h2>
 * {@code nucleic_appointment.patient_id → patient.user_id}，与 T13/T16/T17/T18/T19/T20 同一条口径。
 */
@Service
public class NucleicAppointmentService {

    /** V1:301 列注释给的初始态，也是本卡唯一写入的值。 */
    private static final String PENDING = "PENDING";

    private final NucleicAppointmentMapper nucleicMapper;
    private final PatientMapper patientMapper;
    private final SerialNumberService serialNumberService;

    public NucleicAppointmentService(NucleicAppointmentMapper nucleicMapper,
                                     PatientMapper patientMapper,
                                     SerialNumberService serialNumberService) {
        this.nucleicMapper = nucleicMapper;
        this.patientMapper = patientMapper;
        this.serialNumberService = serialNumberService;
    }

    /**
     * 创建检测预约（卡片 620–621 行 + PRD 201 行的"确认"那一步，J47）。
     *
     * <p>确认页与提交是同一个端点：卡片 621 行那句「确认预约信息：确认检测时间、地点等」
     * 是 UI 上的一次复核，不是一次新的写操作。§9.1 也只给了一个"创建"。
     *
     * <p>一个事务里两步：插行 + 写审计（附录 B 第 3 条，与 T12/T14/T15/T19/T20 同一写法，
     * 不用 {@code @Async}/{@code REQUIRES_NEW}/{@code afterCommit}）。
     */
    @AuditLog(action = "CREATE_NUCLEIC_APPOINTMENT", targetType = "nucleic_appointment")
    @Transactional
    public NucleicReportResponse create(Long userId, NucleicCreateRequest request) {
        Patient patient = patientMapper.selectById(request.getPatientId());
        // 不是你的就诊人、没这个就诊人、就诊人已软删 → 一律 5001，三种情况不区分。
        if (patient == null || !userId.equals(patient.getUserId())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }

        NucleicAppointment appointment = new NucleicAppointment();
        appointment.setOrderNo(serialNumberService.next(SerialType.HX));
        appointment.setPatientId(patient.getId());
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStatus(PENDING);
        // report 刻意不设值：见类注释第二节。留 NULL 就是"还没做检测"，
        // 而不是"做完了但内容空着"——这两件事在页面上必须能分开。
        nucleicMapper.insert(appointment);

        // 回读一次，让成功页/报告页看到的时间与库里一致（与 T19/T20 同一条做法）。
        return report(userId, appointment.getId());
    }

    /** 核酸预约记录列表（PRD 304 行 + 527 行）：本人全部预约，按创建时间倒序。 */
    public List<NucleicListItemResponse> list(Long userId) {
        List<Long> myPatientIds = myPatientIds(userId);
        if (myPatientIds.isEmpty()) {
            // 一个就诊人也没有 → 不可能有核酸预约。直接回空列表，也免得把 IN () 空集合交给 MyBatis。
            return List.of();
        }
        List<NucleicAppointment> rows = nucleicMapper.selectList(
                new LambdaQueryWrapper<NucleicAppointment>()
                        .in(NucleicAppointment::getPatientId, myPatientIds)
                        .orderByDesc(NucleicAppointment::getCreatedAt)
                        .orderByDesc(NucleicAppointment::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> patientNames = patientMapper.selectBatchIds(rows.stream()
                        .map(NucleicAppointment::getPatientId).filter(Objects::nonNull)
                        .distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Patient::getId, Patient::getName));

        return rows.stream().map(row -> {
            NucleicListItemResponse item = new NucleicListItemResponse();
            item.setAppointmentId(row.getId());
            item.setOrderNo(row.getOrderNo());
            item.setPatientName(patientNames.get(row.getPatientId()));
            item.setAppointmentDate(row.getAppointmentDate());
            item.setStatus(row.getStatus());
            return item;
        }).toList();
    }

    /**
     * 检测报告（卡片 622 行，J48「检测报告 → 内容正确」）。
     *
     * <p>越权、不存在、就诊人软删三种情况同为 5001。
     * 报告有没有出，看 {@code report} 是否为 null（NON_NULL 会让整个键消失），
     * 不看状态码——二期若出现"已采样待出结果"这种中间态，
     * 页面按 {@code report} 判断比按 {@code status} 枚举判断更结实。
     */
    public NucleicReportResponse report(Long userId, Long appointmentId) {
        NucleicAppointment appointment = nucleicMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Patient patient = patientMapper.selectById(appointment.getPatientId());
        if (patient == null || !userId.equals(patient.getUserId())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }

        NucleicReportResponse detail = new NucleicReportResponse();
        detail.setAppointmentId(appointment.getId());
        detail.setOrderNo(appointment.getOrderNo());
        detail.setPatientName(patient.getName());
        detail.setAppointmentDate(appointment.getAppointmentDate());
        detail.setStatus(appointment.getStatus());
        detail.setReport(appointment.getReport());
        return detail;
    }

    private List<Long> myPatientIds(Long userId) {
        return patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
    }
}
