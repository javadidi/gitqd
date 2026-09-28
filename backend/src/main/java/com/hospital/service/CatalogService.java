package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.DepartmentDetailResponse;
import com.hospital.dto.DepartmentResponse;
import com.hospital.dto.DoctorDetailResponse;
import com.hospital.dto.DoctorSummaryResponse;
import com.hospital.dto.ScheduleItemResponse;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Schedule;
import com.hospital.entity.Title;
import com.hospital.enums.TimeSlot;
import com.hospital.exception.BizException;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.ScheduleMapper;
import com.hospital.mapper.TitleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 科室与医生目录查询（T10）。<b>本类全程只读，一行都不写。</b>
 *
 * <p><b>为什么科室和医生合在一个 service：</b>两者同属"患者浏览目录"这一个只读域，
 * 而且共用同两段逻辑——职称名解析（doctor.title_id → title.name）与排班聚合
 * （科室详情要每个医生的可约条数，医生详情要排班明细）。
 * 拆成 DepartmentService + DoctorService 会让其中一个必须依赖另一个，
 * 或者把这两段逻辑复制两份，都不如放一起。仓库里 service 按实体命名是既有习惯
 * （PatientService / InpatientService），本类是**有意**的例外，理由就是上面这条。
 *
 * <p><b>范围取舍（四路证据）：</b>
 * <ol>
 *   <li>卡片 412-415 行：科室列表（名称/简介/位置）、科室详情（该科室下所有医生）、
 *       医生列表（姓名/职称/擅长/头像）、医生详情（简介、<b>排班时间</b>）。</li>
 *   <li>卡片 423 行 DoD：「科室/医生查询通」——只有查询，没有增删改。</li>
 *   <li>PRD §9.1 行 609：「科室列表、医生列表、医生详情、排班查询」。</li>
 *   <li>PRD 77 行：科室详情页展示「该科室下所有医生<b>及排班信息</b>」——
 *       比卡片 413 行宽。按 T08-G 的教训（范围要读 DoD + §9.1 + §6.1 + PRD 正文，
 *       不能只读卡片动词清单），这里取宽的一侧，于是 {@link DoctorSummaryResponse}
 *       带了 {@code availableCount}。</li>
 * </ol>
 *
 * <p><b>「排班查询」为什么没有单开端点：</b>T11 卡片 430 行「排班列表：展示医生排班
 * （日期/时段/总号源/剩余号源）」正是 §9.1 那一项，而 T10 卡片 417 行红线明写
 * 「不做排班管理（T11）」。所以本卡只把排班作为**医生详情的内嵌只读列表**给出
 * （卡片 415 行明写「医生详情：展示医生简介、排班时间」），独立的排班端点留给 T11。
 *
 * <p><b>不加 {@code @Transactional}</b>：纯读、单表单次查询居多，没有跨表原子性需求。
 * CONVENTIONS 的 {@code @Transactional(rollbackFor=Exception.class)} 是针对写操作的规矩，
 * T09 的 {@code list}/{@code detail} 同样没加。
 *
 * <p><b>不写 audit_log</b>：PRD 485 行把审计限定在「管理后台操作」，患者端只读查询不记
 * （与 T07/T08/T09 一致）。
 *
 * <p><b>不需要归属校验</b>：科室/医生/排班是全院共享的目录数据，没有 user_id 列，
 * 不存在"别人的科室"。附录 B 第 806 条「小程序端新接口是否强制注入 userId 归属校验」
 * 在本卡的答案是 N/A——但仍挂在 {@code /user/**} 下要求患者 token，
 * 员工 token 打进来是 403（角色隔离照旧生效）。
 */
@Service
public class CatalogService {

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final TitleMapper titleMapper;
    private final ScheduleMapper scheduleMapper;
    /**
     * T12 加：排班条目上要多挂一个「挂号费」，因为 PRD 80 行要求患者<b>提交前</b>就在
     * 「确认预约信息」页看见费用，而这个页的数据全部来自医生详情里的这条排班列表。
     * 价格不重复实现，走 {@link AppointmentFeeService} 这个唯一出处。
     */
    private final AppointmentFeeService feeService;

    public CatalogService(DepartmentMapper departmentMapper,
                          DoctorMapper doctorMapper,
                          TitleMapper titleMapper,
                          ScheduleMapper scheduleMapper,
                          AppointmentFeeService feeService) {
        this.departmentMapper = departmentMapper;
        this.doctorMapper = doctorMapper;
        this.titleMapper = titleMapper;
        this.scheduleMapper = scheduleMapper;
        this.feeService = feeService;
    }

    // ============================================================
    // 科室
    // ============================================================

    /**
     * J22：科室列表。按 {@code sort_order} 升序、同序按 id 升序（seed 里两者一致，
     * 但 sort_order 允许重复，必须有第二排序键否则顺序不确定、测试会偶发红）。
     *
     * <p>{@code keyword} 选填，出处 PRD 76 行「展示医院所有科室列表，<b>支持搜索</b>」。
     * 只匹配科室**名称**：PRD 说的搜索是"找科室"，按简介/位置搜会返回一堆名字对不上的结果。
     * 空白串等同于不搜（返回全部）。
     *
     * <p>已知且接受：MyBatis-Plus 的 {@code like} 是参数绑定（无注入风险），
     * 但用户输入 {@code %} 会被当成通配符从而匹配全部。科室目录是公开数据、只读、
     * 无分页无金额，这个行为没有安全或正确性影响，故不做转义。
     */
    public List<DepartmentResponse> listDepartments(String keyword) {
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId);
        String trimmed = trimToNull(keyword);
        if (trimmed != null) {
            wrapper.like(Department::getName, trimmed);
        }
        return departmentMapper.selectList(wrapper).stream()
                .map(this::toDepartmentResponse)
                .toList();
    }

    /** 科室详情 = 科室字段 + 该科室下的医生（含每人的可约时段数，见类注释第 4 条） */
    public DepartmentDetailResponse departmentDetail(Long departmentId) {
        Department department = requireDepartment(departmentId);
        List<Doctor> doctors = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getDepartmentId, departmentId)
                // doctor 表没有 sort_order 列（V1:84-96），同科室内只能按 id 稳定排序
                .orderByAsc(Doctor::getId));

        DepartmentDetailResponse response = new DepartmentDetailResponse();
        response.setId(department.getId());
        response.setName(department.getName());
        response.setIntro(department.getIntro());
        response.setLocation(department.getLocation());
        response.setSortOrder(department.getSortOrder());
        response.setDoctors(toSummaries(doctors));
        return response;
    }

    // ============================================================
    // 医生
    // ============================================================

    /**
     * 医生列表。{@code departmentId} 选填：不传 = 全院医生，传了 = 该科室医生。
     *
     * <p>传了一个**不存在或已软删**的科室 id 时抛 5001，而不是静默返回空数组——
     * 空数组会让小程序显示"该科室暂无医生"，把"你给的科室根本不存在"
     * 伪装成一个合法的业务结果，排查时无从下手。
     */
    public List<DoctorSummaryResponse> listDoctors(Long departmentId) {
        LambdaQueryWrapper<Doctor> wrapper = new LambdaQueryWrapper<Doctor>()
                .orderByAsc(Doctor::getId);
        if (departmentId != null) {
            requireDepartment(departmentId);
            wrapper.eq(Doctor::getDepartmentId, departmentId);
        }
        return toSummaries(doctorMapper.selectList(wrapper));
    }

    /** J23 的下游：医生详情 = 医生字段 + 科室名 + 职称名 + 今天及以后的排班明细 */
    public DoctorDetailResponse doctorDetail(Long doctorId) {
        Doctor doctor = requireDoctor(doctorId);

        DoctorDetailResponse response = new DoctorDetailResponse();
        response.setId(doctor.getId());
        response.setName(doctor.getName());
        response.setDepartmentId(doctor.getDepartmentId());
        String titleName = resolveTitleNames(Set.of(doctor.getId())).get(doctor.getId());
        response.setTitleName(titleName);
        response.setIntro(doctor.getIntro());
        response.setSpecialty(doctor.getSpecialty());
        response.setAvatar(doctor.getAvatar());
        // 挂号费对这个医生是常数（按职称定），所以查一次摊给每一条排班，而不是每条排班各查一次。
        response.setSchedules(listFutureSchedules(doctorId,
                feeService.feeFenOfTitleName(titleName, doctorId)));

        // 科室可能已被软删（doctor.department_id 没有外键约束，V1:87 只是普通索引），
        // 此时 departmentName 为 null，配合 non_null 整个键消失，小程序用 '—' 兜。
        Department department = departmentMapper.selectById(doctor.getDepartmentId());
        if (department != null) {
            response.setDepartmentName(department.getName());
        }
        return response;
    }

    // ============================================================
    // 内部：职称解析、排班聚合、存在性校验
    // ============================================================

    /**
     * 批量把医生转成摘要。职称名与可约时段数都是**一次批量查**再在内存里分组：
     * 5 名医生若逐个查就是 10 次 SQL（N+1），seed 规模下不痛，但科室详情与医生列表
     * 共用这条路径，将来医生数上百就会变成每页上百次查询。
     */
    private List<DoctorSummaryResponse> toSummaries(List<Doctor> doctors) {
        if (doctors.isEmpty()) {
            return List.of();
        }
        Set<Long> doctorIds = doctors.stream().map(Doctor::getId).collect(Collectors.toSet());
        Map<Long, String> titleNames = resolveTitleNames(doctorIds);
        Map<Long, Integer> availableCounts = countAvailableSchedules(doctorIds);

        List<DoctorSummaryResponse> list = new ArrayList<>(doctors.size());
        for (Doctor doctor : doctors) {
            DoctorSummaryResponse item = new DoctorSummaryResponse();
            item.setId(doctor.getId());
            item.setName(doctor.getName());
            item.setDepartmentId(doctor.getDepartmentId());
            item.setTitleName(titleNames.get(doctor.getId()));
            item.setSpecialty(doctor.getSpecialty());
            item.setAvatar(doctor.getAvatar());
            item.setAvailableCount(availableCounts.getOrDefault(doctor.getId(), 0));
            list.add(item);
        }
        return list;
    }

    /**
     * doctor.title_id → title.name。title_id 可空（V1:88 DEFAULT NULL），
     * 也可能指向一个已软删的职称（同样没有外键约束），两种情况都让 titleName 为 null。
     */
    private Map<Long, String> resolveTitleNames(Set<Long> doctorIds) {
        List<Doctor> doctors = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .select(Doctor::getId, Doctor::getTitleId)
                .in(Doctor::getId, doctorIds));
        Set<Long> titleIds = new HashSet<>();
        for (Doctor doctor : doctors) {
            if (doctor.getTitleId() != null) {
                titleIds.add(doctor.getTitleId());
            }
        }
        if (titleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> titleNameById = titleMapper.selectList(new LambdaQueryWrapper<Title>()
                        .in(Title::getId, titleIds)).stream()
                .collect(Collectors.toMap(Title::getId, Title::getName, (a, b) -> a));

        Map<Long, String> result = new HashMap<>();
        for (Doctor doctor : doctors) {
            String name = doctor.getTitleId() == null ? null : titleNameById.get(doctor.getTitleId());
            if (name != null) {
                result.put(doctor.getId(), name);
            }
        }
        return result;
    }

    /**
     * 「可约时段数」= date &gt;= 今天 且 remaining_slots &gt; 0 且未软删的排班条数。
     *
     * <p>口径由本卡自定（PRD 77 行只说"及排班信息"，没规定形态），选择理由记在
     * {@link DoctorSummaryResponse} 的类注释里：列表页只给条数，明细留到医生详情页。
     *
     * <p>{@code @TableLogic} 自动补 {@code deleted = 0}，所以这里看得见的一定是活排班。
     */
    private Map<Long, Integer> countAvailableSchedules(Set<Long> doctorIds) {
        List<Schedule> rows = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .select(Schedule::getDoctorId)
                .in(Schedule::getDoctorId, doctorIds)
                .ge(Schedule::getDate, LocalDate.now())
                .gt(Schedule::getRemainingSlots, 0));
        return rows.stream().collect(Collectors.groupingBy(
                Schedule::getDoctorId, Collectors.summingInt(row -> 1)));
    }

    /**
     * 医生详情里的排班明细：<b>今天及以后</b>，按 date 升序、同日按 上午→下午→晚上。
     *
     * <p>与 {@link #countAvailableSchedules} 的口径<b>刻意不同</b>：这里不过滤
     * {@code remaining_slots > 0}，号已满的时段也要列出来。医生详情是"排班时间表"，
     * 把已满的时段藏起来会让患者以为该医生那天不出诊；
     * 是否可约由 {@code remainingSlots} 交给前端置灰判断。
     *
     * <p>过去的排班一律不返回：seed 造了 CURDATE()-7..+7 共 15 天 ×5 医生 ×2 时段 =150 行，
     * 不过滤会把一半废数据推给小程序，且对"我要挂号"没有任何意义。
     */
    private List<ScheduleItemResponse> listFutureSchedules(Long doctorId, long feeFen) {
        List<Schedule> rows = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctorId)
                .ge(Schedule::getDate, LocalDate.now())
                .orderByAsc(Schedule::getDate));
        return rows.stream()
                .sorted((a, b) -> {
                    int byDate = a.getDate().compareTo(b.getDate());
                    return byDate != 0 ? byDate : Integer.compare(TimeSlot.weight(a.getTimeSlot()), TimeSlot.weight(b.getTimeSlot()));
                })
                .map(row -> {
                    ScheduleItemResponse item = new ScheduleItemResponse();
                    item.setId(row.getId());
                    item.setDate(row.getDate());
                    item.setTimeSlot(row.getTimeSlot());
                    item.setTotalSlots(row.getTotalSlots());
                    item.setRemainingSlots(row.getRemainingSlots());
                    item.setFeeFen(feeFen);
                    return item;
                })
                .toList();
    }

    /**
     * 科室不存在（或已软删）→ {@code DATA_NOT_FOUND(5001)}。
     *
     * <p>复用 5001 而不是新造 {@code DEPARTMENT_NOT_FOUND}：PRD 没有错误码表
     * （全文 grep「错误码」无命中），错误码是仓库自定约定，1xxx 段是用户/就诊人/住院人、
     * 2xxx 段是排班/预约，科室与医生哪一段都不属于；5001「数据不存在」正是为这种
     * 无专属段的只读资源准备的通用码。T09 同样一个新码都没加。
     */
    private Department requireDepartment(Long departmentId) {
        Department department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return department;
    }

    private Doctor requireDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return doctor;
    }

    private DepartmentResponse toDepartmentResponse(Department department) {
        DepartmentResponse response = new DepartmentResponse();
        response.setId(department.getId());
        response.setName(department.getName());
        response.setIntro(department.getIntro());
        response.setLocation(department.getLocation());
        response.setSortOrder(department.getSortOrder());
        return response;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
