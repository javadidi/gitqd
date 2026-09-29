package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.common.ErrorCode;
import com.hospital.dto.PhysicalAppointmentCreateRequest;
import com.hospital.dto.PhysicalAppointmentListItemResponse;
import com.hospital.entity.Patient;
import com.hospital.entity.PhysicalAppointment;
import com.hospital.entity.PhysicalPackage;
import com.hospital.enums.SerialType;
import com.hospital.exception.BizException;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PhysicalAppointmentMapper;
import com.hospital.mapper.PhysicalPackageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 体检预约（T22 卡片 637–641 行：选择体检人 → 套餐列表 → 套餐详情 → 确认预约信息 → 体检须知）。
 *
 * <h2>本卡写 {@code physical_appointment} 的行；套餐行不写</h2>
 * 与 T20/T21 一样，这张表在本卡之前零行、零生产者（逐字 grep 全仓 {@code physical}，
 * 只有建表语句、三个实体、三个空 mapper）。J49「体检预约 → 记录创建」要求本卡当生产者，
 * 所以预约行一律经真接口产生，测试与验收里<strong>没有一条 {@code INSERT INTO physical_appointment}</strong>。
 *
 * <p>但<b>套餐行本卡不生产</b>：它的生产者是点名存在的 T27 后台「体检套餐管理」
 * （PRD 406–408 行，{@code App.tsx:73} 占位路由已写明 {@code card="T27"}）。
 * 首版 {@code physical_package} 零行，所以患者端的套餐列表页必然是空的——
 * 接口照做、页面照做、空态老实显示，不往 seed 里塞几个套餐让页面好看（[[no-speculative-additions]]）。
 * 套餐行在测试与验收里都是<strong>人工取证探针</strong>，收尾删净。
 *
 * <h2>费用：只读、只展示、不落库、不扣款</h2>
 * {@code physical_appointment} 没有价格列（V1:280-291），PRD 587 行字典也没有，
 * 所以费用只能从 {@code physical_package.price_fen} 现场读。
 * 本卡<strong>不写 {@code payment_record}、不动 {@code patient.balance_fen}</strong>：
 * 规格里没有"体检缴费"这一步（T15 的门诊账单是院内推的，体检没有建单端点），
 * 造一条扣款记录就是替一笔不存在的交易编凭证。代价（改价会影响历史显示）
 * 写在 {@link PhysicalAppointmentListItemResponse} 的类注释与遗留 TODO。
 *
 * <h2>状态只写 {@code PENDING}</h2>
 * V1:286 列注释给了 {@code PENDING/CONFIRMED/COMPLETED/CANCELLED} 四个值。
 * 确认与完成是院内体检中心的事（T25 后台「预约体检管理 — 查看/录入体检报告」，PRD 354–357 行），
 * 取消规格里没给患者入口（对照退号：卡片 448 行明写了「退号」），
 * 所以首版只产生 PENDING，读路径仍原样回 {@code status}。
 *
 * <h2>归属一跳，越权与不存在同为 5001</h2>
 * 体检人就是就诊人（PRD 38 行「体检人 | 进行体检预约的人员」，表列名也是 {@code patient_id}），
 * 所以归属走 {@code physical_appointment.patient_id → patient.user_id}，与 T13–T21 同一条口径。
 */
@Service
public class PhysicalAppointmentService {

    /** V1:286 列注释给的初始态，也是本卡唯一写入的值。 */
    private static final String PENDING = "PENDING";

    private final PhysicalAppointmentMapper appointmentMapper;
    private final PhysicalPackageMapper packageMapper;
    private final PatientMapper patientMapper;
    private final SerialNumberService serialNumberService;

    public PhysicalAppointmentService(PhysicalAppointmentMapper appointmentMapper,
                                      PhysicalPackageMapper packageMapper,
                                      PatientMapper patientMapper,
                                      SerialNumberService serialNumberService) {
        this.appointmentMapper = appointmentMapper;
        this.packageMapper = packageMapper;
        this.patientMapper = patientMapper;
        this.serialNumberService = serialNumberService;
    }

