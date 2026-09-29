package com.hospital.service;

import com.hospital.annotation.AuditLog;
import com.hospital.common.ErrorCode;
import com.hospital.dto.FollowUpCreateRequest;
import com.hospital.dto.FollowUpDetailResponse;
import com.hospital.entity.Department;
import com.hospital.entity.Doctor;
import com.hospital.entity.FollowUp;
import com.hospital.entity.Patient;
import com.hospital.exception.BizException;
import com.hospital.mapper.DepartmentMapper;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.FollowUpMapper;
import com.hospital.mapper.PatientMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 复诊配药（T20 卡片 598–612 行：在线复诊申请 + 复诊详情）。
 *
 * <h2>本卡是 {@code follow_up} 的第一个生产者</h2>
 * 与 P4 的报告/病历/候诊三张卡相反（那三张只读、{@code seed.sql} 零行、全仓无人写），
 * {@code follow_up} 在本卡之前只有三处痕迹：建表语句 {@code V1:312-323}、
 * 实体 {@code FollowUp}、空 mapper —— 没有任何一张卡往里写过一行。
 * 所以本卡必须自己产生数据（卡片 609 行 J45「复诊申请 → 记录创建」就是这条要求），
 * 不需要像 T16/T17/T18 那样在验收里裸插探针行才能看到内容。
 *
 * <h2>红线：首版只模拟流程，不开药</h2>
 * 卡片 606 行逐字：「不做真实开药（二期做）；首版仅模拟流程」。
 * 落到代码上是三件事都没做：没有药名清单、没有处方列、没有"配药信息"字段。
 * "模拟"模拟的是<strong>申请通路</strong>（提交 → 落一条记录 → 详情能查看），
 * 完整理由与四路证据记在 {@link FollowUpDetailResponse} 的类注释里。
 *
 * <h2>状态只有 {@code PENDING} 一个值被写入</h2>
 * V1:318 列注释给了 PENDING/IN_PROGRESS/COMPLETED，但推进状态是院内医生侧的动作，
 * 而 PRD §4 后台没有复诊管理页、28 张卡里也没有任何一张负责推进。
 * 与 T19 的 {@code PENDING}/{@code ISSUED}（模拟阶段开票即成功）同理：<b>不造一个没人写的中间态</b>，
 * 但读路径原样回 {@code status}，不假设写入侧只写过一种值。
 *
 * <h2>PRD 664 行「已就诊患者」这个资格门槛本卡做不到，有意不做</h2>
 * 逐字原文：{@code | 复诊配药 | 已就诊患者在线申请复诊并开具处方药的服务 |}。
 * "已就诊"要校验，唯一的凭证是 {@code medical_record} 里有这个就诊人的病历行，
 * 而 {@code medical_record} <b>首版没有生产者</b>（T18 已逐字确认：seed 零行、无后台录入页）。
 * 于是任何"必须有病历才能申请复诊"的实现都会把所有真实用户当场挡死，
 * 与 J45「复诊申请 → 记录创建」直接冲突。取舍：{@code patient} 归属本人这一层校验照做
 * （这是能做的、也是安全红线要求的），"已就诊"这一层<b>不实现也不假装实现</b>，
 * 记在 WORK_LOG 的遗留 TODO：等病历有生产者之后再补，或由产品确认改口径。
 *
 * <h2>归属一跳，越权与不存在同为 5001</h2>
 * {@code follow_up.patient_id → patient.user_id}。与 T13/T16/T17/T18/T19 同一条口径：
 * 别人的单、没这条单、就诊人被软删，三种情况都给同一个码，不回 403（403 会确认"这条存在"）。
 */
@Service
public class FollowUpService {

    /** V1:318 列注释给的初始态，也是本卡唯一写入的值。 */
    private static final String PENDING = "PENDING";

    private final FollowUpMapper followUpMapper;
    private final PatientMapper patientMapper;
    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;

    public FollowUpService(FollowUpMapper followUpMapper,
                           PatientMapper patientMapper,
                           DepartmentMapper departmentMapper,
                           DoctorMapper doctorMapper) {
        this.followUpMapper = followUpMapper;
        this.patientMapper = patientMapper;
        this.departmentMapper = departmentMapper;
        this.doctorMapper = doctorMapper;
    }

