package com.hospital.dto;

import java.util.List;

/**
 * 批量排班结果（T25）。
 *
 * <p><b>为什么回"建了几条 + 跳过哪几条"而不是全成功或全失败</b>：
 * {@code uk_doctor_date_slot}（V1:111）让"这个医生这天这个时段已经有班"成为唯一索引级事实，
 * 而批量排班最常见的用法就是"把下周排一遍"——中途撞上一条就整批回滚，
 * 管理员只能一条条重试，那是把数据库的正常约束变成用户体验灾难。
 *
 * <p>所以：先查已存在的组合，只插缺的；{@code skipped} 逐项写清是哪天哪段。
 * 整批仍在一个事务里——"跳过"是正常结果不是异常，不需要靠回滚来表达。
 */
public class AdminScheduleBatchResponse {

    private Integer createdCount;
    private List<Long> createdIds;
    private Integer skippedCount;
    /** 每项形如 {@code 2026-10-08/MORNING}，与请求里的日期×时段一一对应。 */
    private List<String> skipped;

    public Integer getCreatedCount() { return createdCount; }
    public void setCreatedCount(Integer createdCount) { this.createdCount = createdCount; }
    public List<Long> getCreatedIds() { return createdIds; }
    public void setCreatedIds(List<Long> createdIds) { this.createdIds = createdIds; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public List<String> getSkipped() { return skipped; }
    public void setSkipped(List<String> skipped) { this.skipped = skipped; }
}
