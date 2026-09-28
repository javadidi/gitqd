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

    MORNING,
    AFTERNOON,
    EVENING;

    public static boolean isValid(String code) {
        return weight(code) < values().length;
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
