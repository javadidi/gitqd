package com.hospital.enums;

/**
 * 任务处理方式。REVIEW 必须打开单据逐条处理，不存在批量完成路径。
 */
public enum TaskScope {

    ACTION,
    REVIEW
}
