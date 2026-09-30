package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 消息公告新增/修改入参（T28 卡片 765 行「消息公告管理：CRUD」/ PRD 4.6.4 的 462 行）。
 *
 * <p>PRD 462 行原文是「发布公告（标题、内容、类型、推送范围等）」，四个字段里
 * <b>「推送范围」这一项本卡不做，因为库里没有它的落点、也没有它的读侧</b>：
 * {@code announcement}（V1:344-353）只有 title/content/type/publish_time 四个业务列，
 * 而小程序唯一的公告消费方 {@code GET /user/stop-notices} 只按 type 过滤、不认识任何"范围"。
 * 加一列没人读 = 让管理员以为发出去的东西有定向效果，比不发更糟；
 * 这一条记在 WORK_LOG 的「有意未做」表里，附录 A 的消息推送（二期）才有它的落点。
 *
 * <p>{@code type} 是字符串而不是枚举类型字段：V1:348 那一列本来就是 VARCHAR(32)，
 * 服务层用 {@code AnnouncementType.of(raw)} 判定，未知值直接 400 拒绝——
 * 不静默兜底成 NOTICE（那等于替管理员选了一个他没选的类型）。
 */
public class AnnouncementSaveRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 256, message = "公告标题过长")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 8000, message = "公告内容过长")
    private String content;

    @NotBlank(message = "公告类型不能为空")
    private String type;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
