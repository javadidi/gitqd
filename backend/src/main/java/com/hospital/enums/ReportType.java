package com.hospital.enums;

/**
 * 报告类型（V1:206 的 {@code report.type} 列注释「LAB/IMAGING/PHYSICAL」）。
 *
 * <p>与 T11 的 {@code TimeSlot} 同一个角色：码值的单一出处，不做展示翻译。中文标签
 * （检验报告 / 检查报告 / 体检报告）在小程序 {@code utils/format.js} 的
 * {@code REPORT_TYPE_LABELS} 里，与管理端 {@code StatusBadge}、T08 的 {@code relation}
 * 都是同一套取舍——后端只存/只回码值。
 *
 * <p><b>{@code PHYSICAL} 在 T17 被刻意挡在可查白名单外，由 T22 放开</b>：V1 把它和 LAB/IMAGING
 * 放进同一列，但 PRD 把两种查询写成了两个小节——§3.4.1「检查报告查询」（158–163 行，只讲检验/检查两类）
 * 与 §3.4.2「体检报告查询」（165–169 行），§6.1 第 518 行那一格同样把「体检报告查询」单列成页面名。
 * 承接体检的是 <b>T22（卡片 642 行「体检报告：查看体检报告」/ J50）</b>。
 *
 * <p>T22 落地时按本注释预留的钩子改了这一行白名单，<b>没有新增第二套报告端点</b>：
 * 体检报告的数据就在同一张 {@code report} 表里，再造 {@code /user/physical-reports}
 * 等于给一张表两个读路径。放开之后的分工是——
 * <ul>
 *   <li>T17 的「选择报告类型」页仍只给 LAB/IMAGING 两个选项（§3.4.1 161 行原话只列这两类），
 *       所以患者从报告查询入口<b>看不见</b>体检报告，这一卡的界面一字未改；</li>
 *   <li>体检报告从 T22 的入口进（个人中心「体检预约记录」与体检流程的出路按钮），
 *       走的是同一对端点带 {@code ?type=PHYSICAL}；</li>
 *   <li>跨卡改动之后重跑了 T17 的全部 15 例（附录 C「改完必须重跑被改卡的全部测试」），
 *       其中原本断言"PHYSICAL 进不来"的两条改成断言"PHYSICAL 进得来且只回体检行"。</li>
 * </ul>
 */
public enum ReportType {

    LAB,
    IMAGING,
    PHYSICAL;

    /**
     * 患者侧报告查询允许的类型白名单。T17 建时只有 LAB/IMAGING，
     * T22（卡片 642 行 / J50）放开 PHYSICAL——理由见上面的类注释。
     */
    public static boolean isQueryable(String code) {
        return LAB.name().equals(code) || IMAGING.name().equals(code) || PHYSICAL.name().equals(code);
    }
}
