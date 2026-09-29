package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AppointmentSummaryResponse;
import com.hospital.entity.Appointment;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Patient;
import com.hospital.entity.Schedule;
import com.hospital.exception.BizException;
import com.hospital.mapper.AppointmentMapper;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.ScheduleMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 预约记录查询（T13 卡片 475–476 行：预约记录列表 / 预约详情）。
 *
 * <p>单独一个类而不是塞进 {@link AppointmentService}：那边全是写路径（挂号、退号，都带事务与审计），
 * 这里是纯读，混在一起会让人分不清"哪个方法在事务里"。与 T10 的 {@code CatalogService}
 * 同一个分工（那边也是纯读、<b>故意不加 {@code @Transactional}</b>——只读查询套事务只是白占连接）。
 *
 * <p><b>归属是这张卡最容易漏的安全点</b>：{@code appointment} 表<b>没有 user_id 列</b>（V1:118-135），
 * 所以"这条预约是不是你的"必须经 {@code patient.user_id} 跳一次。列表用
 * {@code patient_id IN (我的就诊人)} 一次性收口；详情用双条件定位，查不到与不是你的同回 2004。
 * 少了这一跳，改一个 id 就能看见别人的就诊人姓名、医生和时间。
 */
@Service
public class AppointmentQueryService {

    private final AppointmentMapper appointmentMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleMapper scheduleMapper;

    public AppointmentQueryService(AppointmentMapper appointmentMapper,
                                   PatientMapper patientMapper,
                                   DoctorMapper doctorMapper,
                                   DepartmentMapper departmentMapper,
                                   ScheduleMapper scheduleMapper) {
        this.appointmentMapper = appointmentMapper;
        this.patientMapper = patientMapper;
        this.doctorMapper = doctorMapper;
        this.departmentMapper = departmentMapper;
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * 本人全部预约记录，按预约时间倒序（最近发生的排前面——患者进这个页就是要找刚约的那条）。
     *
     * <p>{@code status} 选填：传了就只回那一类（前端三个 tab 各自拉一次，
     * 也支持"全部"不传）。不传就是全量，分组规则留在前端，理由见
     * {@link AppointmentSummaryResponse} 的类注释。
     */
    public List<AppointmentSummaryResponse> list(Long userId, String status) {
        List<Long> myPatientIds = myPatientIds(userId);
        if (myPatientIds.isEmpty()) {
            // 一个就诊人也没有 → 不可能有预约。直接回空列表，省一次注定为空的查询，
            // 也避免把 IN () 空集合交给 MyBatis 拼出非法 SQL。
            return List.of();
        }
        List<Appointment> rows = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .in(Appointment::getPatientId, myPatientIds)
                .eq(status != null && !status.isBlank(), Appointment::getStatus, status)
                .orderByDesc(Appointment::getAppointmentTime)
                .orderByDesc(Appointment::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Names names = namesOf(rows);
        return rows.stream().map(row -> toResponse(row, names)).toList();
    }

    /** 预约详情（卡片 476 行）。归属不过就是 2004，与"根本没这条"同码。 */
    public AppointmentSummaryResponse detail(Long userId, Long appointmentId) {
        Appointment appointment = requireOwned(userId, appointmentId);
        Names names = namesOf(List.of(appointment));
        return toResponse(appointment, names);
    }

    private Appointment requireOwned(Long userId, Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null || !myPatientIds(userId).contains(appointment.getPatientId())) {
            throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        return appointment;
    }

    private List<Long> myPatientIds(Long userId) {
        return patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
    }

    /**
     * 一次批量把三个名字表捞回来。逐个查会变成 4N 次 SQL（患者、医生、科室、排班各一次），
     * 列表页 20 条就是 80 次——与 T10 {@code CatalogService.resolveTitleNames} 同一个理由。
     *
     * <p><b>包内可见是给 T16 {@code QueueService} 用的</b>：候诊查询也要给每条预约标出
     * 就诊人/科室/医生/时段，复制第二份同样的四次批量查就是给同一段逻辑两个出处——
     * 一改一漏之后，两个页面对同一个预约会显示不一样的医生名。
     */
    Names namesOf(List<Appointment> rows) {
        Names names = new Names();
        names.patients = patientMapper.selectBatchIds(distinct(rows.stream()
                        .map(Appointment::getPatientId).collect(Collectors.toList())))
                .stream().collect(Collectors.toMap(Patient::getId, Patient::getName));

        List<Doctor> doctors = doctorMapper.selectBatchIds(distinct(rows.stream()
                .map(Appointment::getDoctorId).collect(Collectors.toList())));
        names.doctors = doctors.stream().collect(Collectors.toMap(Doctor::getId, Doctor::getName));
        names.doctorDepartment = doctors.stream()
                .filter(d -> d.getDepartmentId() != null)
                .collect(Collectors.toMap(Doctor::getId, Doctor::getDepartmentId, (a, b) -> a));
        if (!names.doctorDepartment.isEmpty()) {
            names.departments = departmentMapper.selectBatchIds(names.doctorDepartment.values()).stream()
                    .collect(Collectors.toMap(Department::getId, Department::getName));
        }

        // 读排班不过滤软删行：T25 的停诊会留下"预约还在、班已撤"的历史，
        // 而时段只有 schedule 表里有——按默认的 selectBatchIds 那些记录会集体丢掉时段。
        Collection<Long> scheduleIds = distinct(rows.stream()
                .map(Appointment::getScheduleId).collect(Collectors.toList()));
        List<Schedule> schedules = scheduleIds.isEmpty() ? List.of()
                : scheduleMapper.selectByIdsIncludingDeleted(new java.util.HashSet<>(scheduleIds));
        names.slots = schedules.stream().collect(Collectors.toMap(Schedule::getId, Schedule::getTimeSlot));
        return names;
    }

    private static Collection<Long> distinct(List<Long> ids) {
        return ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
    }

    private AppointmentSummaryResponse toResponse(Appointment row, Names names) {
        AppointmentSummaryResponse item = new AppointmentSummaryResponse();
        item.setId(row.getId());
        item.setOrderNo(row.getOrderNo());
        item.setStatus(row.getStatus());
        item.setPatientName(names.patients.get(row.getPatientId()));
        item.setDoctorName(names.doctors.get(row.getDoctorId()));
        Long departmentId = names.doctorDepartment.get(row.getDoctorId());
        item.setDepartmentName(departmentId == null ? null : names.departments.get(departmentId));
        item.setTimeSlot(names.slots.get(row.getScheduleId()));
        item.setAppointmentTime(row.getAppointmentTime());
        item.setFeeFen(row.getFeeFen());
        return item;
    }

    /**
     * 一批查询的临时容器，只为把"四次批量查"的结果带回到映射那一步。
     * 包内可见（成员同）：{@code QueueService} 复用同一份解析结果，见 {@link #namesOf}。
     */
    static final class Names {
        Map<Long, String> patients = new HashMap<>();
        Map<Long, String> doctors = new HashMap<>();
        Map<Long, Long> doctorDepartment = new HashMap<>();
        Map<Long, String> departments = new HashMap<>();
        Map<Long, String> slots = new HashMap<>();
    }
}
