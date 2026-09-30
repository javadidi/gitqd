package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminAppointmentResponse;
import com.hospital.dto.AdminFilterOptionsResponse;
import com.hospital.entity.Appointment;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Patient;
import com.hospital.entity.RefundRecord;
import com.hospital.entity.Schedule;
import com.hospital.exception.BizException;
import com.hospital.mapper.AppointmentMapper;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.RefundRecordMapper;
import com.hospital.mapper.ScheduleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端预约挂号查询（T25 卡片 697–698 行 / PRD 347–348 行）。纯读，不套事务（T10/T13 同一条理由）。
 *
 * <h2>四个筛选参数就是 PRD 347 行那四个，一个不多</h2>
 * 「支持按<b>日期/科室/医生/状态</b>筛选」——关键词搜索、分页、时段筛选规格都没写，
 * 分页与 T17/T18/T19/T21/T22/T23 一起留给 T28（{@code DataTable} 自己有前端翻页，
 * URL 上的 {@code ?page=} 由组件负责，附录 B 第 808 条因此已经满足）。
 *
 * <h2>日期与科室两个筛选为什么要绕两张表</h2>
 * {@code appointment} 没有就诊日期列（V1:118-135 的 {@code appointment_time} 是下单那一刻），
 * 就诊那天在 {@code schedule.date}（V1:104）；科室也不在预约上，要经
 * {@code doctor.department_id}（V1:90）。所以：日期 → 先查该日期区间的排班 id 集合，
 * 科室 → 先查该科室的医生 id 集合，再各自 {@code IN}。
 * <b>这不是偷懒的写法，是这套 schema 唯一的正确写法</b>：
 * 直接在 appointment 上按 appointment_time 筛"日期"会筛出"哪天下的单"，
 * 而管理员要的是"哪天来看诊"——两者在隔天预约时完全不同。
 *
 * <h2>没有归属跳</h2>
 * 患者侧的每一次查询都要经 {@code patient.user_id} 认一次主（T13 那条老规矩）；
 * 这里是后台，看全院预约本来就是需求。安全边界由 {@code /admin/**} 的角色规则与
 * 各写端点上的 {@code @RequireCap} 承担，不由这个类负责。
 */
@Service
public class AdminAppointmentQueryService {

    private final AppointmentMapper appointmentMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleMapper scheduleMapper;
    private final RefundRecordMapper refundRecordMapper;

    public AdminAppointmentQueryService(AppointmentMapper appointmentMapper,
                                        PatientMapper patientMapper,
                                        DoctorMapper doctorMapper,
                                        DepartmentMapper departmentMapper,
                                        ScheduleMapper scheduleMapper,
                                        RefundRecordMapper refundRecordMapper) {
        this.appointmentMapper = appointmentMapper;
        this.patientMapper = patientMapper;
        this.doctorMapper = doctorMapper;
        this.departmentMapper = departmentMapper;
        this.scheduleMapper = scheduleMapper;
        this.refundRecordMapper = refundRecordMapper;
    }

    public List<AdminAppointmentResponse> list(LocalDate dateFrom, LocalDate dateTo,
                                               Long departmentId, Long doctorId, String status) {
        LambdaQueryWrapper<Appointment> query = new LambdaQueryWrapper<>();

        if (dateFrom != null || dateTo != null) {
            // 含已停诊的班：被停那天的预约是管理员按日期排查时最该看见的一批
            Set<Long> scheduleIds = new HashSet<>(
                    scheduleMapper.selectIdsByDateRange(dateFrom, dateTo));
            if (scheduleIds.isEmpty()) {
                return List.of();
            }
            query.in(Appointment::getScheduleId, scheduleIds);
        }
        if (departmentId != null) {
            // 含已删医生：他们在这同一个科室看过的病人，管理员按科室筛时必须一起出来
            Set<Long> doctorIds = new HashSet<>(
                    doctorMapper.selectIdsByDepartmentIncludingDeleted(departmentId));
            if (doctorIds.isEmpty()) {
                return List.of();
            }
            query.in(Appointment::getDoctorId, doctorIds);
        }
        if (doctorId != null) {
            query.eq(Appointment::getDoctorId, doctorId);
        }
        if (status != null && !status.isBlank()) {
            query.eq(Appointment::getStatus, status.trim());
        }
        query.orderByDesc(Appointment::getId);

        List<Appointment> rows = appointmentMapper.selectList(query);
        if (rows.isEmpty()) {
            return List.of();
        }
        Names names = loadNames(rows.stream().map(Appointment::getPatientId).toList(),
                rows.stream().map(Appointment::getDoctorId).toList(),
                rows.stream().map(Appointment::getScheduleId).toList());
        List<AdminAppointmentResponse> result = new ArrayList<>();
        for (Appointment row : rows) {
            result.add(toResponse(row, names, false));
        }
        return result;
    }

    /** 挂号详情（PRD 348 行）。id 不存在 → 与患者端同码，后台也不该分辨"没有"与"别人的"。 */
    public AdminAppointmentResponse detail(Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        Names names = loadNames(List.of(appointment.getPatientId()),
                List.of(appointment.getDoctorId()), List.of(appointment.getScheduleId()));
        return toResponse(appointment, names, true);
    }

    /**
     * 两个下拉的选项（科室 / 医生）。只回 id 与名字，医生多带一个 departmentId
     * 好让前端在选了科室时就地过滤医生列表——完整的医生管理在 T27，这里不是它的替身。
     */
    public AdminFilterOptionsResponse filters() {
        List<Department> departments = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getSortOrder));
        List<Doctor> doctors = doctorMapper.selectList(
                new LambdaQueryWrapper<Doctor>().orderByAsc(Doctor::getDepartmentId)
                        .orderByAsc(Doctor::getId));
        AdminFilterOptionsResponse response = new AdminFilterOptionsResponse();
        response.setDepartments(departments.stream()
                .map(item -> new AdminFilterOptionsResponse.Option(item.getId(), item.getName()))
                .toList());
        response.setDoctors(doctors.stream()
                .map(item -> new AdminFilterOptionsResponse.Option(
                        item.getId(), item.getName(), item.getDepartmentId()))
                .toList());
        return response;
    }

    private AdminAppointmentResponse toResponse(Appointment row, Names names, boolean withRefund) {
        AdminAppointmentResponse response = new AdminAppointmentResponse();
        Schedule schedule = row.getScheduleId() == null ? null : names.schedules.get(row.getScheduleId());
        Doctor doctor = row.getDoctorId() == null ? null : names.doctors.get(row.getDoctorId());
        Patient patient = row.getPatientId() == null ? null : names.patients.get(row.getPatientId());

        response.setId(row.getId());
        response.setOrderNo(row.getOrderNo());
        response.setStatus(row.getStatus());
        response.setPatientId(row.getPatientId());
        response.setPatientName(patient == null ? null : patient.getName());
        response.setCardNo(patient == null ? null : patient.getCardNo());
        response.setDoctorName(doctor == null ? null : doctor.getName());
        response.setDepartmentName(doctor == null ? null : names.departments.get(doctor.getDepartmentId()));
        response.setAppointmentDate(schedule == null ? null : schedule.getDate());
        response.setTimeSlot(schedule == null ? null : schedule.getTimeSlot());
        response.setFeeFen(row.getFeeFen());
        response.setCreatedAt(row.getCreatedAt());

        if (withRefund) {
            // 退款单按 related_id 反查：一张预约最多挂一张（T13 只在 CONFIRMED 退号时挂）
            List<RefundRecord> refunds = refundRecordMapper.selectList(new LambdaQueryWrapper<RefundRecord>()
                    .eq(RefundRecord::getRelatedType, RefundTicketService.RELATED_TYPE_APPOINTMENT)
                    .eq(RefundRecord::getRelatedId, row.getId())
                    .orderByDesc(RefundRecord::getId));
            if (!refunds.isEmpty()) {
                RefundRecord refund = refunds.get(0);
                response.setRefundNo(refund.getOrderNo());
                response.setRefundFen(refund.getAmountFen());
                response.setRefundStatus(refund.getStatus());
            }
        }
        return response;
    }

    /** 一次批量取齐三张关联表的名字，不在循环里查库（T10 的 doctorNamesOf 同一条纪律）。 */
    private Names loadNames(Collection<Long> patientIds, Collection<Long> doctorIds,
                            Collection<Long> scheduleIds) {
        Names names = new Names();
        if (!patientIds.isEmpty()) {
            for (Patient patient : patientMapper.selectBatchIds(new HashSet<>(patientIds))) {
                names.patients.put(patient.getId(), patient);
            }
        }
        if (!doctorIds.isEmpty()) {
            // T27 起医生与科室都能被后台删（卡片 736/737 行的「删除」），
            // 而 appointment.doctor_id 是 NOT NULL——历史预约必须仍然说得出是谁、哪个科室。
            // 与下面 schedule 那条同一次教训（T25 的"— —"缺陷），这次在建删除入口的当卡一起改。
            List<Doctor> doctors = doctorMapper.selectByIdsIncludingDeleted(new HashSet<>(doctorIds));
            Set<Long> departmentIds = new HashSet<>();
            for (Doctor doctor : doctors) {
                names.doctors.put(doctor.getId(), doctor);
                if (doctor.getDepartmentId() != null) {
                    departmentIds.add(doctor.getDepartmentId());
                }
            }
            if (!departmentIds.isEmpty()) {
                for (Department department : departmentMapper.selectByIdsIncludingDeleted(departmentIds)) {
                    names.departments.put(department.getId(), department.getName());
                }
            }
        }
        if (!scheduleIds.isEmpty()) {
            // 含软删行：停诊撤掉的班也是这些预约的就诊日期与时段的唯一来源
            for (Schedule schedule : scheduleMapper.selectByIdsIncludingDeleted(
                    new HashSet<>(scheduleIds))) {
                names.schedules.put(schedule.getId(), schedule);
            }
        }
        return names;
    }

    private static final class Names {
        private final Map<Long, Patient> patients = new HashMap<>();
        private final Map<Long, Doctor> doctors = new HashMap<>();
        private final Map<Long, String> departments = new HashMap<>();
        private final Map<Long, Schedule> schedules = new HashMap<>();
    }
}
