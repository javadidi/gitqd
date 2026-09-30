package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 反馈处理回复入参（T27 卡片 747 行 / PRD 442 行「查看反馈详情并进行处理回复」）。
 * 只有 reply 一个字段：PRD 594 行字典里反馈这一行只有「反馈ID、用户ID、内容、图片、状态、回复」六项，
 * 状态是回复的<b>结果</b>而不是管理员的输入（见 {@code AdminFeedbackService#reply}），
 * 所以这里不收 status，也不给"直接改状态"的口子。
 */
public class FeedbackReplyRequest {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 2000, message = "回复内容过长")
    private String reply;

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
