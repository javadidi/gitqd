package com.hospital.dto;

public class WechatLoginResponse {

    private String token;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    /** false = 还没绑手机号，小程序据此引导去个人中心绑定 */
    private Boolean hasPhone;
    /** 本次登录是否新建了 user（true=首次授权） */
    private Boolean newUser;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Boolean getHasPhone() { return hasPhone; }
    public void setHasPhone(Boolean hasPhone) { this.hasPhone = hasPhone; }
    public Boolean getNewUser() { return newUser; }
    public void setNewUser(Boolean newUser) { this.newUser = newUser; }
}
