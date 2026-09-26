package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user")
public class User extends BaseEntity {

    private String wechatOpenid;
    private String phone;
    private String nickname;
    private String avatarUrl;

    public String getWechatOpenid() { return wechatOpenid; }
    public void setWechatOpenid(String wechatOpenid) { this.wechatOpenid = wechatOpenid; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
