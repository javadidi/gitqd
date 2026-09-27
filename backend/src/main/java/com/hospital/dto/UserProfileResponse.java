package com.hospital.dto;

public class UserProfileResponse {

    private Long userId;
    private String nickname;
    private String avatarUrl;
    /** 打码后的手机号，给个人中心展示用；未绑定时为 null */
    private String phone;
    private Boolean hasPhone;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Boolean getHasPhone() { return hasPhone; }
    public void setHasPhone(Boolean hasPhone) { this.hasPhone = hasPhone; }
}
