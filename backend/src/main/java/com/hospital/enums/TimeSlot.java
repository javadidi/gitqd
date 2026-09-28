package com.hospital.enums;

/**
 * 出诊时段。码值出处 V1__init.sql:105 列注释「MORNING/AFTERNOON/EVENING」，
 * 与 seed.sql 造出的 150 行排班、小程序 utils/format.js 的 TIME_SLOT_LABELS 同一套码。
 *
 * <p><b>为什么要这个枚举</b>：T10 的 {@code CatalogService} 里已经有一份私有的
 * {@code SLOT_ORDER = List.of("MORNING","AFTERNOON","EVENING")} 用来排序，
 * T11 又要一份"哪些码值合法"来做入参校验。两处各写一遍，将来加个时段就会漏改一边
 * （排序漏改是顺序错，校验漏改是 400），所以收进 enums 包做单一来源，
 * 与 Capability / TaskStatus 等既有枚举同位置同用法。
 *
 * <p><b>用显式权重而不是字符串自然序</b>：按字母排是 AFTERNOON &lt; EVENING &lt; MORNING，
 * 与"上午→下午→晚上"的出诊顺序相反。声明顺序即权重（{@code ordinal()}）。
 */
public enum TimeSlot {

    MORNING(8, 30),
    AFTERNOON(14, 0),
    EVENING(18, 30);

    private final int startHour;
    private final int startMinute;

    TimeSlot(int startHour, int startMinute) {
        this.startHour = startHour;
        this.startMinute = startMinute;
    }

    /**
     * 该时段在排班日期上的开始时刻，用于算 {@code appointment.appointment_time}（V1:125「预约时间」）。
     *
     * <p><b>出处分两段，必须说清</b>：MORNING 08:30、AFTERNOON 14:00 是<b>抄的</b>——
     * {@code seed.sql:140} 造那 13 笔预约时写的就是
     * {@code TIMESTAMP(s.date, IF(s.time_slot = 'MORNING', '08:30:00', '14:00:00'))}，
     * 这是本仓库里唯一有出处的一对时刻，沿用它们能让 T12 新建的预约和种子预约在同一个口径上
     * （否则同一个时段，种子数据说 08:30、接口建出来的说 09:00，患者对照两条记录会看不出差别，
     * 而 {@code SeedCheckService} 也照不出这种漂移）。
     *
     * <p>EVENING 18:30 <b>是扩展，不是规格</b>：种子里没有任何晚间排班，所以那条 SQL 根本没覆盖到它。
     * 但 T11 已经让 EVENING 可排班、T12 就必须给它一个时刻，因为 {@code appointment_time NOT NULL}。
     * 取 18:30 的理由只有"下午 14:00 之后、且仍属晚间门诊的常规时段"，
     * <b>没有任何 PRD / 原型依据</b>。真按医院口径定这三段时刻属 T25「医生排班管理」页
     * （卡片 701 行），届时以那里为准，本处只是首版能跑通的暂定值。
     */
    public java.time.LocalTime startTime() {
        return java.time.LocalTime.of(startHour, startMinute);
    }

    public static boolean isValid(String code) {
        return weight(code) < values().length;
    }

    /**
     * 按码值取开始时刻，未知码值退到 14:00。
     *
     * <p>"未知退到 14:00"不是随手兜底，而是照抄 {@code seed.sql:140} 那条 SQL 的 ELSE 分支
     * （{@code IF(s.time_slot = 'MORNING', '08:30:00', '14:00:00')}）——
     * 种子里所有非 MORNING 的时段都算 14:00，所以这里遇到脏码值时的行为与种子完全一致。
     */
    public static java.time.LocalTime startTimeOf(String code) {
        for (TimeSlot slot : values()) {
            if (slot.name().equals(code)) {
                return slot.startTime();
            }
        }
        return AFTERNOON.startTime();
    }

    /** 未知码值返回 {@code values().length}，即排在所有已知时段之后 */
    public static int weight(String code) {
        for (TimeSlot slot : values()) {
            if (slot.name().equals(code)) {
                return slot.ordinal();
            }
        }
        return values().length;
    }
}
