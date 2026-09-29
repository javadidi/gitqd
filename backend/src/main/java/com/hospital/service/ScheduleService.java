package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditReason;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminScheduleBatchResponse;
import com.hospital.dto.AdminScheduleSuspendResponse;
import com.hospital.dto.ScheduleAdminResponse;
import com.hospital.dto.ScheduleBatchCreateRequest;
import com.hospital.dto.ScheduleCreateRequest;
import com.hospital.dto.ScheduleRescheduleRequest;
import com.hospital.dto.ScheduleUpdateRequest;
import com.hospital.entity.Appointment;
import com.hospital.entity.Doctor;
import com.hospital.entity.RefundRecord;
import com.hospital.entity.Schedule;
import com.hospital.enums.TimeSlot;
import com.hospital.exception.BizException;
import com.hospital.mapper.AppointmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.ScheduleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排班管理（T11）。后台侧的排班 CRUD，**本卡不碰小程序、不碰 admin 前端**。
 *
 * <p><b>范围（五路证据）</b>：
 * <ol>
 *   <li>卡片 429-433 行：排班列表（日期/时段/总号源/剩余号源）、创建排班（医生/日期/时段/号源数量）、
 *       <b>R2 硬约束</b>（同一医生同一时段不可重复排班，后端前置查 + 唯一索引兜底）、
 *       修改/取消排班（<b>调整号源</b>或取消排班）。</li>
 *   <li>卡片 444 行 DoD：「排班 CRUD 通；唯一索引验证」——只有后端能力，没有页面要求。</li>
 *   <li>卡片 701 行（T25「管理后台 - 预约管理」）：「医生排班管理：设置医生排班，<b>支持批量排班、临时停诊/调班</b>」，
 *       配 J56「排班管理 → CRUD 通」。所以<b>批量排班、停诊/调班归 T25，本卡不做</b>；
 *       admin 侧那个占位页也早已标注归属——{@code admin/src/App.tsx:37}
 *       {@code <PlaceholderPage title="医生排班管理" prd="4.3.4" card="T25" />}。
 *       这与 T10 同型：T10 也没动 admin（医生管理/科室管理页标的是 T27）。</li>
 *   <li>PRD §4.3.4（需求文档 359-362 行）与 §9.2（630 行「排班管理（CRUD）、停诊设置」）
 *       是<b>后台功能/接口清单</b>，其中 CRUD 部分就是本卡；停诊设置随 T25 落地。</li>
 *   <li>PRD 41 行「医生 | <b>查看</b>排班信息、患者预约情况」——医生能看不能改，
 *       与 {@code PermissionService.ROLE_CAPS}（doctor/nurse 的 caps 为空）一致：
 *       列表端点只要求员工角色，三个写端点要 {@code MANAGE_DOCTOR} 能力。</li>
 * </ol>
 *
 * <p><b>R2 怎么做到"前置查 + 唯一索引兜底"两层</b>：
 * {@link #create} 先 {@code selectCount} 查活行（占着就 2002），再试复活软删行，最后 insert；
 * insert 撞 {@code uk_doctor_date_slot} 抛的 {@link org.springframework.dao.DuplicateKeyException}
 * <b>刻意不在这里 catch</b>——本方法在事务里，事务内 catch 住 DAO 异常，事务已被标成 rollback-only，
 * 提交时会炸成查不出原因的 500（T07 {@code loginByWechat}、T08 {@code PatientService} 都踩过并记了注释）。
 * 所以异常原样抛给 controller，由 controller 在事务外翻译成 2002
 * （并发下还可能在唯一索引的间隙锁上被判死锁，那是 {@code ConcurrencyFailureException}，同样翻译成 2002）。
 * 兜底路径只在并发下才会走到（两个请求同时通过前置查），但它是唯一能真正保证不出现重复排班的一层。
 *
 * <p><b>取消排班为什么要守卫（卡片 437 行「⚠️ 易混淆：排班取消时，已预约的记录需处理
 * （通知患者/自动退号）」）</b>：这一行的两个动作都不在本卡——
 * 「通知患者」是消息推送，属附录 A 二期待办（需求文档/任务卡 786 行）；
 * 「自动退号」属 T13 预约管理 + 退号（J31/J32），而且 T12 卡片 458 行红线明写
 * 「除本方法外禁止任何地方更新预约状态」。本卡于是取<b>拒绝</b>：
 * 排班下还有未取消的预约就直接 2007，一行都不改。
 * 这不是偷懒而是当前唯一安全的做法——seed.sql:131-158 已经造了 13 笔预约占着号源，
 * 静默取消会立刻产生一堆指向已取消排班的活预约，患者端还查得到、却挂不上号。
 * 守卫口径与 seed.sql:131 的注释一致：{@code status <> 'CANCELLED'} 即占用号源。
 *
 * <p><b>不加 V4 迁移</b>：取消用 {@code deleted} 软删（表已有该列），
 * 停诊状态列是 T25「临时停诊」才需要的东西，本卡没有规格要求就不提前建。
 *
 * <p><b>写操作一律 {@code @Transactional} + {@code @AuditLog}</b>：
 * PRD 485 行把审计限定在「管理后台操作」，本类四个方法里三个是后台写操作，都要记。
 * 事务由 {@code TransactionOrderConfig}（order=0）包在审计切面（@Order(100)）外层，
 * 所以业务回滚时审计一并回滚——这正是附录 B「审计是否同事务」要的样子，
 * 也是 T04 J8 已经验证过的行为。绝不用 {@code @Async}/{@code REQUIRES_NEW}/{@code afterCommit}。
 * 列表是纯读，不加事务也不记审计（与 T10 CatalogService 一致）。
 */
@Service
public class ScheduleService {

    /**
     * 号源占用口径：预约状态不是 CANCELLED 就算占着号。
     * 出处 seed.sql:131 行注释「号源占用口径（暂定，T12 实现退号时复核）：status <> 'CANCELLED' 即占用号源」，
     * 以及 V1__init.sql:124 的列注释「PENDING_PAYMENT/CONFIRMED/CANCELLED/COMPLETED」。
     * 预约状态枚举要到 T12 才会成为代码里的类型，本卡只用这一个值，先按字面量写并在此注明出处。
     */
    private static final String APPOINTMENT_CANCELLED = "CANCELLED";

    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final AppointmentMapper appointmentMapper;
    private final RefundTicketService refundTicketService;

    public ScheduleService(ScheduleMapper scheduleMapper,
                           DoctorMapper doctorMapper,
                           AppointmentMapper appointmentMapper,
                           RefundTicketService refundTicketService) {
        this.scheduleMapper = scheduleMapper;
        this.doctorMapper = doctorMapper;
        this.appointmentMapper = appointmentMapper;
        this.refundTicketService = refundTicketService;
    }

    // ============================================================
    // 查
    // ============================================================

    /**
     * J24 的读取侧：排班列表。三个筛选参数都是选填，全不传 = 全院全部活排班。
     *
     * <p><b>为什么是这三个</b>：卡片 430 行的列表维度就是「医生排班（日期/时段/总号源/剩余号源）」，
     * 加上"哪位医生"。不加关键词搜索、不加分页、不加时段筛选——卡片和 PRD §4.3.4 都没写，
     * 而分页/搜索/筛选进 URL 是<b>后台页面</b>的规矩（附录 B 第 808 条），页面属 T25。
     *
     * <p><b>只返回活排班</b>：{@code @TableLogic} 自动补 {@code deleted = 0}，已取消的看不见。
     * 后台要不要看已取消/停诊的排班是 T25 的需求（PRD 630 行「停诊设置」），
     * 本卡不为它提前开一个 {@code includeCancelled} 参数——那需要绕开逻辑删再手写一条查询，
     * 而目前没有任何调用方。
     *
     * <p><b>排序</b>：日期升序 → 同日按上午/下午/晚上 → 同时段按医生 id。
     * 时段那一级必须在 Java 里排（{@link TimeSlot#weight}），SQL 的字符串序会把
     * AFTERNOON 排到 MORNING 前面；医生姓名用一次 {@code selectBatchIds} 批量解析，不做 N+1。
     */
    public List<ScheduleAdminResponse> list(Long doctorId, LocalDate dateFrom, LocalDate dateTo) {
        List<Schedule> rows = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(doctorId != null, Schedule::getDoctorId, doctorId)
                .ge(dateFrom != null, Schedule::getDate, dateFrom)
                .le(dateTo != null, Schedule::getDate, dateTo));
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, String> doctorNames = doctorNamesOf(rows);
        List<ScheduleAdminResponse> result = new ArrayList<>(rows.size());
        for (Schedule row : rows) {
            result.add(toResponse(row, doctorNames.get(row.getDoctorId())));
        }
        result.sort(Comparator
                .comparing(ScheduleAdminResponse::getDate)
                .thenComparingInt(item -> TimeSlot.weight(item.getTimeSlot()))
                .thenComparing(ScheduleAdminResponse::getDoctorId));
        return result;
    }

    // ============================================================
    // 增
    // ============================================================

    /**
     * J24：创建排班 → 数据正确。J25 的第一层（前置查）也在这里。
     *
     * <p><b>审计的 target_id 是 NULL</b>：{@code AuditLogAspect.findTargetId} 只认标了
     * {@code @AuditTarget} 的 Long 参数，而创建时 id 还不存在（切面是"先审计后执行"，
     * 这正是业务失败能连带回滚审计的原因）。审计行仍可按 action + target_type 精确定位，
     * detail JSON 里带着完整的请求参数。
     *
     * <p>撞唯一索引时抛 {@code DuplicateKeyException}（并发被判死锁时是 {@code ConcurrencyFailureException}），
     * 都由 controller 翻译成 2002，理由见类注释。
     */
    @AuditLog(action = "CREATE_SCHEDULE", targetType = "schedule")
    @Transactional
    public ScheduleAdminResponse create(ScheduleCreateRequest request) {
        Doctor doctor = requireDoctor(request.getDoctorId());

        String timeSlot = request.getTimeSlot().trim();
        if (!TimeSlot.isValid(timeSlot)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "时段只能是 MORNING/AFTERNOON/EVENING，收到：" + timeSlot);
        }

        // R2 第一层：活行占着这个槽位就拒。软删行 selectCount 看不见（@TableLogic），留给下一步复活。
        Long live = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctor.getId())
                .eq(Schedule::getDate, request.getDate())
                .eq(Schedule::getTimeSlot, timeSlot));
        if (live != null && live > 0) {
            throw new BizException(ErrorCode.SCHEDULE_CONFLICT);
        }

        Schedule schedule = new Schedule();
        schedule.setDoctorId(doctor.getId());
        schedule.setDate(request.getDate());
        schedule.setTimeSlot(timeSlot);
        schedule.setTotalSlots(request.getTotalSlots());
        // 新排班没人预约过，剩余 = 总号源（与 seed.sql:108-124 的口径一致）
        schedule.setRemainingSlots(request.getTotalSlots());

        // uk_doctor_date_slot 不含 deleted 列，取消过的槽位仍被软删行占着 → 复活它，id 不变，
        // 历史 appointment 的 schedule_id 依旧有效。这是 J26「号源恢复」的最后一环。
        if (scheduleMapper.reviveSoftDeleted(schedule) == 1) {
            Schedule revived = scheduleMapper.selectOne(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getDoctorId, doctor.getId())
                    .eq(Schedule::getDate, request.getDate())
                    .eq(Schedule::getTimeSlot, timeSlot));
            return toResponse(revived, doctor.getName());
        }

        // R2 第二层由数据库兜：并发下两个请求都通过了上面的前置查，只有一个 insert 能成功
        scheduleMapper.insert(schedule);
        return toResponse(schedule, doctor.getName());
    }

    // ============================================================
    // 改
    // ============================================================

    /**
     * 调整号源。已约数（{@code total - remaining}）保持不变，剩余号源随总数同增减：
     * 总数 20 剩 5（已约 15）改成 30 → 剩 15；改成 10 → 被拒，因为已约 15 个号不能凭空消失。
     *
     * <p><b>已约数为什么从本行算而不是去数 appointment</b>：{@code remaining_slots} 就是号源账本，
     * seed.sql:167-173 也是按「总号源 − 未取消预约数」把它写平的。
     * 再数一遍预约等于引入第二个真相来源，两者一旦不一致（T12 的扣减还没实现）就说不清该信谁。
     */
    @AuditLog(action = "UPDATE_SCHEDULE", targetType = "schedule")
    @Transactional
    public ScheduleAdminResponse updateSlots(@AuditTarget Long scheduleId, ScheduleUpdateRequest request) {
        Schedule schedule = requireSchedule(scheduleId);
        Doctor doctor = requireDoctor(schedule.getDoctorId());

        int newTotal = request.getTotalSlots();
        // max(0, …)：防御 remaining > total 的脏行使已约数变成负数，从而把新的 remaining 抬到 total 之上
        int booked = Math.max(0, schedule.getTotalSlots() - schedule.getRemainingSlots());
        if (newTotal < booked) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "总号源不能小于已预约数（已约 " + booked + "）");
        }

        schedule.setTotalSlots(newTotal);
        schedule.setRemainingSlots(newTotal - booked);
        scheduleMapper.updateById(schedule);
        return toResponse(schedule, doctor.getName());
    }

    // ============================================================
    // 取消
    // ============================================================

    /**
     * J26：取消排班 → 剩余号源恢复。
     *
     * <p>守卫（卡片 437 行）：本排班下还有 {@code status <> 'CANCELLED'} 的预约 → 2007，一行不改。
     * TODO(T13)：退号能力落地后，这里应改为「同事务把这些预约置为 CANCELLED 并生成退款记录」，
     * 而不是继续拒绝；TODO(二期/附录 A)：取消后通知患者属消息推送，首版不做。
     *
     * <p>{@code cancelById} 返回 0 说明这行已被并发取消（守卫读到的还是活行）→ 2001，
     * 让调用方重新拉列表，而不是报一个"取消成功"的假象。
     *
     * @param reason 选填的取消原因，只进审计（audit_log.reason，VARCHAR(512)），不落 schedule 表——
     *               该表没有原因列，加列属 T25「临时停诊」的规格。
     */
    @AuditLog(action = "CANCEL_SCHEDULE", targetType = "schedule")
    @Transactional
    public void cancel(@AuditTarget Long scheduleId, @AuditReason String reason) {
        Schedule schedule = requireSchedule(scheduleId);

        Long active = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getScheduleId, scheduleId)
                .ne(Appointment::getStatus, APPOINTMENT_CANCELLED));
        if (active != null && active > 0) {
            throw new BizException(ErrorCode.SCHEDULE_HAS_APPOINTMENTS);
        }

        if (scheduleMapper.cancelById(scheduleId) == 0) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
    }

    // ============================================================
    // 批量排班 / 停诊 / 调班（T25 卡片 701 行）
    // ============================================================

    /**
     * 批量排班：一位医生 × 连续日期 × 若干时段 × 统一号源数。
     *
     * <p><b>撞已存在的组合是"跳过"而不是"报错"</b>：理由写在
     * {@link AdminScheduleBatchResponse} 的类注释里——"把下周排一遍"中途撞上一条就整批回滚，
     * 是把数据库的正常约束变成用户体验灾难。前置查一次拿齐已存在的 (日期, 时段)，
     * 只插缺的那些；并发下仍可能双双通过前置查，那时唯一索引会拒一个，
     * 由 controller 翻译成 2002（与 {@link #create} 同一条 R2 第二层）。
     */
    @AuditLog(action = "CREATE_SCHEDULE_BATCH", targetType = "schedule")
    @Transactional
    public AdminScheduleBatchResponse batchCreate(ScheduleBatchCreateRequest request) {
        Doctor doctor = requireDoctor(request.getDoctorId());
        LocalDate from = request.getDateFrom();
        LocalDate to = request.getDateTo();
        if (to.isBefore(from)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "结束日期不能早于起始日期");
        }
        List<String> slots = new ArrayList<>();
        for (String raw : request.getTimeSlots()) {
            String slot = raw == null ? "" : raw.trim();
            if (!TimeSlot.isValid(slot)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                        "时段只能是 MORNING/AFTERNOON/EVENING，收到：" + raw);
            }
            if (!slots.contains(slot)) {
                slots.add(slot);
            }
        }

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            dates.add(day);
        }
        Set<String> taken = new HashSet<>();
        for (Schedule existing : scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctor.getId())
                .in(Schedule::getDate, dates)
                .in(Schedule::getTimeSlot, slots))) {
            taken.add(existing.getDate() + "/" + existing.getTimeSlot());
        }

        List<Long> createdIds = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (LocalDate day : dates) {
            for (String slot : slots) {
                if (taken.contains(day + "/" + slot)) {
                    skipped.add(day + "/" + slot);
                    continue;
                }
                Schedule schedule = new Schedule();
                schedule.setDoctorId(doctor.getId());
                schedule.setDate(day);
                schedule.setTimeSlot(slot);
                schedule.setTotalSlots(request.getTotalSlots());
                schedule.setRemainingSlots(request.getTotalSlots());
                scheduleMapper.insert(schedule);
                createdIds.add(schedule.getId());
            }
        }

        AdminScheduleBatchResponse response = new AdminScheduleBatchResponse();
        response.setCreatedCount(createdIds.size());
        response.setCreatedIds(createdIds);
        response.setSkippedCount(skipped.size());
        response.setSkipped(skipped);
        return response;
    }

    /**
     * <b>临时停诊：取消一个已经有人订的班，并把这些预约一起退掉</b>。
     *
     * <p>这正是 T11 留给自己、T13 明确不接、最后落到本卡的那条 TODO
     * （T13 的定案原文：「T11 留下的 2007 守卫不在 T13 改，挪给 T25」）。
     * 与 {@link #cancel} 的分工是：<b>取消</b>用于还没人订的班（有活预约就 2007 拒绝），
     * <b>停诊</b>用于医院主动撤掉一个已经有人订的班——此时拒绝没有意义，
     * 班已经停了，剩下的问题只有"这些患者怎么办"，答案只能是同事务里替他们退号。
     *
     * <p>三条不变量：
     * <ul>
     *   <li>每张活预约走与 T13 同一套判定：{@code cancelIfActive} 受影响 0 行就跳过（并发下已被取消），
     *       绝不重复挂单；</li>
     *   <li>退款单由 {@link RefundTicketService} 统一挂，规则与患者端退号一字不差
     *       （只有已支付才挂、金额取账上的 {@code fee_fen}、状态只到 PENDING）；</li>
     *   <li><b>不还号源</b>：这个班马上就要被软删，把 {@code remaining_slots} 加回去
     *       只会造出"一个已停的班还有 20 个空位"的假账。T13 的退号必须还，因为班还在。</li>
     * </ul>
     *
     * <p>TODO(附录 A 二期)：停诊后通知患者属消息推送，首版不做——所以现在只有退款单
     * 与审计留痕，患者自己要在预约记录里看到"已取消"。
     */
    @AuditLog(action = "SUSPEND_SCHEDULE", targetType = "schedule")
    @Transactional
    public AdminScheduleSuspendResponse suspend(@AuditTarget Long scheduleId, @AuditReason String reason) {
        requireSchedule(scheduleId);

        List<Appointment> active = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getScheduleId, scheduleId)
                .ne(Appointment::getStatus, APPOINTMENT_CANCELLED));

        int refundCount = 0;
        long refundFen = 0L;
        for (Appointment appointment : active) {
            if (appointmentMapper.cancelIfActive(appointment.getId()) == 0) {
                // 并发下患者自己已经退号了。跳过，不能替他再挂一张退款单。
                continue;
            }
            RefundRecord refund = refundTicketService.issueForAppointment(appointment, reason);
            if (refund != null) {
                refundCount++;
                refundFen += refund.getAmountFen();
            }
        }

        if (scheduleMapper.cancelById(scheduleId) == 0) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }

        AdminScheduleSuspendResponse response = new AdminScheduleSuspendResponse();
        response.setScheduleId(scheduleId);
        response.setAppointmentCount(active.size());
        response.setRefundCount(refundCount);
        response.setRefundFen(refundFen);
        return response;
    }

    /**
     * 调班：把这个班挪到另一天/另一段。
     *
     * <p><b>有活预约就直接拒（2007），不自动挪人</b>：规格只写了"支持临时停诊/调班"六个字，
     * 没说过"调班时把已订患者一并迁到新时段"。真要自动迁移，就得决定号源够不够、
     * 单号换不换、原时段要不要腾出来——每一条都是替产品定规则。
     * 所以本卡的调班只服务"还没人订的班挪个时间"，已经有人订的要先停诊或让患者退号，
     * 错误文案里把这条路径说清楚。
     *
     * <p>撞 {@code uk_doctor_date_slot} 抛 {@code DuplicateKeyException}，由 controller 翻译成 2002，
     * 与 {@link #create} 同一条 R2 第二层。
     */
    @AuditLog(action = "RESCHEDULE_SCHEDULE", targetType = "schedule")
    @Transactional
    public ScheduleAdminResponse reschedule(@AuditTarget Long scheduleId,
                                            ScheduleRescheduleRequest request,
                                            @AuditReason String reason) {
        Schedule schedule = requireSchedule(scheduleId);
        Doctor doctor = requireDoctor(schedule.getDoctorId());

        Long active = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getScheduleId, scheduleId)
                .ne(Appointment::getStatus, APPOINTMENT_CANCELLED));
        if (active != null && active > 0) {
            throw new BizException(ErrorCode.SCHEDULE_HAS_APPOINTMENTS.getCode(),
                    "该班已有 " + active + " 位患者预约，请先停诊或等患者退号后再调班");
        }

        String timeSlot = request.getTimeSlot().trim();
        if (!TimeSlot.isValid(timeSlot)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "时段只能是 MORNING/AFTERNOON/EVENING，收到：" + request.getTimeSlot());
        }
        Long live = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, schedule.getDoctorId())
                .eq(Schedule::getDate, request.getDate())
                .eq(Schedule::getTimeSlot, timeSlot)
                .ne(Schedule::getId, scheduleId));
        if (live != null && live > 0) {
            throw new BizException(ErrorCode.SCHEDULE_CONFLICT);
        }

        schedule.setDate(request.getDate());
        schedule.setTimeSlot(timeSlot);
        scheduleMapper.updateById(schedule);
        return toResponse(schedule, doctor.getName());
    }

    // ============================================================
    // 内部
    // ============================================================

    private Schedule requireSchedule(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return schedule;
    }

    /** 医生不存在或已软删 → 5001，与 T10 CatalogService.requireDoctor 同码同理由 */
    private Doctor requireDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return doctor;
    }

    private Map<Long, String> doctorNamesOf(List<Schedule> rows) {
        Set<Long> doctorIds = new HashSet<>();
        for (Schedule row : rows) {
            doctorIds.add(row.getDoctorId());
        }
        List<Doctor> doctors = doctorMapper.selectBatchIds(doctorIds);
        Map<Long, String> result = new HashMap<>();
        for (Doctor doctor : doctors) {
            result.put(doctor.getId(), doctor.getName());
        }
        return result;
    }

    private ScheduleAdminResponse toResponse(Schedule schedule, String doctorName) {
        ScheduleAdminResponse response = new ScheduleAdminResponse();
        response.setId(schedule.getId());
        response.setDoctorId(schedule.getDoctorId());
        response.setDoctorName(doctorName);
        response.setDate(schedule.getDate());
        response.setTimeSlot(schedule.getTimeSlot());
        response.setTotalSlots(schedule.getTotalSlots());
        response.setRemainingSlots(schedule.getRemainingSlots());
        return response;
    }
}
