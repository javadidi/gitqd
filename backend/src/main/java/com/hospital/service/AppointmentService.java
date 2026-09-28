package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditReason;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AppointmentCancelResponse;
import com.hospital.dto.AppointmentCreateRequest;
import com.hospital.dto.AppointmentResponse;
import com.hospital.entity.Appointment;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.Patient;
import com.hospital.entity.RefundRecord;
import com.hospital.entity.Schedule;
import com.hospital.enums.SerialType;
import com.hospital.enums.TimeSlot;
import com.hospital.exception.BizException;
import com.hospital.mapper.AppointmentMapper;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.RefundRecordMapper;
import com.hospital.mapper.ScheduleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约挂号（T12 A 段）：卡片 452 行明写「<b>一个 {@code @Transactional} 方法</b>」，
 * 把 453 行的 ①选择就诊人 → ②选科室/医生 → ③看排班/剩余号源 → ④确认信息 → ⑤建单（PENDING_PAYMENT）
 * → ⑥扣号源 → ⑦发起支付 → ⑨审计 全串在一次事务里。
 *
 * <p>①②③④ 里有三步是小程序的页面动作（PRD 75-80 行），后端在这一个方法里承担的是它们的<b>结果校验</b>：
 * 患者 id 要属于当前登录人、排班要还在且是未来的、这位患者在这个排班上还不能已经有单。
 * 页面能绕过顺序直接调本接口（小程序只是客户端），所以这几条一条都不能省——
 * 附录 B「权限判断是否只写在 UI」在预约上的同型问题。
 *
 * <p><b>⑧「支付成功 → CONFIRMED」不在本方法里</b>，那是 B 段独立回调的事（卡片 455-456 行）。
 * 本方法建出来的单一律是 {@code PENDING_PAYMENT}。
 *
 * <h2>号源为什么只能靠一条带条件的 UPDATE 来扣</h2>
 *
 * <p>见 {@link ScheduleMapper#occupySlot}。这里只补一句业务侧的因果：
 * 「先查 {@code remaining > 0} 再写 {@code remaining - 1}」在两个患者同时点同一个时段时必然超卖，
 * 而号源超卖是<b>医院现场才会发现</b>的错误（两个人拿着同一个时段的号去候诊），
 * 数据库层面没有第二道索引能拦住它——{@code uk_doctor_date_slot} 管的是排班唯一，
 * {@code uk_patient_schedule} 管的是同一人同一班不重复，<b>都不管"这个班一共放出去几个号"</b>。
 * 所以本方法里 {@code occupySlot} 返回 0 就是唯一可信的"没号了"判据。
 *
 * <h2>异常翻译成业务码的位置在 controller，不在这里</h2>
 *
 * <p>J30 的第二层（{@code uk_patient_schedule} 撞唯一索引）不在本方法里 catch：
 * 本方法是 {@code @Transactional} 的，事务内捕获 DB 异常会把事务标成 rollback-only，
 * 之后正常返回时 commit 抛 {@code UnexpectedRollbackException}，用户看到一个查不出原因的 500。
 * T07 {@code loginByWechat}、T08 {@code PatientService}、T11 {@code ScheduleService.create}
 * 都是靠"不套事务"绕开这条的，<b>本卡不能照抄</b>——卡片 452 行明确要求这一整个方法在一个事务里
 * （否则 J27 要的"支付异常时 appointment 和 schedule 一起回滚"根本无从谈起）。
 * 所以 {@code DuplicateKeyException} 原样外抛，由 {@code AppointmentController} 在事务边界外翻成 2005。
 *
 * <p>与之相对，{@code BizException}（号已满、重复预约的前置查、排班不存在）在这里直接抛是安全的：
 * 异常穿透事务导致回滚正是我们要的，且回滚把已经扣掉的号一并还原。
 *
 * <h2>审计：卡片 453 行第⑨步，切面在事务外层先写流水</h2>
 *
 * <p>PRD 485 行把审计范围写成「管理后台操作」，而本卡的操作人是患者、走的是 {@code /user/**}。
 * <b>两处冲突时按"卡片更具体"取宽</b>（同 T10 给医生列表补 {@code availableCount} 的判断方向），
 * 所以预约动作留审计流水。这要求 {@code AuditLogAspect} 认识患者主体——
 * 它原本只认 {@code LoginUser}，遇到患者主体会抛 401，已扩为同时认 {@code LoginPatient}
 * （操作人类型多出一个 {@code PATIENT}，{@code operator_id} 指向 {@code user.id} 而非 {@code admin.id}，
 * 详见 {@code OperatorType} 的注释）。这是改 T04 的地基，因此本卡的门禁会把 T04 的 J7/J8 一并回归。
 */
@Service
public class AppointmentService {

    /**
     * appointment.status 的四个取值见 V1:124 列注释。<b>全仓唯一出处</b>：
     * 同包的 {@code AppointmentPaymentService}（支付推进）与 {@code AppointmentQueryService}
     * 都从这里取，避免同一个状态码在两个类里各写一份字面量、改一处漏一处。
     */
    static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    static final String CONFIRMED = "CONFIRMED";
    static final String CANCELLED = "CANCELLED";
    static final String COMPLETED = "COMPLETED";
    /** appointment / refund_record.related_type 的取值见 V1:176 列注释。 */
    private static final String RELATED_TYPE_APPOINTMENT = "APPOINTMENT";
    /** 退款单初始状态：V1:179 的 PENDING/APPROVED/REJECTED/COMPLETED 之首。 */
    private static final String REFUND_PENDING = "PENDING";

    private final AppointmentMapper appointmentMapper;
    private final PatientMapper patientMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final AppointmentFeeService feeService;
    private final SerialNumberService serialNumberService;
    private final WechatPayService wechatPayService;
    private final RefundRecordMapper refundRecordMapper;

    public AppointmentService(AppointmentMapper appointmentMapper,
                              PatientMapper patientMapper,
                              ScheduleMapper scheduleMapper,
                              DoctorMapper doctorMapper,
                              DepartmentMapper departmentMapper,
                              AppointmentFeeService feeService,
                              SerialNumberService serialNumberService,
                              WechatPayService wechatPayService,
                              RefundRecordMapper refundRecordMapper) {
        this.appointmentMapper = appointmentMapper;
        this.patientMapper = patientMapper;
        this.scheduleMapper = scheduleMapper;
        this.doctorMapper = doctorMapper;
        this.departmentMapper = departmentMapper;
        this.feeService = feeService;
        this.serialNumberService = serialNumberService;
        this.wechatPayService = wechatPayService;
        this.refundRecordMapper = refundRecordMapper;
    }

    /**
     * 创建预约（J27/J29/J30 的共同主体）。
     *
     * <p>写序是「先扣号、再建单」而不是反过来，有两个理由：
     * ① 扣号是唯一性判据，扣不到就没必要留下半张单；
     * ② 先拿 {@code schedule} 的行锁，并发抢同一个时段时后到者阻塞在这把锁上而不是阻塞在
     * {@code appointment} 的唯一索引间隙上，等它继续时号已经被扣掉，直接拿到 0 行 → 干净地回 2003。
     * 反序（先 insert 再扣号）会让两个事务互相持有对方想要的锁，出现死锁的概率明显更高。
     */
    @AuditLog(action = "CREATE_APPOINTMENT", targetType = "appointment")
    @Transactional
    public AppointmentResponse create(Long userId, AppointmentCreateRequest request) {
        Patient patient = requireOwnedPatient(userId, request.getPatientId());          // ①
        Schedule schedule = requireBookableSchedule(request.getScheduleId());           // ③
        Doctor doctor = requireDoctor(schedule.getDoctorId());

        // J30 第一层：同一就诊人同一排班已有<b>有效</b>单 → 2005。
        // T13-C 起，已取消（CANCELLED）的那条<b>不算占位</b>：它会被复活（见下方分支），
        // 因为 uk_patient_schedule 不看 status，不复活就等于"退号即永久拉黑这个班"，
        // 而 PRD 86 行要的是"不得同时有两张有效预约"。
        Appointment existing = appointmentMapper.selectOne(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getPatientId, patient.getId())
                .eq(Appointment::getScheduleId, schedule.getId()));
        if (existing != null && !CANCELLED.equals(existing.getStatus())) {
            throw new BizException(ErrorCode.APPOINTMENT_DUPLICATE);
        }

        long feeFen = feeService.feeFenOfDoctor(schedule.getDoctorId());                // 费用只从服务端算
        LocalDateTime appointmentTime = LocalDateTime.of(
                schedule.getDate(), TimeSlot.startTimeOf(schedule.getTimeSlot()));

        if (scheduleMapper.occupySlot(schedule.getId()) == 0) {                         // ⑥
            throw new BizException(ErrorCode.SCHEDULE_NO_SLOTS);
        }

        String orderNo = serialNumberService.next(SerialType.YY);
        Appointment appointment;
        if (existing != null) {
            // 复活那条已取消的行。返回 0 只可能是并发下它刚被别的请求改走状态 → 按重复预约拒掉，
            // 此时上面扣的号随事务回滚一起还原。
            if (appointmentMapper.reviveCancelled(existing.getId(), orderNo, feeFen, appointmentTime) == 0) {
                throw new BizException(ErrorCode.APPOINTMENT_DUPLICATE);
            }
            appointment = appointmentMapper.selectById(existing.getId());
        } else {
            appointment = new Appointment();
            appointment.setOrderNo(orderNo);
            appointment.setPatientId(patient.getId());
            appointment.setDoctorId(schedule.getDoctorId());
            appointment.setScheduleId(schedule.getId());
            appointment.setStatus(PENDING_PAYMENT);                                      // ⑤
            appointment.setAppointmentTime(appointmentTime);
            appointment.setFeeFen(feeFen);
            // ② 的 doctor_id 取自排班而不是取自入参：见 AppointmentCreateRequest 的注释，
            // 这条记录上的医生永远与排班一致。
            appointmentMapper.insert(appointment);                                        // J30 第二层由索引兜，异常外抛
        }

        String prepayId = wechatPayService.prepay(appointment.getOrderNo(), feeFen);      // ⑦ 见类注释：真实通道落地时要挪到 afterCommit

        return toResponse(appointment, patient.getName(), doctor, prepayId);
    }

    /**
     * 退号（T13 卡片 477 行「取消预约 → 退还挂号费 → 恢复号源 → 审计」，J31/J32）。
     *
     * <h2>四步为什么必须在一个事务里</h2>
     * 少任何一步都会留下错账：只改状态不还号 = 患者名额白白损失；只还号不改状态 =
     * 一个名额被两个人占（原预约还在，别人又约进来）；改了状态也没还号但漏了退款单 =
     * 患者付过的钱在医院账上凭空消失。所以这四步要么全成，要么全不回滚不了——
     * 由 {@code @Transactional} 保证，中途任何一步抛异常都会把已做的部分一起还原。
     *
     * <h2>{@code PENDING_PAYMENT} 的单<b>不生成退款单</b>，这是本方法最容易写错的一处</h2>
     * 卡片 477 行写的是「退还挂号费」，但待支付的那张单<b>从来没有收到过钱</b>——
     * 给它建一条 {@code refund_record} 等于凭空造出一笔"应退给患者的钱"，
     * 而 {@code refund_record} 是财务单据（V1:172-183，无 deleted 列，只增不删），
     * 后台 T26 的费用管理与退款审核都会照单核算，假退款单会直接污染对账。
     * 所以只有 {@code CONFIRMED}（真付过钱，{@code payment_record} 里有 SUCCESS 流水）才挂退款单。
     * J31 用的是已支付的单，正好覆盖这条分支；待支付分支另有测试反向钉住。
     *
     * <h2>退款只到"挂单"为止</h2>
     * 卡片 479 行红线「退款需审核（二期做）」，所以本方法只写一条 {@code status=PENDING} 的退款单，
     * <b>不动 {@code payment_record}、不做任何真实出款</b>（真实微信支付本身也是附录 A 二期）。
     * 审核动作在后台（T25/T26 的退款审核页），那里才有 {@code reviewer_id} 可填。
     * 因此接口与 UI 的措辞必须是"退款申请已提交，等待审核"，不能写成"已退款"。
     */
    @AuditLog(action = "CANCEL_APPOINTMENT", targetType = "appointment")
    @Transactional
    public AppointmentCancelResponse cancel(Long userId, @AuditTarget Long appointmentId,
                                            @AuditReason String reason) {
        Appointment appointment = requireOwnedAppointment(userId, appointmentId);
        String statusBefore = appointment.getStatus();

        // J32：已就诊不可退号（卡片 479 行红线）。先在 Java 层判一次给准确文案，
        // SQL 层的 WHERE 里还有一道（cancelIfActive 的状态集合不含 COMPLETED），双保险。
        if (COMPLETED.equals(statusBefore)) {
            throw new BizException(ErrorCode.APPOINTMENT_STATUS_ERROR.getCode(), "已就诊的预约不可退号");
        }

        if (appointmentMapper.cancelIfActive(appointmentId) == 0) {
            // 并发下另一个请求已经取消过了。这里必须拒绝而不是静默成功，
            // 否则会往下再还一次号、再挂一张退款单。
            throw new BizException(ErrorCode.APPOINTMENT_STATUS_ERROR.getCode(), "该预约已取消，无需重复退号");
        }

        if (scheduleMapper.releaseSlot(appointment.getScheduleId()) == 0) {
            // 号源加不回去（排班已被取消，或数据已经异常到 remaining=total）。
            // 抛异常让上面那步取消一起回滚——宁可退号失败，也不能留下"取消了但名额没回来"的账。
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }

        AppointmentCancelResponse response = new AppointmentCancelResponse();
        response.setId(appointment.getId());
        response.setOrderNo(appointment.getOrderNo());
        response.setStatus("CANCELLED");
        response.setFeeFen(appointment.getFeeFen());

        if (CONFIRMED.equals(statusBefore)) {
            RefundRecord refund = new RefundRecord();
            refund.setOrderNo(serialNumberService.next(SerialType.TK));
            refund.setRelatedId(appointment.getId());
            refund.setRelatedType(RELATED_TYPE_APPOINTMENT);
            // 金额取预约账上的，不接受任何外部传入（T12 那条「支付金额禁篡改」在退款侧的同一半句）
            refund.setAmountFen(appointment.getFeeFen());
            refund.setStatus(REFUND_PENDING);
            refund.setReason(reason);
            // reviewer_id 留空：审核是二期的事，现在没有任何人审过这笔
            refundRecordMapper.insert(refund);
            response.setRefundNo(refund.getOrderNo());
            response.setRefundFen(refund.getAmountFen());
            response.setRefundStatus(refund.getStatus());
            response.setRefundRequired(true);
        } else {
            // 待支付：没收过钱，无款可退
            response.setRefundRequired(false);
        }
        return response;
    }

    /**
     * 归属校验。{@code appointment} 表<b>没有 user_id 列</b>（V1:118-135），
     * 所以"这张单是不是你的"必须经 {@code patient.user_id} 跳一次。
     * 查不到与不是你的同回 2004，不给枚举机会（与 T08/T09/T12 一致）。
     */
    private Appointment requireOwnedAppointment(Long userId, Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        Long owned = patientMapper.selectCount(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getId, appointment.getPatientId())
                .eq(Patient::getUserId, userId));
        if (owned == null || owned == 0) {
            throw new BizException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        return appointment;
    }

    /**
     * ①选择就诊人：只能选自己的。
     *
     * <p>别人的就诊人和不存在的就诊人同回 1003，不用 403（403 会确认"这条存在"），
     * 与 T08/T09 的 {@code requireOwned} 完全同构。这一步不能省：
     * 患者只要改一个 {@code patientId} 就能拿别人的名字去挂号。
     */
    private Patient requireOwnedPatient(Long userId, Long patientId) {
        Patient patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getId, patientId)
                .eq(Patient::getUserId, userId));
        if (patient == null) {
            throw new BizException(ErrorCode.PATIENT_NOT_FOUND);
        }
        return patient;
    }

    /**
     * ③看排班/剩余号源：排班要存在、没被取消，且<b>不能是过去的日期</b>。
     *
     * <p>{@code selectById} 因为 {@code @TableLogic} 自动带 {@code deleted = 0}，
     * 所以"T11 取消掉的排班"和"根本没这条排班"在这里都是 2001，语义正确：
     * 取消即停诊，患者侧不该知道区别。
     *
     * <p>过去日期同样回 2001 而不是给一个专门的"排班已过期"码，理由是<b>与患者端的读口径对齐</b>：
     * T10 的 {@code CatalogService} 给小程序的排班列表本来就只取 {@code date >= today}，
     * 患者根本看不见过去的排班；如果这里回一个不同的码，等于用接口响应告诉调用方
     * "这条 id 存在、只是过期了"，把一条读侧已经屏蔽掉的信息又漏了出去。
     * 这不是猜测性的防御——{@code appointment_time} 要落在过去这件事本身就没有业务意义。
     */
    private Schedule requireBookableSchedule(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getDate().isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return schedule;
    }

    /** 排班上的医生必须还在（V1 没有外键，医生被删不会报错，只会留下指向不存在医生的预约） */
    private Doctor requireDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return doctor;
    }

    /**
     * 出参。{@code departmentName} 要再跳一次 doctor → department，
     * 因为 PRD 80 行「确认预约信息—展示就诊人、<b>科室</b>、医生、时间、费用」把科室列成了必须展示项，
     * 而 {@code appointment} 表里没有科室列（科室是医生的属性，V1:87）。
     * 一次挂号多两次单行主键查询可以接受：这是写路径，量级远小于患者刷列表。
     */
    private AppointmentResponse toResponse(Appointment appointment, String patientName,
                                           Doctor doctor, String prepayId) {
        AppointmentResponse response = new AppointmentResponse();
        response.setId(appointment.getId());
        response.setOrderNo(appointment.getOrderNo());
        response.setStatus(appointment.getStatus());
        response.setPatientName(patientName);
        response.setDoctorName(doctor.getName());
        Department department = departmentMapper.selectById(doctor.getDepartmentId());
        response.setDepartmentName(department == null ? null : department.getName());
        Schedule schedule = scheduleMapper.selectById(appointment.getScheduleId());
        response.setTimeSlot(schedule == null ? null : schedule.getTimeSlot());
        response.setAppointmentTime(appointment.getAppointmentTime());
        response.setFeeFen(appointment.getFeeFen());
        response.setPrepayId(prepayId);
        return response;
    }
}
