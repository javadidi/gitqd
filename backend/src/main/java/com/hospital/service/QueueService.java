package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.dto.QueueStatusResponse;
import com.hospital.entity.Appointment;
import com.hospital.entity.Patient;
import com.hospital.entity.QueueStatus;
import com.hospital.mapper.AppointmentMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.QueueStatusMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 候诊查询（T16 卡片 526–539 行：候诊查询页 / 实时更新 / {@code <QueueProgress>} 组件）。
 *
 * <h2>纯读，且首版没有生产者</h2>
 * 与 T10 的 {@code CatalogService}、T13 的 {@code AppointmentQueryService} 同一个分工：
 * <b>故意不加 {@code @Transactional}</b>，只读查询套事务只是白占连接。
 * 本卡<b>不写</b> {@code queue_status}：全仓没有任何一张卡负责写它（任务卡只在 T16 提到候诊，
 * PRD 的 §4 后台章节 / §6.2 页面清单 / §9.2 接口清单里没有叫号管理），真实系统里由院内
 * 叫号系统写入（PRD 662 行）。所以这里只有读，写路径留给二期对接。
 *
 * <h2>骨架是"我的预约"，不是"队列记录"</h2>
 * {@code queue_status} 只有 {@code appointment_id} 和三个数字，它说不清这是谁的队、
 * 排的是哪个医生。患者手里的实体是一次预约，所以列表以预约为骨架去 LEFT 挂队列行：
 * 没进队列的预约照样出现（{@code queueStatus = null}），患者看得懂"还没开始叫号"，
 * 而不是误以为预约消失了。
 *
 * <h2>哪些预约进这个列表：口径与出处</h2>
 * {@code status IN (CONFIRMED, COMPLETED)} 且 {@code appointment_time >= 今天零点}。
 * <ul>
 *   <li>排除 {@code PENDING_PAYMENT}：卡片 453 行第 ⑤—⑧ 步写的是「⑤ 创建预约记录
 *       （PENDING_PAYMENT）… ⑧ 支付成功 → 预约状态 CONFIRMED」——没付钱的单<b>还没挂上号</b>，
 *       而 PRD 662 行的队列是「医院排队叫号系统」按已挂号者建的。另有一条反向佐证：
 *       卡片 331 行把「覆盖待支付/已确认/已完成/已取消」明确写在 <b>T13 预约记录</b>名下，
 *       T16 卡片 529 行只要求"展示当前排队人数、叫号进度"，两者范围不同是规格自己做的区分。
 *       <b>注意别反向引用</b>：卡片 453 行的 ⑤⑥ 说明 PENDING_PAYMENT 已经在扣号源了，
 *       所以"未支付"不是"什么都没发生"，它排除在叫号之外是<b>业务口径</b>，不是数据缺失；</li>
 *   <li>排除 {@code CANCELLED}：退了号还显示队，就是叫一个不存在的人；</li>
 *   <li>保留 {@code COMPLETED}：<b>这一条没有直接出处，是我的设计选择</b>。理由：
 *       {@code queue_status} 不会为已就诊的行删除，若按状态过滤掉，页面里那条预约会凭空消失，
 *       而同一时刻 T13 的预约记录页还查得到它——两页对同一条预约给出"有/没有"的矛盾读数，
 *       比"多显示一条已就诊"更伤。PRD 106 行「实时更新排队状态，防止过号」也支持保留：
 *       过号的人要能看见"已经叫过了"才知道要找分诊台；</li>
 *   <li>日期下界取今天零点：PRD 103 行的词是「<b>当前</b>候诊叫号状态」，历史预约的队早已无意义，
 *       而把它们混进来会让首屏第一条是一周前的旧队。</li>
 * </ul>
 *
 * <h2>归属：一跳都不能省</h2>
 * {@code queue_status} 与 {@code appointment} 都没有 {@code user_id}，所以"这条队是不是你的"
 * 必须经 {@code appointment.patient_id → patient.user_id} 跳两次。列表用
 * {@code patient_id IN (我的就诊人)} 一次收口（与 T13 同一条纪律），
 * 少了这一跳，改一个 appointment_id 就能看见别人排到几号。
 */
@Service
public class QueueService {

    private final AppointmentMapper appointmentMapper;
    private final PatientMapper patientMapper;
    private final QueueStatusMapper queueMapper;
    private final AppointmentQueryService appointmentQueryService;

    public QueueService(AppointmentMapper appointmentMapper,
                        PatientMapper patientMapper,
                        QueueStatusMapper queueMapper,
                        AppointmentQueryService appointmentQueryService) {
        this.appointmentMapper = appointmentMapper;
        this.patientMapper = patientMapper;
        this.queueMapper = queueMapper;
        this.appointmentQueryService = appointmentQueryService;
    }

    /**
     * 本人"当前"的候诊状态列表。按就诊时间<b>升序</b>（即将就诊的排最前）——
     * 与 T13 预约记录列表的倒序刻意相反：那边是翻历史，这边是"下一个该我了吗"。
     */
    public List<QueueStatusResponse> list(Long userId) {
        List<Long> myPatientIds = patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
        if (myPatientIds.isEmpty()) {
            // 一个就诊人也没有就不可能有队。直接回空列表，免得把 IN () 空集合交给 MyBatis。
            return List.of();
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<Appointment> rows = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .in(Appointment::getPatientId, myPatientIds)
                .in(Appointment::getStatus,
                        List.of(AppointmentService.CONFIRMED, AppointmentService.COMPLETED))
                .ge(Appointment::getAppointmentTime, todayStart)
                .orderByAsc(Appointment::getAppointmentTime)
                .orderByAsc(Appointment::getId));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> appointmentIds = rows.stream().map(Appointment::getId).toList();
        // 一次 in 捞全部队列行；uk_appointment_id（V1:196）保证一预约至多一行，
        // 所以 toMap 不需要合并函数——真出现两行就该炸在这里，而不是静默挑一条
        Map<Long, QueueStatus> queues = queueMapper.selectList(new LambdaQueryWrapper<QueueStatus>()
                        .in(QueueStatus::getAppointmentId, appointmentIds))
                .stream().collect(Collectors.toMap(QueueStatus::getAppointmentId, q -> q));

        AppointmentQueryService.Names names = appointmentQueryService.namesOf(rows);
        List<QueueStatusResponse> result = new ArrayList<>(rows.size());
        for (Appointment row : rows) {
            result.add(toResponse(row, names, queues.get(row.getId())));
        }
        return result;
    }

    private QueueStatusResponse toResponse(Appointment row, AppointmentQueryService.Names names,
                                           QueueStatus queue) {
        QueueStatusResponse item = new QueueStatusResponse();
        item.setAppointmentId(row.getId());
        item.setOrderNo(row.getOrderNo());
        item.setAppointmentStatus(row.getStatus());
        item.setAppointmentTime(row.getAppointmentTime());
        item.setPatientName(names.patients.get(row.getPatientId()));
        item.setDoctorName(names.doctors.get(row.getDoctorId()));
        Long departmentId = names.doctorDepartment.get(row.getDoctorId());
        item.setDepartmentName(departmentId == null ? null : names.departments.get(departmentId));
        item.setTimeSlot(names.slots.get(row.getScheduleId()));
        if (queue != null) {
            item.setQueueStatus(queue.getStatus());
            item.setCurrentNumber(queue.getCurrentNumber());
            item.setWaitingCount(queue.getWaitingCount());
            item.setQueueUpdatedAt(queue.getUpdatedAt());
        }
        return item;
    }
}