    /**
     * 创建复诊申请（卡片 602 行，J45「复诊申请 → 记录创建」）。
     *
     * <p>一个事务里两步：插 {@code follow_up} 行 + 写审计。审计必须同事务（附录 B 第 4 条，
     * 与 T12/T14/T15/T19 同一写法）：整笔回滚时审计也要一起消失，反之患者申请成功就必须留下一条
     * 谁在什么时候申请的记录。所以既不用 {@code @Async}，也不用 {@code REQUIRES_NEW}，
     * 更不在 {@code afterCommit} 里写 —— 复诊申请是院内业务凭证，不是外部通道调用。
     */
    @AuditLog(action = "CREATE_FOLLOW_UP", targetType = "follow_up")
    @Transactional
    public FollowUpDetailResponse create(Long userId, FollowUpCreateRequest request) {
        Patient patient = patientMapper.selectById(request.getPatientId());
        // 不是你的就诊人、没这个就诊人、就诊人已软删 → 一律 5001，三种情况不区分。
        if (patient == null || !userId.equals(patient.getUserId())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }

        Department department = departmentMapper.selectById(request.getDepartmentId());
        if (department == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Doctor doctor = doctorMapper.selectById(request.getDoctorId());
        if (doctor == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        // 医生与科室对不上 → 400。这一条不是新功能，是数据一致性守卫：
        // 详情页第 604 行要「内容正确」（J46），而"心血管内科 / 医生李慧敏（消化内科）"
        // 是一份自相矛盾的复诊单。小程序结构上产生不了错配（医生列表本来就是按
        // {@code ?departmentId=} 拉的，见 {@code pages/followup/apply}），
        // 只有手搓请求才能撞到，所以给 400 而不是新开一个错误码。
        if (!request.getDepartmentId().equals(doctor.getDepartmentId())) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "所选医生不属于该科室");
        }

        FollowUp followUp = new FollowUp();
        followUp.setPatientId(patient.getId());
        followUp.setDepartmentId(department.getId());
        followUp.setDoctorId(doctor.getId());
        followUp.setDisease(request.getDisease());
        followUp.setStatus(PENDING);
        followUpMapper.insert(followUp);

        // 回读一次：createdAt 以库里的时间戳为准（MP 插入后实体上是 JVM 算的值），
        // 这样申请成功页跳详情时看到的时间与详情接口给的完全一致。与 T19 同一条做法。
        return detail(userId, followUp.getId());
    }

    /** 复诊详情（卡片 604 行，J46「复诊详情 → 内容正确」）。越权、不存在、软删同为 5001。 */
    public FollowUpDetailResponse detail(Long userId, Long followUpId) {
        FollowUp followUp = followUpMapper.selectById(followUpId);
        if (followUp == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Patient patient = patientMapper.selectById(followUp.getPatientId());
        if (patient == null || !userId.equals(patient.getUserId())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }

        FollowUpDetailResponse detail = new FollowUpDetailResponse();
        detail.setFollowUpId(followUp.getId());
        detail.setPatientName(patient.getName());
        detail.setDisease(followUp.getDisease());
        detail.setStatus(followUp.getStatus());
        detail.setCreatedAt(followUp.getCreatedAt());

        // 科室名与医生名各查一次（详情只有一行，不像 T18 列表那样需要批量）。
        // 名字一律从各自的表解析，而不是把 departmentId/doctorId 外放给前端去猜：
        // 与 T13/T16/T17/T18 同一条纪律 —— 同一个医生在预约记录、候诊页、病历页、复诊页
        // 必须显示同一个名字，而名字只有一个出处。行被软删时这里就是 null，
        // 前端 {@code || '—'} 兜底，不拿 id 冒充名字。
        Department department = departmentMapper.selectById(followUp.getDepartmentId());
        detail.setDepartmentName(department == null ? null : department.getName());
        Doctor doctor = doctorMapper.selectById(followUp.getDoctorId());
        detail.setDoctorName(doctor == null ? null : doctor.getName());
        return detail;
    }
}
