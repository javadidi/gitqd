package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("feedback")
public class Feedback extends BaseEntity {

    private Long userId;
    private String content;
    private String images;
    private String status;
    private String reply;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
