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
 * 就诊人管理（T08）：列表 / 详情 / 新增 / 编辑 / 删除。
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
 * <p><b>删除的语义（T08-G 补，与用户确认过的方案）</b>：软删，且<b>卡号不释放给他人</b>。
 * 就诊卡号在医院是实体卡号，用户把自己小程序里的就诊人删掉 ≠ 医院注销了这张卡；
 * 若放给别人重用，历史预约/缴费/报告（7 张表以 {@code patient_id NOT NULL} 引用本表，且无外键约束）
 * 的卡号归属就会错乱。所以：删掉后<b>本人</b>用同一卡号重新添加 = 复活原来那一行（id 不变，
 * 信息按新填的覆盖）；<b>他人</b>用同一卡号添加 = 仍然 1004。
 * 物理删除不可行——硬删不会报错（没有外键），但会静默留下一堆指向不存在就诊人的单据。
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
        // 活行占着这个卡号（不管是本人的还是别人的）→ 直接 1004，不给复活机会
        if (isCardNoTakenByLiveRow(cardNo, null)) {
            throw new BizException(ErrorCode.PATIENT_CARD_NO_EXISTS);
        }

        Patient patient = new Patient();
        patient.setUserId(userId);
        patient.setName(request.getName().trim());
        patient.setCardNo(cardNo);
        patient.setIdCard(cryptoService.encrypt(request.getIdCard().trim()));
        patient.setPhone(cryptoService.encrypt(request.getPhone().trim()));
        patient.setRelation(request.getRelation());

        // 走到这里说明活行里没有这个卡号，但 uk_card_no 可能还被一条软删行占着。
        // 若那条软删行是本用户的，就复活它（保留原 id，历史单据的 patient_id 仍然有效）；
        // 若是别人的，下面的 insert 会撞唯一索引 → 1004，卡号不外放。
        if (patientMapper.reviveSoftDeletedByCardNo(patient) == 1) {
            // 复活的就是这一行，必然查得到（同一连接、刚被置成 deleted=0）
            Patient revived = patientMapper.selectOne(
                    new LambdaQueryWrapper<Patient>()
                            .eq(Patient::getUserId, userId)
                            .eq(Patient::getCardNo, cardNo));
            return toResponse(revived);
        }

        try {
            patientMapper.insert(patient);
        } catch (DuplicateKeyException e) {
            // 两种情况都会撞：并发新增同一个卡号（前置查两边都没查到），
            // 或卡号被**别人**的软删行占着。patient 表除主键外只有 uk_card_no 一个唯一索引，
            // 所以撞的必然是卡号，一律回 1004。
            throw new BizException(ErrorCode.PATIENT_CARD_NO_EXISTS);
        }
        return toResponse(patient);
    }

    /**
     * 删除 = 逻辑删（{@code deleted=1}），不是物理删。
     *
     * <p>先过 {@link #requireOwned}，所以删别人的、删已删的、删不存在的都是 1003，
     * 攻击者无法用这个接口探测某个 id 是否存在。
     */
    public void delete(Long userId, Long patientId) {
        requireOwned(userId, patientId);
        patientMapper.deleteById(patientId);
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
            if (isCardNoTakenByLiveRow(cardNo, patientId)) {
                throw new BizException(ErrorCode.PATIENT_CARD_NO_EXISTS);
            }
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
            // 前置查只看活行，所以撞索引的就是别人（或本人已删）那一行的卡号
            throw new BizException(ErrorCode.PATIENT_CARD_NO_EXISTS);
        }
        return toResponse(patient);
    }

    /**
     * R1 第一层：前置查活行。返回 true 表示这个卡号已被一条<b>未删除</b>的行占用。
     *
     * <p>查不出"谁占了这个卡号"，也不该查——那是别人的就诊信息。
     * {@code excludeId} 用于编辑时把自己排除掉，否则改自己其他字段也会被自己的卡号拦下。
     *
     * <p><b>看不见软删行，这是 {@code @TableLogic} 的行为，也是这里唯一的信息缺口</b>：
     * {@code uk_card_no} 建在 card_no 单列上、不认识 deleted 列，所以软删行仍占着索引。
     * 缺口由两个调用方各自补：
     * <ul>
     *   <li>{@code create} 补法是「本人软删行 → 复活；他人软删行 → 让 insert 撞索引转 1004」；</li>
     *   <li>{@code update} 刻意<b>不补</b>——改卡号撞上任何软删行都直接 1004。
     *       编辑一个活着的就诊人没有理由把另一个已删除的就诊人复活，那会让用户莫名其妙地
     *       "删掉的人又回来了"，而且复活的行 id 与正在编辑的行不是同一条。</li>
     * </ul>
     */
    private boolean isCardNoTakenByLiveRow(String cardNo, Long excludeId) {
        Long count = patientMapper.selectCount(
                new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getCardNo, cardNo)
                        .ne(excludeId != null, Patient::getId, excludeId));
        return count != null && count > 0;
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
