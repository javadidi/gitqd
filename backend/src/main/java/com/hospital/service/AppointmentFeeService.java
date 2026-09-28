package com.hospital.service;

import com.hospital.entity.Doctor;
import com.hospital.entity.Title;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.TitleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 挂号费的唯一出处（T12）。
 *
 * <p><b>这是一个规格缺口，不是规格要求</b>，所以先说清证据链，免得后来的人把它当成 PRD 定过的东西：
 * <ul>
 *   <li>PRD 全文只有 §3.3.1 第 80 行「确认预约信息—展示就诊人、科室、医生、时间、<b>费用</b>等」
 *       和 §7.1 第 546 行流程里的「支付挂号费」两句，<b>从未给出任何数额</b>；</li>
 *   <li>V1 建表 28 张表里，只有 {@code appointment.fee_fen}（V1:126）存放"这笔预约收了多少钱"，
 *       <b>没有任何一列存放"定价"</b>——{@code title} 表只有 name/sort_order（V1:72-79），
 *       {@code doctor}、{@code schedule} 也都没有费用列；</li>
 *   <li>PRD §4.4 费用管理（366-383 行，落在 T26）里全是消费/充值/退款<b>记录查询</b>，没有定价功能；
 *       §4.6.3 职称管理（456-458 行，落在 T28）只说「添加职称类型」，同样没有费用字段；</li>
 *   <li>{@code seed.sql:22} 已经白纸黑字承认过这件事：「PRD 未规定任何挂号费/缴费数额，
 *       下列金额是为让列表可读而定的」。</li>
 * </ul>
 *
 * <p><b>这里的三个数字不是新发明的</b>：它们就是种子里已经跑通自检的那三个
 * （主任医师 5000 / 副主任医师 3000 / 主治医师 2000 分）。逐笔核对过 13 条种子预约全部吻合：
 * 医生 1 张伟、3 王建国（title=主任医师）名下都是 5000；医生 2 李慧敏（副主任医师）是 3000；
 * 医生 4 陈雪、5 刘一鸣（主治医师）是 2000。所以本类是把 seed 里散着的既有约定收敛成一处，
 * 而不是再加一套可能和它冲突的口径。
 *
 * <p><b>为什么不建 V4 给 {@code title} 加一列 fee_fen</b>：那等于凭空给 PRD 的职称表发明一个字段，
 * 并且连带要求 T28 的职称管理页出现一个规格从没画过的输入框。等真定价需求落地（T26 费用管理或 T28 系统设置）
 * 时再建表、再搬数据，本类就是那天来临前唯一的取值出口——届时只改这一个类。
 *
 * <p><b>金额只能从这里来</b>（卡片 458 行红线「支付金额禁篡改」）：
 * 创建预约的入参里没有任何金额字段，费用一律由服务端按排班→医生→职称现算，
 * 客户端传来的任何数字都不参与记账。
 *
 * <p><b>查不到职称时按最低标准收</b>（{@code doctor.title_id} 在 V1:88 是可空列）：
 * 宁可少收医院也不能多收患者的钱，同时打一条 WARN 把这条数据暴露出来——
 * 静默按 0 收或者抛异常都会让一笔正常挂号莫名失败。
 */
@Service
public class AppointmentFeeService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentFeeService.class);

    /** 分 → 元为单位。key 是 title.name，出处见类注释。 */
    private static final Map<String, Long> FEN_BY_TITLE_NAME = Map.of(
            "主任医师", 5000L,
            "副主任医师", 3000L,
            "主治医师", 2000L);

    /** 兜底标准：三种职称里最低的那一档，与 seed 的主治医师取值一致。 */
    private static final long FALLBACK_FEN = 2000L;

    private final DoctorMapper doctorMapper;
    private final TitleMapper titleMapper;

    public AppointmentFeeService(DoctorMapper doctorMapper, TitleMapper titleMapper) {
        this.doctorMapper = doctorMapper;
        this.titleMapper = titleMapper;
    }

    /**
     * 按医生算挂号费（分）。医生必然已由调用方校验过存在性，这里只负责职称这一跳。
     *
     * <p>两次单行查询而不是让调用方把 Title 传进来：计费口径只走这一条路径，
     * 免得将来出现「某个入口带了职称、另一个入口没带」的分叉账本。
     */
    public long feeFenOfDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null || doctor.getTitleId() == null) {
            log.warn("医生 {} 没有职称，挂号费按最低标准 {} 分计（见 AppointmentFeeService 类注释）",
                    doctorId, FALLBACK_FEN);
            return FALLBACK_FEN;
        }
        Title title = titleMapper.selectById(doctor.getTitleId());
        return feeFenOfTitleName(title == null ? null : title.getName(), doctorId);
    }

    /**
     * 已知职称名时的查表版本——给"职称已经在手上"的调用方用，避免为了取一个价格再跳两次库。
     *
     * <p>T10 的医生详情就是这个情况：它本来就查了 {@code title} 来出职称名，
     * 排班条目上要挂挂号费（PRD 80 行「确认预约信息—展示…费用」要求患者提交前就看得见价格），
     * 再走一次 {@link #feeFenOfDoctor} 就是平白多两次查询。
     *
     * @param doctorId 只用于兜底日志里指认是谁，允许为 null
     */
    public long feeFenOfTitleName(String titleName, Long doctorId) {
        Long fee = titleName == null ? null : FEN_BY_TITLE_NAME.get(titleName);
        if (fee == null) {
            log.warn("职称「{}」（医生 {}）不在挂号费表内，按最低标准 {} 分计", titleName, doctorId, FALLBACK_FEN);
            return FALLBACK_FEN;
        }
        return fee;
    }
}
