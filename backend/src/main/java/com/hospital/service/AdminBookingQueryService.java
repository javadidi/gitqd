package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminNucleicResponse;
import com.hospital.dto.AdminPhysicalReportRequest;
import com.hospital.dto.AdminPhysicalReportResponse;
import com.hospital.dto.AdminPhysicalResponse;
import com.hospital.entity.NucleicAppointment;
import com.hospital.entity.Patient;
import com.hospital.entity.PhysicalAppointment;
import com.hospital.entity.PhysicalPackage;
import com.hospital.entity.Report;
import com.hospital.enums.ReportType;
import com.hospital.enums.SerialType;
import com.hospital.exception.BizException;
import com.hospital.mapper.NucleicAppointmentMapper;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.PhysicalAppointmentMapper;
import com.hospital.mapper.PhysicalPackageMapper;
import com.hospital.mapper.ReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端核酸 / 体检预约查询与体检报告录入（T25 卡片 699–700 行 / PRD 350–357 行）。
 *
 * <h2>本类第一次给 {@code report} 表写入</h2>
 * T17 建读侧、T22 放开 PHYSICAL 白名单，两张卡都写着"生产者在 T25"。
 * {@link #recordPhysicalReport} 就是那个生产者：录入一份 PHYSICAL 报告，
 * 患者侧立刻能在 T17 的报告列表与 T22 的体检报告入口看到它——
 * 这是本卡唯一一处跨卡兑现，测试里两头都钉了断言。
 *
 * <p>但<b>核酸报告仍然没有任何生产者</b>：T21 的红线（卡片 624 行「不做真实检测」）
 * 说的是"产品代码永不写 report"，发票可以模拟、医学结论不可以。
 * 本卡给体检开这条通道是因为 PRD 357 行明写「录入体检报告」，
 * 而核酸那一段（PRD 352 行）只有「查看检测预约详情」——一个字都没说录入。
 *
 * <h2>重复录入怎么办</h2>
 * {@code report} 表没有任何指向预约的列（T22 遗留 TODO 第 3 条），所以"这份报告属于哪次体检"
 * 只能靠 {@code patient_id + type='PHYSICAL'} 推。本卡的做法：录入前先看这个体检人有没有
 * PHYSICAL 报告，有就回 5002 拒绝，避免同一个人堆出两份互相矛盾的报告；
 * 而"查看"读的是最新那一份。真正的修法（report 加一列指向预约）留给有迁移预算的那张卡。
 */
@Service
public class AdminBookingQueryService {

    private final NucleicAppointmentMapper nucleicMapper;
    private final PhysicalAppointmentMapper physicalMapper;
    private final PhysicalPackageMapper packageMapper;
    private final PatientMapper patientMapper;
    private final ReportMapper reportMapper;
    private final SerialNumberService serialNumberService;
    private final ObjectMapper objectMapper;

    public AdminBookingQueryService(NucleicAppointmentMapper nucleicMapper,
                                    PhysicalAppointmentMapper physicalMapper,
                                    PhysicalPackageMapper packageMapper,
                                    PatientMapper patientMapper,
                                    ReportMapper reportMapper,
                                    SerialNumberService serialNumberService,
                                    ObjectMapper objectMapper) {
        this.nucleicMapper = nucleicMapper;
        this.physicalMapper = physicalMapper;
        this.packageMapper = packageMapper;
        this.patientMapper = patientMapper;
        this.reportMapper = reportMapper;
        this.serialNumberService = serialNumberService;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 核酸
    // ============================================================

    public List<AdminNucleicResponse> nucleicList(String status) {
        LambdaQueryWrapper<NucleicAppointment> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(NucleicAppointment::getStatus, status.trim());
        }
        List<NucleicAppointment> rows = nucleicMapper.selectList(query.orderByDesc(NucleicAppointment::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Patient> patients = patientsOf(rows.stream().map(NucleicAppointment::getPatientId).toList());
        List<AdminNucleicResponse> result = new ArrayList<>();
        for (NucleicAppointment row : rows) {
            result.add(toNucleic(row, patients.get(row.getPatientId()), false));
        }
        return result;
    }

    public AdminNucleicResponse nucleicDetail(Long id) {
        NucleicAppointment row = nucleicMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return toNucleic(row, patientMapper.selectById(row.getPatientId()), true);
    }

    private AdminNucleicResponse toNucleic(NucleicAppointment row, Patient patient, boolean withReport) {
        AdminNucleicResponse response = new AdminNucleicResponse();
        response.setId(row.getId());
        response.setOrderNo(row.getOrderNo());
        response.setPatientId(row.getPatientId());
        response.setPatientName(patient == null ? null : patient.getName());
        response.setCardNo(patient == null ? null : patient.getCardNo());
        response.setAppointmentDate(row.getAppointmentDate());
        response.setStatus(row.getStatus());
        response.setCreatedAt(row.getCreatedAt());
        if (withReport) {
            response.setReport(row.getReport());
        }
        return response;
    }

    // ============================================================
    // 体检
    // ============================================================

    public List<AdminPhysicalResponse> physicalList(String status) {
        LambdaQueryWrapper<PhysicalAppointment> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(PhysicalAppointment::getStatus, status.trim());
        }
        List<PhysicalAppointment> rows = physicalMapper
                .selectList(query.orderByDesc(PhysicalAppointment::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Patient> patients = patientsOf(rows.stream().map(PhysicalAppointment::getPatientId).toList());
        Map<Long, PhysicalPackage> packages = packagesOf(rows.stream()
                .map(PhysicalAppointment::getPackageId).toList());
        List<AdminPhysicalResponse> result = new ArrayList<>();
        for (PhysicalAppointment row : rows) {
            result.add(toPhysical(row, patients.get(row.getPatientId()), packages.get(row.getPackageId())));
        }
        return result;
    }

    public AdminPhysicalResponse physicalDetail(Long id) {
        PhysicalAppointment row = requirePhysical(id);
        return toPhysical(row, patientMapper.selectById(row.getPatientId()),
                packageMapper.selectById(row.getPackageId()));
    }

    private AdminPhysicalResponse toPhysical(PhysicalAppointment row, Patient patient, PhysicalPackage pack) {
        AdminPhysicalResponse response = new AdminPhysicalResponse();
        response.setId(row.getId());
        response.setOrderNo(row.getOrderNo());
        response.setPatientId(row.getPatientId());
        response.setPatientName(patient == null ? null : patient.getName());
        response.setCardNo(patient == null ? null : patient.getCardNo());
        // 套餐名与价格读时现场带：预约表没有这两列（T22 同一条），套餐软删则两键一起消失
        response.setPackageName(pack == null ? null : pack.getName());
        response.setPriceFen(pack == null ? null : pack.getPriceFen());
        response.setAppointmentDate(row.getAppointmentDate());
        response.setStatus(row.getStatus());
        response.setCreatedAt(row.getCreatedAt());
        return response;
    }

    // ============================================================
    // 体检报告：查看 + 录入（PRD 357 行）
    // ============================================================

    /** 报告详情。还没录入时回一个只有 appointmentId 的空壳，前端据此显示录入表单。 */
    public AdminPhysicalReportResponse report(Long appointmentId) {
        PhysicalAppointment appointment = requirePhysical(appointmentId);
        Report report = latestPhysicalReport(appointment.getPatientId());
        AdminPhysicalReportResponse response = new AdminPhysicalReportResponse();
        response.setAppointmentId(appointment.getId());
        if (report == null) {
            return response;
        }
        fillReport(response, report);
        return response;
    }

    /**
     * 录入体检报告（本卡唯一的 report 写入）。
     *
     * <p>审计 + 事务：这是"医院对一位患者说了一句话"级别的动作，必须留痕且不能只写一半。
     * {@code report_no} 走 {@link SerialType#YJ}（T17 建报告编号时用的就是它，不分类型）。
     */
    @AuditLog(action = "CREATE_PHYSICAL_REPORT", targetType = "report")
    @Transactional
    public AdminPhysicalReportResponse recordPhysicalReport(@AuditTarget Long appointmentId,
                                                            AdminPhysicalReportRequest request) {
        PhysicalAppointment appointment = requirePhysical(appointmentId);
        if (latestPhysicalReport(appointment.getPatientId()) != null) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS.getCode(),
                    "该体检人已有体检报告，请先查看现有报告再决定是否处理");
        }

        Report report = new Report();
        report.setReportNo(serialNumberService.next(SerialType.YJ));
        report.setPatientId(appointment.getPatientId());
        report.setType(ReportType.PHYSICAL.name());
        report.setResult(request.getResult().trim());
        // items 留 null：没有键名约定的 JSON 列不由这张表单填（见 AdminPhysicalReportRequest）
        report.setReportTime(LocalDateTime.now());
        reportMapper.insert(report);

        AdminPhysicalReportResponse response = new AdminPhysicalReportResponse();
        response.setAppointmentId(appointment.getId());
        fillReport(response, report);
        return response;
    }

    private void fillReport(AdminPhysicalReportResponse response, Report report) {
        response.setReportId(report.getId());
        response.setReportNo(report.getReportNo());
        response.setResult(report.getResult());
        response.setReportTime(report.getReportTime());
        response.setItems(parseItems(report.getItems()));
    }

    /** 与 T17/T22 同一条：items 原样透传，解不动就回 null，不抛给患者一个 500。 */
    private JsonNode parseItems(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    private Report latestPhysicalReport(Long patientId) {
        List<Report> rows = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getPatientId, patientId)
                .eq(Report::getType, ReportType.PHYSICAL.name())
                .orderByDesc(Report::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private PhysicalAppointment requirePhysical(Long appointmentId) {
        PhysicalAppointment appointment = physicalMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return appointment;
    }

    private Map<Long, Patient> patientsOf(Collection<Long> ids) {
        Set<Long> unique = new HashSet<>(ids);
        unique.remove(null);
        Map<Long, Patient> result = new HashMap<>();
        if (!unique.isEmpty()) {
            for (Patient patient : patientMapper.selectBatchIds(unique)) {
                result.put(patient.getId(), patient);
            }
        }
        return result;
    }

    private Map<Long, PhysicalPackage> packagesOf(Collection<Long> ids) {
        Set<Long> unique = new HashSet<>(ids);
        unique.remove(null);
        Map<Long, PhysicalPackage> result = new HashMap<>();
        if (!unique.isEmpty()) {
            for (PhysicalPackage pack : packageMapper.selectBatchIds(unique)) {
                result.put(pack.getId(), pack);
            }
        }
        return result;
    }
}
