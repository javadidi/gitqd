package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateNicknameRequest {

    /** 上限 64 与 user.nickname VARCHAR(64) 对齐，超长会在库里被截断而不是报错 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称不能超过64个字符")
    private String nickname;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
