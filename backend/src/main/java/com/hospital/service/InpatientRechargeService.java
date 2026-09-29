package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.common.ErrorCode;
import com.hospital.dto.InpatientRechargeCreateRequest;
import com.hospital.dto.InpatientRechargeResponse;
import com.hospital.entity.Inpatient;
import com.hospital.entity.RechargeRecord;
import com.hospital.enums.SerialType;
import com.hospital.exception.BizException;
import com.hospital.mapper.InpatientMapper;
import com.hospital.mapper.RechargeRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 住院充值（T23 卡片 657 行「住院充值：选择住院人，输入充值金额，支付。」+
 * PRD 224-227 行四页流程 + PRD 300-301 行「住院充值记录列表 / 账单详情」）。
 *
 * <h2>它和 T14 门诊充值是同一条流水线的另一半</h2>
 * 两者写的是<b>同一张表</b>：{@code recharge_record} 从建表那天起就同时为两种充值设计
 * （V1:143 {@code patient_id} 注释「就诊人（门诊充值）」、V1:144 {@code inpatient_id} 注释
 * 「住院人（住院充值）」，两列都 {@code DEFAULT NULL}），seed 里也早就躺着一笔住院充值
 * （seed.sql:184 的 {@code SEED-RC-0003}，{@code patient_id} 为 NULL、{@code inpatient_id}=1）。
 * 所以本卡复用 {@code CF} 单号前缀（{@link SerialType#CF} 的标签就是「充值单号」，不分门诊住院）
 * 与 {@code markSuccess} 那条幂等 UPDATE，<b>不新增迁移、不新增列、不新增序列种类</b>。
 *
 * <h2>唯一的实质差别：这里没有任何余额可加</h2>
 * T14 的第四步行是 {@code patientMapper.addBalance}，因为 PRD 98 行写了「实时到账就诊卡余额」，
 * 而 {@code patient} 表有 {@code balance_fen} 列（V4 加的）。住院侧两样都没有：
 * {@code inpatient} 表（V1:41-53）没有余额列，PRD 与卡片也没有一句说住院充值会到账。
 * <b>所以本方法只推进单据状态，一张钱表都不碰。</b>这条不是"忘了写"，是被写成断言的契约：
 * 真 HTTP 验收与集成测试各钉一条「充值前后 {@code patient.balance_fen} 总额不变、
 * {@code payment_record}/{@code refund_record} 行数不变，唯一变动的行就是这条新充值单」，
 * 将来谁要给住院充值加到账，这三条断言会先响。
 *
 * <h2>为什么仍然要走"建单 → 支付 → 置成功"三步</h2>
 * 卡片红线 663 行「不做真实支付（二期做）；首版仅模拟流程」，mock 通道下这三步看着像绕圈。
 * 保留它有两个非审美的理由：① 单据必须先有 {@code order_no} 才挂得上流水号，
 * 这个次序在真实回调落地后不能反；② {@code markSuccess} 的 {@code WHERE status = 'PENDING'}
 * 让"同一张单被置两次成功"在数据库层面不可能，接真通道时不必回来补判重（T14 同一条理由）。
 *
 * <h2>归属：住院人必须是我的</h2>
 * {@code requireOwned} 按 {@code (id, user_id)} 双条件查，查不到与不是本人的同回
 * {@code INPATIENT_NOT_FOUND(1005)}——不用 403，403 等于向枚举者确认「这条住院人存在」，
 * 与 T09 的 {@code InpatientService.requireOwned} 同一条判法。
 */
@Service
public class InpatientRechargeService {

    private static final String PENDING = "PENDING";
    private static final String SUCCESS = "SUCCESS";
    private static final String PAY_METHOD = "WECHAT";

    private final RechargeRecordMapper rechargeMapper;
    private final InpatientMapper inpatientMapper;
    private final SerialNumberService serialNumberService;
    private final WechatPayService wechatPayService;

    public InpatientRechargeService(RechargeRecordMapper rechargeMapper,
                                    InpatientMapper inpatientMapper,
                                    SerialNumberService serialNumberService,
                                    WechatPayService wechatPayService) {
        this.rechargeMapper = rechargeMapper;
        this.inpatientMapper = inpatientMapper;
        this.serialNumberService = serialNumberService;
        this.wechatPayService = wechatPayService;
    }

    /**
     * 住院充值（J51：住院充值 → 记录创建）。
     *
     * <p>写序是「先验归属、再建单」：归属不通过时连一行流水都不该留下，
     * 否则别人的住院号能往自己账下堆垃圾单，而审计又会记成一次成功创建。
     */
    @AuditLog(action = "CREATE_INPATIENT_RECHARGE", targetType = "recharge_record")
    @Transactional
    public InpatientRechargeResponse recharge(Long userId, InpatientRechargeCreateRequest request) {
        Inpatient inpatient = requireOwned(userId, request.getInpatientId());
        long amountFen = request.getAmountFen();

        RechargeRecord record = new RechargeRecord();
        record.setOrderNo(serialNumberService.next(SerialType.CF));
        // patient_id 留 NULL：这张表用两列分别承载两种充值（V1:143-144），
        // 门诊单填 patient_id、住院单填 inpatient_id，另一侧为空。
        record.setInpatientId(inpatient.getId());
        record.setAmountFen(amountFen);
        record.setPayMethod(PAY_METHOD);
        record.setStatus(PENDING);
        rechargeMapper.insert(record);

        wechatPayService.prepay(record.getOrderNo(), amountFen);

        // 流水号先算进变量、再同时用于 UPDATE 与出参：内存与库必须来自同一次计算（T14 的教训）。
        String tradeNo = mockTradeNo(record.getId());
        if (rechargeMapper.markSuccess(record.getId(), tradeNo) == 0) {
            throw new BizException(ErrorCode.PAYMENT_FAILED);
        }

        record.setStatus(SUCCESS);
        record.setTradeNo(tradeNo);
        return toResponse(record, inpatient);
    }

    /**
     * 住院充值记录列表（PRD 300 行）。
     *
     * <p>{@code inpatientId} 可空：不传就是本人全部住院人的流水（个人中心那一层），
     * 传了就是「选择住院人员 → 住院记录」那一条链路（PRD 232-233 行）。
     * 传进来的一律先验归属，所以这个筛选参数不能用来读别人的记录。
     */
    public List<InpatientRechargeResponse> list(Long userId, Long inpatientId) {
        // 先验筛选参数，再取"我的住院人"。顺序反了会漏判：一个还没有任何住院人的用户
        // 拿别人的 inpatientId 来筛，会在归属检查之前就被"我没有人"这条短路返回成空列表（200），
        // 于是这个参数看起来被接受了——它其实只是恰好读不到东西。
        if (inpatientId != null) {
            requireOwned(userId, inpatientId);
        }
        List<Inpatient> mine = inpatientMapper.selectList(
                new LambdaQueryWrapper<Inpatient>().eq(Inpatient::getUserId, userId));
        if (mine.isEmpty()) {
            return List.of();
        }
        List<Long> myIds = mine.stream().map(Inpatient::getId).toList();
        List<RechargeRecord> rows = rechargeMapper.selectList(new LambdaQueryWrapper<RechargeRecord>()
                .in(RechargeRecord::getInpatientId, myIds)
                .eq(inpatientId != null, RechargeRecord::getInpatientId, inpatientId)
                .orderByDesc(RechargeRecord::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Inpatient> byId = mine.stream()
                .collect(Collectors.toMap(Inpatient::getId, row -> row));
        return rows.stream()
                .map(row -> toResponse(row, byId.get(row.getInpatientId())))
                .toList();
    }

    /**
     * 账单详情（PRD 301 行「账单详情 — 查看单笔充值明细」）。
     *
     * <p>{@code patient_id} 为空的单才归本接口管：门诊单与住院单共用一张表，
     * 少了这道判据，把 T14 的充值 id 传进来就会从这里读到一条门诊流水。
     */
    public InpatientRechargeResponse detail(Long userId, Long rechargeId) {
        RechargeRecord record = rechargeMapper.selectById(rechargeId);
        if (record == null || record.getInpatientId() == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        Inpatient inpatient = inpatientMapper.selectOne(new LambdaQueryWrapper<Inpatient>()
                .eq(Inpatient::getId, record.getInpatientId())
                .eq(Inpatient::getUserId, userId));
        if (inpatient == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return toResponse(record, inpatient);
    }

    /**
     * mock 通道下的微信流水号占位，与 T14 的 {@code MOCK_TXN_RC_} 同一族。
     * 真实对接后由回调里的 {@code transaction_id} 提供，届时该方法删除。
     */
    private String mockTradeNo(Long rechargeId) {
        return "MOCK_TXN_IRC_" + rechargeId;
    }

    private Inpatient requireOwned(Long userId, Long inpatientId) {
        Inpatient inpatient = inpatientMapper.selectOne(new LambdaQueryWrapper<Inpatient>()
                .eq(Inpatient::getId, inpatientId)
                .eq(Inpatient::getUserId, userId));
        if (inpatient == null) {
            throw new BizException(ErrorCode.INPATIENT_NOT_FOUND);
        }
        return inpatient;
    }

    private InpatientRechargeResponse toResponse(RechargeRecord record, Inpatient inpatient) {
        InpatientRechargeResponse response = new InpatientRechargeResponse();
        response.setId(record.getId());
        response.setOrderNo(record.getOrderNo());
        response.setInpatientId(record.getInpatientId());
        response.setInpatientName(inpatient == null ? null : inpatient.getName());
        response.setInpatientNo(inpatient == null ? null : inpatient.getInpatientNo());
        response.setAmountFen(record.getAmountFen());
        response.setStatus(record.getStatus());
        response.setPayMethod(record.getPayMethod());
        response.setTradeNo(record.getTradeNo());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }
}
