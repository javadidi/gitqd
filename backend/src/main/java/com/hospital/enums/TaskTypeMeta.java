package com.hospital.enums;

import com.hospital.common.ErrorCode;
import com.hospital.exception.BizException;

import java.time.LocalDateTime;

/**
 * 任务类型元数据。清单以任务卡 T05 与 PRD 第四章后台功能为限（三个确认类对应 4.3.1–4.3.3，
 * 退款审核对应 4.4.6）；复诊配药与病案配送在后台侧只有记录列表、无审核动作，故不设类型，
 * 待 T20 等卡片确认确有人工判定环节再按实际单据流程补常量。
 * 除「预约确认 2h」由任务卡点名外，其余 defaultDueHours 为暂定 SLA，PRD 未规定。
 */
public enum TaskTypeMeta {

    APPOINTMENT_CONFIRM("预约确认", 2, TaskScope.ACTION, "/appointments/{id}"),
    NUCLEIC_CONFIRM("核酸采样确认", 4, TaskScope.ACTION, "/nucleic-appointments/{id}"),
    PHYSICAL_CONFIRM("体检预约确认", 24, TaskScope.ACTION, "/physical-appointments/{id}"),
    REFUND_REVIEW("退款审核", 24, TaskScope.REVIEW, "/refunds/{id}");

    private final String label;
    private final int defaultDueHours;
    private final TaskScope scope;
    private final String urlTemplate;

    TaskTypeMeta(String label, int defaultDueHours, TaskScope scope, String urlTemplate) {
        this.label = label;
        this.defaultDueHours = defaultDueHours;
        this.scope = scope;
        this.urlTemplate = urlTemplate;
    }

    public static TaskTypeMeta fromCode(String code) {
        for (TaskTypeMeta meta : values()) {
            if (meta.name().equals(code)) {
                return meta;
            }
        }
        throw new BizException(ErrorCode.TASK_TYPE_UNKNOWN);
    }

    public String label() { return label; }

    public int defaultDueHours() { return defaultDueHours; }

    public TaskScope scope() { return scope; }

    public String urlTemplate() { return urlTemplate; }

    public LocalDateTime defaultDueAt(LocalDateTime from) {
        return from.plusHours(defaultDueHours);
    }

    public String resolveUrl(Long relatedId) {
        return urlTemplate.replace("{id}", String.valueOf(relatedId));
    }
}
