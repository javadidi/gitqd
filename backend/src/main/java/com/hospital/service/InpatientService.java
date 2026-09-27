package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.InpatientBindRequest;
import com.hospital.dto.InpatientResponse;
import com.hospital.entity.Inpatient;
import com.hospital.exception.BizException;
import com.hospital.mapper.InpatientMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 住院人管理（T09）：列表 / 详情 / 绑定住院号。
 *
 * <p><b>「绑定」在本卡是"用户自报建档"，不是"认领医院已登记的住院记录"。</b>
 * 卡片写的是「输入住院号 → 验证 → 绑定」，而"验证"能验的只有两件事：参数合法、住院号全局未被占用。
 * 选这个语义的证据是表结构本身——V1__init.sql:43 的 {@code user_id} 是 <b>NOT NULL</b>，
 * 也就是说这张表里不存在"还没归属任何用户"的行，没有可供认领的池子；
 * 而且 inpatient 表**没有身份证/手机号列**，即使造一个未认领池出来，
 * 也只能靠「住院号 + 姓名」匹配来验证，那等于任何知道这两个值的人都能绑走别人的住院记录，
 * 比自报建档更不安全。真实 HIS 对接是二期性质的事（附录 A 未列，但同性质），本卡不碰。
 *
 * <p>两条与 T08 就诊人同构的规矩：
 * <ol>
 *   <li><b>归属校验写在 service，不只在 controller</b>（附录 B 第 804 条「权限判断是否只写在 UI」）。
 *       每次读都用 (id, user_id) 双条件定位，别人的住院人在这里等同于不存在，
 *       一律抛 {@code INPATIENT_NOT_FOUND(1005)}——不用 403，因为 403 会确认"这条记录存在"。</li>
 *   <li><b>住院号唯一是两层</b>：前置查给出友好提示 {@code INPATIENT_NO_EXISTS(1006)}，
 *       {@code uk_inpatient_no} 兜住并发（catch {@link DuplicateKeyException} 再抛同一个码）。</li>
 * </ol>
 *
 * <p><b>与 T08 的一处刻意不同：这里没有复活分支。</b>
 * T08 的 {@code create} 会把"本人软删过的同一卡号"复活成原来那一行，那是因为 T08 有删除入口。
 * T09 四路规格证据（卡片动词清单、DoD「住院人绑定通」、PRD §9.1「绑定住院号、查询住院人信息」、
 * PRD §6.1 页面清单）都没提删除/解绑，本卡不建删除入口，
 * 所以程序自己永远不会造出软删行；住院号一旦被占用就不外放，撞上一律 1006。
 * （若将来加了删除，这段判断要重新过一遍：inpatient 被 recharge_record 与 case_delivery
 * 以 {@code inpatient_id} 引用，物理删同样会甩下孤儿单据。）
 *
 * <p>{@code bind} 刻意<b>不加 {@code @Transactional}</b>：只有一次写，没有跨表原子性需求；
 * 而一旦包进事务，下面 catch 住的 {@link DuplicateKeyException} 会把事务标成 rollback-only，
 * 异常虽然被转成了友好的 1006，提交时照样炸成一个查不出原因的 500。
 * T07 的 {@code loginByWechat}、T08 的 {@code create}/{@code update} 都是这个理由。
 *
 * <p>本卡不写 audit_log：PRD 需求文档 485 行把审计限定在「管理后台操作」，
 * 患者端接口一律不写（与 T07/T08 一致）。
 */
@Service
public class InpatientService {

    private final InpatientMapper inpatientMapper;

    public InpatientService(InpatientMapper inpatientMapper) {
        this.inpatientMapper = inpatientMapper;
    }

    public List<InpatientResponse> list(Long userId) {
        List<Inpatient> rows = inpatientMapper.selectList(
                new LambdaQueryWrapper<Inpatient>()
                        .eq(Inpatient::getUserId, userId)
                        .orderByAsc(Inpatient::getId));
        return rows.stream().map(this::toResponse).toList();
    }

    public InpatientResponse detail(Long userId, Long inpatientId) {
        return toResponse(requireOwned(userId, inpatientId));
    }

    /** J20：绑定住院号 → 验证通过后落库；J21：重复住院号 → 被拒 */
    public InpatientResponse bind(Long userId, InpatientBindRequest request) {
        String inpatientNo = request.getInpatientNo().trim();
        if (isInpatientNoTaken(inpatientNo)) {
            throw new BizException(ErrorCode.INPATIENT_NO_EXISTS);
        }

        Inpatient inpatient = new Inpatient();
        inpatient.setUserId(userId);
        inpatient.setName(request.getName().trim());
        inpatient.setInpatientNo(inpatientNo);
        inpatient.setDepartment(trimToNull(request.getDepartment()));
        inpatient.setBedNo(trimToNull(request.getBedNo()));

        try {
            inpatientMapper.insert(inpatient);
        } catch (DuplicateKeyException e) {
            // 走到这里只可能是并发绑定同一个住院号（两边前置查都没查到）：
            // inpatient 表除主键外只有 uk_inpatient_no 一个唯一索引，所以撞的必然是住院号。
            throw new BizException(ErrorCode.INPATIENT_NO_EXISTS);
        }
        return toResponse(inpatient);
    }

    /**
     * 第一层防护：前置查。返回 true 表示这个住院号已被占用（不管是本人的还是别人的）。
     *
     * <p>查不出"谁占了这个住院号"，也不该查——那是别人的住院信息。
     * {@code @TableLogic} 会自动补 {@code deleted = 0}，所以本方法看不见软删行；
     * 而 {@code uk_inpatient_no} 建在单列上、不认 deleted 列，软删行仍占着索引。
     * 这个缺口在 T09 无所谓（本卡不产生软删行），缺口由 catch 分支统一兜成 1006。
     */
    private boolean isInpatientNoTaken(String inpatientNo) {
        Long count = inpatientMapper.selectCount(
                new LambdaQueryWrapper<Inpatient>().eq(Inpatient::getInpatientNo, inpatientNo));
        return count != null && count > 0;
    }

    /** 归属校验：双条件定位，查不到就是"不存在"，不区分"没这条"和"不是你这条" */
    private Inpatient requireOwned(Long userId, Long inpatientId) {
        Inpatient inpatient = inpatientMapper.selectOne(
                new LambdaQueryWrapper<Inpatient>()
                        .eq(Inpatient::getId, inpatientId)
                        .eq(Inpatient::getUserId, userId));
        if (inpatient == null) {
            throw new BizException(ErrorCode.INPATIENT_NOT_FOUND);
        }
        return inpatient;
    }

    private InpatientResponse toResponse(Inpatient inpatient) {
        InpatientResponse response = new InpatientResponse();
        response.setId(inpatient.getId());
        response.setName(inpatient.getName());
        response.setInpatientNo(inpatient.getInpatientNo());
        response.setDepartment(inpatient.getDepartment());
        response.setBedNo(inpatient.getBedNo());
        response.setBoundAt(inpatient.getCreatedAt());
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
