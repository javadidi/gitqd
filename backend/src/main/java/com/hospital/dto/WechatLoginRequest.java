package com.hospital.dto;

import jakarta.validation.constraints.NotBlank;

public class WechatLoginRequest {

    /** wx.login() 返回的临时凭证，一次性、5 分钟有效 */
    @NotBlank(message = "code不能为空")
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
