package com.hospital.enums;

/**
 * 报告类型（V1:206 的 {@code report.type} 列注释「LAB/IMAGING/PHYSICAL」）。
 *
 * <p>与 T11 的 {@code TimeSlot} 同一个角色：码值的单一出处，不做展示翻译。中文标签
 * （检验报告 / 检查报告 / 体检报告）在小程序 {@code utils/format.js} 的
 * {@code REPORT_TYPE_LABELS} 里，与管理端 {@code StatusBadge}、T08 的 {@code relation}
 * 都是同一套取舍——后端只存/只回码值。
 *
 * <p><b>{@code PHYSICAL} 有意不在本卡的可查白名单里</b>：V1 把它和 LAB/IMAGING 放进同一列，
 * 但 PRD 把两种查询写成了两个小节——§3.4.1「检查报告查询」（158–163 行，只讲检验/检查两类）
 * 与 §3.4.2「体检报告查询」（165–169 行）。§6.1 第 518 行那一格同样把「体检报告查询」
 * 与前两页并列列出。承接体检的是 <b>T22（体检服务，卡片 642 行「体检报告：查看体检报告」/
 * J50）</b>，不是本卡。所以 {@link #isQueryable} 只放 LAB/IMAGING：
 * T17 若顺手支持 PHYSICAL，就等于替 T22 把它的数据路径做掉一半，
 * 而后卡要改的只是这个白名单一行（T22 到时候还得加体检报告专属的报告项展示，那是另一回事）。
 */
public enum ReportType {

    LAB,
    IMAGING,
    PHYSICAL;

    /** 患者侧报告查询（T17）允许的类型白名单。 */
    public static boolean isQueryable(String code) {
        return LAB.name().equals(code) || IMAGING.name().equals(code);
    }
}
