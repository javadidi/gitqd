package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.MedicalRecordDetailResponse;
import com.hospital.dto.MedicalRecordListItemResponse;
import com.hospital.entity.Doctor;
import com.hospital.entity.MedicalRecord;
import com.hospital.entity.Patient;
import com.hospital.exception.BizException;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.MedicalRecordMapper;
import com.hospital.mapper.PatientMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 病历查询（T18 卡片 562–574 行：病历列表 / 病历详情）。
 *
 * <p>纯读，与 T10/T13/T16/T17 同一个分工：<b>故意不加 {@code @Transactional}</b>。
 *
 * <h2>本卡一条病历都不生产</h2>
 * {@code medical_record} 与 T17 的 {@code report} 同构：{@code seed.sql} 里零行
 * （逐字 grep 过 {@code insert into medical_record}，无匹配），28 张卡里没有任何一张写它，
 * PRD §4 后台也没有病历录入页。真实部署里它由院内 HIS 生成 —— <b>但这句是行业常识推断，
 * 规格从没说过</b>（PRD 全文没有 HIS/LIS/PACS 任何一个词）。所以本卡一律只读，
 * 验收取证只能裸插探针行（标成「人工取证探针」）。
 *
 * <h2>与 T17 最大的形状差别：没有筛选条件</h2>
 * 病历没有类型列（{@code V1:220-232} 六列里没有分类字段），PRD 614 行的接口概览
 * 「病历列表、病历详情」也没有给任何参数。所以列表端点<b>一个 query 参数都没有</b>，
 * 不像 T17 那样有 {@code ?type=}。附录 B 第 10 条（筛选进 URL）对本卡是 N/A ——
 * 没有筛选可进，硬造一个"按医生筛"就是发明需求。
 *
 * <h2>归属：一跳</h2>
 * 表里没有 {@code user_id}，所以经 {@code medical_record.patient_id → patient.user_id} 跳一次。
 * 列表用 {@code patient_id IN (我的就诊人)} 收口；详情双条件定位，越权与不存在与软删同为 5001。
 *
 * <h2>「医嘱」为什么不在这里</h2>
 * 卡片 566 行与 PRD 177 行都点名了医嘱，但 PRD 590 行数据字典与 V1 建表语句都没有这一列。
 * 取舍与三条理由记在 {@link MedicalRecordDetailResponse} 的类注释里，
 * 本服务不假装它有这个字段。
 */
@Service
public class MedicalRecordService {

    private final MedicalRecordMapper recordMapper;
    private final PatientMapper patientMapper;
    private final DoctorMapper doctorMapper;

    public MedicalRecordService(MedicalRecordMapper recordMapper,
                                PatientMapper patientMapper,
                                DoctorMapper doctorMapper) {
        this.recordMapper = recordMapper;
        this.patientMapper = patientMapper;
        this.doctorMapper = doctorMapper;
    }

    /**
     * 本人全部历史病历，按就诊时间倒序（最近一次就诊在前——"历史病历"就是这个读法，
     * 与 T13 预约记录同口径，与 T16 候诊列表的升序相反）。
     */
    public List<MedicalRecordListItemResponse> list(Long userId) {
        List<Long> myPatientIds = myPatientIds(userId);
        if (myPatientIds.isEmpty()) {
            return List.of();
        }
        List<MedicalRecord> rows = recordMapper.selectList(new LambdaQueryWrapper<MedicalRecord>()
                .in(MedicalRecord::getPatientId, myPatientIds)
                .orderByDesc(MedicalRecord::getRecordTime)
                .orderByDesc(MedicalRecord::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Names names = namesOf(rows);
        return rows.stream().map(row -> {
            MedicalRecordListItemResponse item = new MedicalRecordListItemResponse();
            item.setRecordId(row.getId());
            item.setRecordNo(row.getRecordNo());
            item.setPatientName(names.patients.get(row.getPatientId()));
            item.setDoctorName(names.doctors.get(row.getDoctorId()));
            item.setRecordTime(row.getRecordTime());
            return item;
        }).toList();
    }

    /** 病历详情（卡片 566 行）。越权、不存在、软删三种情况同为 5001。 */
    public MedicalRecordDetailResponse detail(Long userId, Long recordId) {
        MedicalRecord record = recordMapper.selectById(recordId);
        if (record == null || !myPatientIds(userId).contains(record.getPatientId())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Names names = namesOf(List.of(record));
        MedicalRecordDetailResponse detail = new MedicalRecordDetailResponse();
        detail.setRecordId(record.getId());
        detail.setRecordNo(record.getRecordNo());
        detail.setPatientName(names.patients.get(record.getPatientId()));
        detail.setDoctorName(names.doctors.get(record.getDoctorId()));
        detail.setRecordTime(record.getRecordTime());
        detail.setDiagnosis(record.getDiagnosis());
        detail.setPrescription(record.getPrescription());
        return detail;
    }

    private List<Long> myPatientIds(Long userId) {
        return patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
    }

    /**
     * 两次批量查把名字捞回来（逐行查就是 2N 次 SQL）。
     *
     * <p>医生名走 {@code doctor} 表而不是把 {@code doctor_id} 外放：与 T13/T16/T17 同一条纪律
     * ——同一位医生在预约记录、候诊页、病历页必须显示同一个名字，而名字只有一个出处。
     * 医生行被软删时这里就是 {@code null}（{@code @TableLogic} 会跳过它），
     * 前端 {@code || '—'} 兜底，不拿 id 冒充名字。
     */
    private Names namesOf(List<MedicalRecord> rows) {
        Map<Long, String> patients = patientMapper.selectBatchIds(
                        rows.stream().map(MedicalRecord::getPatientId).filter(Objects::nonNull)
                                .distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Patient::getId, Patient::getName));
        List<Long> doctorIds = rows.stream().map(MedicalRecord::getDoctorId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> doctors = doctorIds.isEmpty() ? Map.of()
                : doctorMapper.selectBatchIds(doctorIds).stream()
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName));
        return new Names(patients, doctors);
    }

    /** 一批查询的临时容器（与 T13/T16 的 {@code Names} 同形，只是这里只有两张名字表）。 */
    private static final class Names {
        private final Map<Long, String> patients;
        private final Map<Long, String> doctors;

        private Names(Map<Long, String> patients, Map<Long, String> doctors) {
            this.patients = patients;
            this.doctors = doctors;
        }
    }
}
