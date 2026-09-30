package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminCatalogOptionsResponse;
import com.hospital.dto.AdminDepartmentResponse;
import com.hospital.dto.AdminDoctorResponse;
import com.hospital.dto.DepartmentSaveRequest;
import com.hospital.dto.DoctorSaveRequest;
import com.hospital.entity.Appointment;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Schedule;
import com.hospital.entity.Title;
import com.hospital.exception.BizException;
import com.hospital.mapper.AppointmentMapper;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.mapper.TitleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端科室与医生的写入（T27 卡片 736–737 行 / PRD 4.5.1–4.5.2 的 396–404 行，J59 判据）。
 *
 * <h2>列表两个都不加筛选参数</h2>
 * PRD 397 行是「医生列表 — 展示所有医生信息」、402 行是「科室列表 — 展示所有科室」，
 * 都只有"展示所有"。这与 T26 五个费用列表同一口径：规格里会筛的时候是明说的
 * （对照 PRD 347 行那句「支持按日期/科室/医生/状态筛选」），没写就不给补筛子。
 *
 * <h2>删除是软删，且两处守卫</h2>
 * {@code department}/{@code doctor} 都带 {@code deleted} 列（V1:66、V1:94）并 {@code extends BaseEntity}，
 * 所以 {@code deleteById} 走 {@code @TableLogic} 的逻辑删——历史单据的主体不能从库里消失。
 * 但"能软删"不等于"随便删"：
 * <ul>
 *   <li>科室下面还有活医生 → {@code 2008}（删了科室，医生的 {@code department_id}（V1:87）
 *       就指着一个不存在的科室，而它 NOT NULL，连"改到别处"都做不了）；</li>
 *   <li>医生还有<b>今天及以后</b>的排班，或还有<b>未取消/未就诊</b>的预约 → {@code 2009}。
 *        past 的班与 COMPLETED 的预约是历史，读侧靠
 *       {@link DoctorMapper#selectByIdsIncludingDeleted} 照样能显出名字，不该挡住归档；
 *       而"患者还约在一个已经被删的人名下"是任何规格都没有授权发生的事——
 *       卡片 736 行只写了「编辑/删除医生信息」，没有一个字交代删掉之后别人的号怎么办。</li>
 * </ul>
 * 守卫的口径与 T11 的 2007（排班有预约就拒绝取消）完全同族，包括"拒绝并告诉管理员先处理什么"。
 *
 * <h2>为什么不做"删科室时把医生一起软删"</h2>
 * 那是级联删除，规格从没写过；一次点击悄悄删掉五个医生和他们的排班，
 * 是 T25 停诊那种"必须逐条列明影响了什么"的反面——停诊好歹回包给了三个数字，
 * 而这里连一个数字都没有的来源。所以拒绝，让人先去处理。
 */
@Service
public class AdminCatalogCommandService {

    /** appointment.status 里"还活着"的两个取值（V1:124 的四个取值减去 CANCELLED 与 COMPLETED）。 */
    private static final List<String> LIVE_APPOINTMENT_STATUSES = List.of("PENDING_PAYMENT", "CONFIRMED");

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final TitleMapper titleMapper;
    private final ScheduleMapper scheduleMapper;
    private final AppointmentMapper appointmentMapper;

    public AdminCatalogCommandService(DepartmentMapper departmentMapper,
                                      DoctorMapper doctorMapper,
                                      TitleMapper titleMapper,
                                      ScheduleMapper scheduleMapper,
                                      AppointmentMapper appointmentMapper) {
        this.departmentMapper = departmentMapper;
        this.doctorMapper = doctorMapper;
        this.titleMapper = titleMapper;
        this.scheduleMapper = scheduleMapper;
        this.appointmentMapper = appointmentMapper;
    }

    // ============================================================
    // 科室
    // ============================================================

    public List<AdminDepartmentResponse> listDepartments() {
        List<Department> rows = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getSortOrder)
                        .orderByAsc(Department::getId));
        List<AdminDepartmentResponse> result = new ArrayList<>();
        for (Department row : rows) {
            result.add(toDepartmentResponse(row));
        }
        return result;
    }

    public AdminDepartmentResponse departmentDetail(Long id) {
        return toDepartmentResponse(requireDepartment(id));
    }

    @AuditLog(action = "CREATE_DEPARTMENT", targetType = "department")
    @Transactional
    public AdminDepartmentResponse createDepartment(DepartmentSaveRequest request) {
        Department department = new Department();
        department.setName(request.getName().trim());
        department.setIntro(blankToNull(request.getIntro()));
        department.setLocation(blankToNull(request.getLocation()));
        // sortOrder 不由本卡写入：规格从没要求给科室排序（PRD 403 行只给名称/简介/位置）
        departmentMapper.insert(department);
        return toDepartmentResponse(departmentMapper.selectById(department.getId()));
    }

    @AuditLog(action = "UPDATE_DEPARTMENT", targetType = "department")
    @Transactional
    public AdminDepartmentResponse updateDepartment(@AuditTarget Long id, DepartmentSaveRequest request) {
        Department department = requireDepartment(id);
        // 用 LambdaUpdateWrapper 显式 SET，而不是 updateById：
        // MP 的 updateById 默认跳过 null 字段，"把简介抹掉"这种编辑会静默不生效，
        // 患者端 DepartmentController（T10）于是还显示那段删掉的话。
        departmentMapper.update(null, new LambdaUpdateWrapper<Department>()
                .eq(Department::getId, id)
                .set(Department::getName, request.getName().trim())
                .set(Department::getIntro, blankToNull(request.getIntro()))
                .set(Department::getLocation, blankToNull(request.getLocation())));
        return toDepartmentResponse(departmentMapper.selectById(id));
    }

    @AuditLog(action = "DELETE_DEPARTMENT", targetType = "department")
    @Transactional
    public void deleteDepartment(@AuditTarget Long id) {
        requireDepartment(id);
        long doctors = doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getDepartmentId, id));
        if (doctors > 0) {
            throw new BizException(ErrorCode.DEPARTMENT_HAS_DOCTORS);
        }
        departmentMapper.deleteById(id);
    }

    // ============================================================
    // 医生
    // ============================================================

    public List<AdminDoctorResponse> listDoctors() {
        List<Doctor> rows = doctorMapper.selectList(
                new LambdaQueryWrapper<Doctor>().orderByAsc(Doctor::getDepartmentId)
                        .orderByAsc(Doctor::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> departmentNames = new HashMap<>();
        for (Department department : departmentMapper.selectByIdsIncludingDeleted(
                departmentIdsOf(rows))) {
            departmentNames.put(department.getId(), department.getName());
        }
        Map<Long, String> titleNames = titleNames();
        List<AdminDoctorResponse> result = new ArrayList<>();
        for (Doctor row : rows) {
            result.add(toDoctorResponse(row, departmentNames, titleNames));
        }
        return result;
    }

    public AdminDoctorResponse doctorDetail(Long id) {
        Doctor doctor = requireDoctor(id);
        Map<Long, String> departmentNames = new HashMap<>();
        if (doctor.getDepartmentId() != null) {
            for (Department department : departmentMapper.selectByIdsIncludingDeleted(
                    List.of(doctor.getDepartmentId()))) {
                departmentNames.put(department.getId(), department.getName());
            }
        }
        Map<Long, String> titleNames = titleNames();
        return toDoctorResponse(doctor, departmentNames, titleNames);
    }

    @AuditLog(action = "CREATE_DOCTOR", targetType = "doctor")
    @Transactional
    public AdminDoctorResponse createDoctor(DoctorSaveRequest request) {
        requireDepartment(request.getDepartmentId());
        requireTitleWhenPresent(request.getTitleId());

        Doctor doctor = new Doctor();
        doctor.setName(request.getName().trim());
        doctor.setDepartmentId(request.getDepartmentId());
        doctor.setTitleId(request.getTitleId());
        doctor.setIntro(blankToNull(request.getIntro()));
        doctor.setSpecialty(blankToNull(request.getSpecialty()));
        // avatar 不赋值：见 DoctorSaveRequest 类注释（没有上传通道，不收也不回显）
        doctorMapper.insert(doctor);
        return doctorDetail(doctor.getId());
    }

    @AuditLog(action = "UPDATE_DOCTOR", targetType = "doctor")
    @Transactional
    public AdminDoctorResponse updateDoctor(@AuditTarget Long id, DoctorSaveRequest request) {
        requireDoctor(id);
        requireDepartment(request.getDepartmentId());
        requireTitleWhenPresent(request.getTitleId());

        doctorMapper.update(null, new LambdaUpdateWrapper<Doctor>()
                .eq(Doctor::getId, id)
                .set(Doctor::getName, request.getName().trim())
                .set(Doctor::getDepartmentId, request.getDepartmentId())
                .set(Doctor::getTitleId, request.getTitleId())
                .set(Doctor::getIntro, blankToNull(request.getIntro()))
                .set(Doctor::getSpecialty, blankToNull(request.getSpecialty())));
        // avatar 不在 SET 里：本卡没有上传通道，所以既不清也不写（见 DoctorSaveRequest 类注释）
        return doctorDetail(id);
    }

    @AuditLog(action = "DELETE_DOCTOR", targetType = "doctor")
    @Transactional
    public void deleteDoctor(@AuditTarget Long id) {
        requireDoctor(id);

        long upcomingSchedules = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, id)
                .ge(Schedule::getDate, LocalDate.now()));
        long liveAppointments = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getDoctorId, id)
                .in(Appointment::getStatus, LIVE_APPOINTMENT_STATUSES));
        if (upcomingSchedules > 0 || liveAppointments > 0) {
            throw new BizException(ErrorCode.DOCTOR_HAS_ACTIVE_SCHEDULES);
        }
        doctorMapper.deleteById(id);
    }

    // ============================================================
    // 表单纯数据源
    // ============================================================

    /**
     * 职称下拉。职称本身的管理在 T28（卡片 764 行「职称管理：CRUD」），
     * 本卡只读它——医生表单需要它，而"需要一份选项"不等于"要一个 CRUD 页"。
     */
    public List<Title> listTitles() {
        return titleMapper.selectList(new LambdaQueryWrapper<Title>().orderByAsc(Title::getSortOrder));
    }

    /**
     * 医生表单的两份选项。这里刻意用<b>默认读法</b>（只回活科室、活职称）：
     * 历史单据的科室名要靠 {@code selectByIdsIncludingDeleted} 才显形，
     * 但"把一个已删科室选成新医生的归属"不是管理员该做到的事，两个场景两张读法。
     */
    public AdminCatalogOptionsResponse options() {
        AdminCatalogOptionsResponse response = new AdminCatalogOptionsResponse();
        response.setDepartments(departmentMapper.selectList(
                        new LambdaQueryWrapper<Department>().orderByAsc(Department::getSortOrder)
                                .orderByAsc(Department::getId)).stream()
                .map(row -> new AdminCatalogOptionsResponse.DepartmentOption(row.getId(), row.getName()))
                .toList());
        response.setTitles(listTitles().stream()
                .map(row -> new AdminCatalogOptionsResponse.TitleOption(
                        row.getId(), row.getName(), row.getSortOrder()))
                .toList());
        return response;
    }

    // ============================================================
    // 内部
    // ============================================================

    private Department requireDepartment(Long id) {
        Department department = id == null ? null : departmentMapper.selectById(id);
        if (department == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return department;
    }

    private Doctor requireDoctor(Long id) {
        Doctor doctor = id == null ? null : doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return doctor;
    }

    /**
     * 职称选填，但填了就必须存在：{@code title_id} 是 DEFAULT NULL（V1:88），
     * seed 里第 4、5 位医生就是没职称的形状，所以不能要求必填；
     * 但把一个不存在的 id 存进去，列表那一栏就只会显示"—"，管理员看不出自己填错了。
     */
    private void requireTitleWhenPresent(Long titleId) {
        if (titleId == null) {
            return;
        }
        if (titleMapper.selectById(titleId) == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
    }

    private Set<Long> departmentIdsOf(List<Doctor> rows) {
        Set<Long> ids = new HashSet<>();
        for (Doctor row : rows) {
            if (row.getDepartmentId() != null) {
                ids.add(row.getDepartmentId());
            }
        }
        return ids;
    }

    private Map<Long, String> titleNames() {
        Map<Long, String> names = new HashMap<>();
        for (Title title : titleMapper.selectList(null)) {
            names.put(title.getId(), title.getName());
        }
        return names;
    }

    private AdminDepartmentResponse toDepartmentResponse(Department row) {
        AdminDepartmentResponse response = new AdminDepartmentResponse();
        response.setId(row.getId());
        response.setName(row.getName());
        response.setIntro(row.getIntro());
        response.setLocation(row.getLocation());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    private AdminDoctorResponse toDoctorResponse(Doctor row, Map<Long, String> departmentNames,
                                                 Map<Long, String> titleNames) {
        AdminDoctorResponse response = new AdminDoctorResponse();
        response.setId(row.getId());
        response.setName(row.getName());
        response.setDepartmentId(row.getDepartmentId());
        response.setDepartmentName(departmentNames.get(row.getDepartmentId()));
        response.setTitleId(row.getTitleId());
        response.setTitleName(row.getTitleId() == null ? null : titleNames.get(row.getTitleId()));
        response.setIntro(row.getIntro());
        response.setSpecialty(row.getSpecialty());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
