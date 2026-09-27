package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.PatientCreateRequest;
import com.hospital.dto.PatientResponse;
import com.hospital.dto.PatientUpdateRequest;
import com.hospital.entity.Patient;
import com.hospital.exception.BizException;
import com.hospital.mapper.PatientMapper;
import com.hospital.util.MaskUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 就诊人管理（T08）：列表 / 详情 / 新增 / 编辑。
 *
 * <p>三条不可让的规矩：
 * <ol>
 *   <li><b>归属校验写在 service，不只在 controller</b>（附录 B「权限判断是否只写在 UI／入口」）。
 *       每一次读写都用 (id, user_id) 双条件定位，别人的就诊人在这里等同于不存在，
 *       一律抛 {@code PATIENT_NOT_FOUND(1003)}——不用 403，因为 403 会确认"这条记录存在"。</li>
 *   <li><b>身份证与手机号加密落库、打码出库</b>（卡片 379 行 ⚠️ 易混淆）。
 *       明文只在本类的方法栈里存在过，写库前过 {@link CryptoService#encrypt}，
 *       出库前过 {@link MaskUtil}，任何返回值都不带明文。</li>
 *   <li><b>R1 双层防护</b>：前置查给出友好提示，唯一索引兜住并发。</li>
 * </ol>
 *
 * <p>{@code create} / {@code update} 刻意<b>不加 {@code @Transactional}</b>：两者各只有一次写，
 * 没有跨表原子性需求；而一旦包进事务，下面 catch 住的 {@link DuplicateKeyException}
 * 会把事务标成 rollback-only，异常虽然被转成了友好的 1004，提交时照样炸成一个查不出原因的 500。
 * T07 的 {@code loginByWechat} 已经踩过并验证过这条，见其 javadoc。
 */
@Service
public class PatientService {

    private final PatientMapper patientMapper;
    private final CryptoService cryptoService;

    public PatientService(PatientMapper patientMapper, CryptoService cryptoService) {
        this.patientMapper = patientMapper;
        this.cryptoService = cryptoService;
    }

    public List<PatientResponse> list(Long userId) {
        List<Patient> patients = patientMapper.selectList(
                new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId)
                        .orderByAsc(Patient::getId));
        return patients.stream().map(this::toResponse).toList();
    }

    public PatientResponse detail(Long userId, Long patientId) {
        return toResponse(requireOwned(userId, patientId));
    }

    /** J17：加密落库；J18：重复就诊卡号被拒 */
    public PatientResponse create(Long userId, PatientCreateRequest request) {
        String cardNo = request.getCardNo().trim();
        requireCardNoAvailable(cardNo, null);

        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName(request.getName().trim());
        patient.setCardNo(cardNo);
        patient.setIdCard(cryptoService.encrypt(request.getIdCard().trim()));
        patient.setPhone(cryptoService.encrypt(request.getPhone().trim()));
        patient.setRelation(request.getRelation());
        try {
            patientMapper.insert(patient);
        } catch (DuplicateKeyException e) {
            // 并发新增同一个卡号：前置查两边都没查到，uk_card_no 只放一个过。
            // patient 表除主键外只有 uk_card_no 一个唯一索引，所以撞的必然是卡号。
            throw new BizException(ErrorCode.PATIENT_CARD_NO_EXISTS);
        }
        return toResponse(patient);
    }

    /**
     * J19：编辑。cardNo / idCard / phone 传空白 = 保持原值。
     *
     * <p>"留空即不改"不是偷懒：响应里只有打码值，前端拿不到明文，
     * 没法把原值预填回输入框；若强制必填，用户改个姓名就得重打一遍身份证和手机号。
     */
    public PatientResponse update(Long userId, Long patientId, PatientUpdateRequest request) {
        Patient patient = requireOwned(userId, patientId);

        String cardNo = trimToNull(request.getCardNo());
        if (cardNo != null && !cardNo.equals(patient.getCardNo())) {
            requireCardNoAvailable(cardNo, patientId);
            patient.setCardNo(cardNo);
        }

        String idCard = trimToNull(request.getIdCard());
        if (idCard != null) {
            patient.setIdCard(cryptoService.encrypt(idCard));
        }

        String phone = trimToNull(request.getPhone());
        if (phone != null) {
            patient.setPhone(cryptoService.encrypt(phone));
        }

        patient.setName(request.getName().trim());
        patient.setRelation(request.getRelation());
        try {
            patientMapper.updateById(patient);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PATIENT_CARD_NO_EXISTS);
        }
        return toResponse(patient);
    }

    /**
     * R1 第一层：前置查。命中就抛 1004，给用户看得懂的提示。
     *
     * <p>查不出"谁占了这个卡号"，也不该查——那是别人的就诊信息。
     * {@code excludeId} 用于编辑时把自己排除掉，否则改自己其他字段也会被自己的卡号拦下。
     *
     * <p>注意：{@code @TableLogic} 让 MyBatis-Plus 自动补 {@code deleted = 0}，
     * 但 {@code uk_card_no} 不认 deleted 列——软删过的卡号仍被唯一索引占着，
     * 于是前置查放过、insert 撞索引。T08 没有删除功能，这条路走不到；
     * 将来真要做删除（PRD §9.1 提过），必须先决定这个语义，别只加个接口。
     */
    private void requireCardNoAvailable(String cardNo, Long excludeId) {
        Long count = patientMapper.selectCount(
                new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getCardNo, cardNo)
                        .ne(excludeId != null, Patient::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PATIENT_CARD_NO_EXISTS);
        }
    }

    /** 归属校验：双条件定位，查不到就是"不存在"，不区分"没这条"和"不是你这条" */
    private Patient requireOwned(Long userId, Long patientId) {
        Patient patient = patientMapper.selectOne(
                new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getId, patientId)
                        .eq(Patient::getUserId, userId));
        if (patient == null) {
            throw new BizException(ErrorCode.PATIENT_NOT_FOUND);
        }
        return patient;
    }

    private PatientResponse toResponse(Patient patient) {
        PatientResponse response = new PatientResponse();
        response.setId(patient.getId());
        response.setName(patient.getName());
        response.setRelation(patient.getRelation());
        response.setCardNo(patient.getCardNo());
        // decrypt 对 SEED_ENC: 占位值返回 null，mask 再对 null 返回 null，前端按「未填写」渲染
        response.setIdCard(MaskUtil.maskIdCard(cryptoService.decrypt(patient.getIdCard())));
        response.setPhone(MaskUtil.maskPhone(cryptoService.decrypt(patient.getPhone())));
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
