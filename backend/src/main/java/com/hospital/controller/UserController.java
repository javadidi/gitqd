package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.BindPhoneRequest;
import com.hospital.dto.SendSmsCodeRequest;
import com.hospital.dto.UpdateNicknameRequest;
import com.hospital.dto.UserProfileResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序端个人中心（T07）。
 *
 * <p>整条路径在 SecurityConfig 里限定为 hasRole("patient")，员工 token 打不进来；
 * 每个方法的 userId 都从 token 取（SecurityUtils.currentUserId），请求体里没有 userId 字段。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public Result<UserProfileResponse> profile() {
        return Result.success(userService.getProfile(SecurityUtils.currentUserId()));
    }

    @PutMapping("/profile")
    public Result<UserProfileResponse> updateNickname(@Valid @RequestBody UpdateNicknameRequest request) {
        Long userId = SecurityUtils.currentUserId();
        userService.updateNickname(userId, request.getNickname());
        return Result.success(userService.getProfile(userId));
    }

    @PostMapping("/sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        userService.sendBindPhoneCode(SecurityUtils.currentUserId(), request.getPhone());
        return Result.success();
    }

    @PostMapping("/phone")
    public Result<UserProfileResponse> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        return Result.success(userService.bindPhone(
                SecurityUtils.currentUserId(), request.getPhone(), request.getCode()));
    }
}