    /**
     * 创建体检预约（卡片 640 行"确认"那一步，J49）。
     *
     * <p>一个事务里两步：插行 + 写审计（附录 B 第 3 条，与 T12/T14/T15/T19/T20/T21 同一写法）。
     * 全程不碰钱：没有扣款、没有支付单、没有余额变动。
     */
    @AuditLog(action = "CREATE_PHYSICAL_APPOINTMENT", targetType = "physical_appointment")
    @Transactional
    public PhysicalAppointmentListItemResponse create(Long userId, PhysicalAppointmentCreateRequest request) {
        Patient patient = patientMapper.selectById(request.getPatientId());
        // 不是你的体检人、没这个人、已软删 → 一律 5001，三种情况不区分。
        if (patient == null || !userId.equals(patient.getUserId())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        // 套餐不存在或已软删同样 5001：客户端提交了一个库里没有的引用。
        // 这一条必须查——package_id 是 NOT NULL 外键语义的列（V1:284），
        // 不查就会留下一条"套餐查不到名字与价格"的记录，确认页与记录页都显示不出来。
        PhysicalPackage pkg = packageMapper.selectById(request.getPackageId());
        if (pkg == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }

        PhysicalAppointment appointment = new PhysicalAppointment();
        appointment.setOrderNo(serialNumberService.next(SerialType.TJ));
        appointment.setPatientId(patient.getId());
        appointment.setPackageId(pkg.getId());
        appointment.setAppointmentDate(request.getAppointmentDate());
        appointment.setStatus(PENDING);
        appointmentMapper.insert(appointment);

        return toRow(appointment, patient.getName(), pkg);
    }

    /** 体检预约记录列表（PRD 309/527 行）：本人全部预约，按创建时间倒序。 */
    public List<PhysicalAppointmentListItemResponse> list(Long userId) {
        List<Long> myPatientIds = myPatientIds(userId);
        if (myPatientIds.isEmpty()) {
            return List.of();
        }
        List<PhysicalAppointment> rows = appointmentMapper.selectList(
                new LambdaQueryWrapper<PhysicalAppointment>()
                        .in(PhysicalAppointment::getPatientId, myPatientIds)
                        .orderByDesc(PhysicalAppointment::getCreatedAt)
                        .orderByDesc(PhysicalAppointment::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> patientNames = patientMapper.selectBatchIds(rows.stream()
                        .map(PhysicalAppointment::getPatientId).filter(Objects::nonNull)
                        .distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Patient::getId, Patient::getName));
        List<Long> packageIds = rows.stream().map(PhysicalAppointment::getPackageId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, PhysicalPackage> packages = packageIds.isEmpty() ? Map.of()
                : packageMapper.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(PhysicalPackage::getId, p -> p));

        return rows.stream().map(row -> toRow(row, patientNames.get(row.getPatientId()),
                packages.get(row.getPackageId()))).toList();
    }

    /**
     * 一行预约 + 两张关联表的现场值 → 列表行。
     *
     * <p>套餐被软删时 {@code pkg} 是 null（{@code @TableLogic} 会跳过它），
     * 于是 {@code packageName}/{@code priceFen} 都是 null → 两个键一起消失，
     * 前端 {@code || '—'} 兜底。<b>不拿套餐 id 冒充名字，也不把价格兜成 0</b>：
     * 0 元与"价格未知"在钱上是两件完全不同的事。
     */
    private PhysicalAppointmentListItemResponse toRow(PhysicalAppointment row, String patientName,
                                                      PhysicalPackage pkg) {
        PhysicalAppointmentListItemResponse item = new PhysicalAppointmentListItemResponse();
        item.setAppointmentId(row.getId());
        item.setOrderNo(row.getOrderNo());
        item.setPatientName(patientName);
        item.setAppointmentDate(row.getAppointmentDate());
        item.setStatus(row.getStatus());
        if (pkg != null) {
            item.setPackageName(pkg.getName());
            item.setPriceFen(pkg.getPriceFen());
        }
        return item;
    }

    private List<Long> myPatientIds(Long userId) {
        return patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
    }
}
