package com.hospital.controller;

import com.hospital.common.ErrorCode;
import com.hospital.dto.ChangePasswordRequest;
import com.hospital.dto.LoginRequest;
import com.hospital.dto.LoginResponse;
import com.hospital.dto.WechatLoginRequest;
import com.hospital.dto.WechatLoginResponse;
import com.hospital.common.Result;
import com.hospital.entity.Admin;
import com.hospital.entity.Role;
import com.hospital.exception.BizException;
import com.hospital.mapper.AdminMapper;
import com.hospital.mapper.RoleMapper;
import com.hospital.security.LoginUser;
import com.hospital.service.AdminAccountService;
import com.hospital.service.CaptchaService;
import com.hospital.service.PermissionService;
import com.hospital.service.UserService;
import com.hospital.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AdminMapper adminMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;
    private final CaptchaService captchaService;
    private final UserService userService;
    private final AdminAccountService adminAccountService;

    public AuthController(AdminMapper adminMapper,
                          RoleMapper roleMapper,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          PermissionService permissionService,
                          CaptchaService captchaService,
                          UserService userService,
                          AdminAccountService adminAccountService) {
        this.adminMapper = adminMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
        this.captchaService = captchaService;
        this.userService = userService;
        this.adminAccountService = adminAccountService;
    }

    @GetMapping("/captcha")
    public Result<CaptchaService.Captcha> captcha() {
        return Result.success(captchaService.generate());
    }

    /**
     * 小程序微信授权登录（T07 / J14、J15）。
     * 与上面的 /auth/login 是两套主体：这里签发的是患者 token（principal=user），
     * 打不进任何管理后台接口。
     */
    @PostMapping("/wechat-login")
    public Result<WechatLoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return Result.success(userService.loginByWechat(request.getCode()));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 先消费验证码再查库：验证码一次性作废，密码错也要重新取图，防止脱机爆破
        if (!captchaService.verifyAndConsume(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new BizException(ErrorCode.CAPTCHA_INVALID);
        }

        Admin admin = adminMapper.selectOne(
                new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, request.getUsername())
        );
        if (admin == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }

        Role role = roleMapper.selectById(admin.getRoleId());
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }

        String roleName = role.getName();
        // 模块列表读 role.permissions（V1:392），不再只读代码里的静态表：
        // 卡片 763 行「角色管理：CRUD + 权限配置」配的就是这一列，
        // 登录不读它的话，页面上的勾选永远不会生效。四个内置角色的等价性由
        // PermissionServiceTest.resolveModules_coreRolesMatchStaticMatrix 钉住。
        List<String> modules = permissionService.resolveModules(roleName, role.getPermissions());
        List<String> caps = permissionService.getCaps(roleName).stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        String token = jwtUtil.generateToken(admin.getId(), admin.getUsername(), roleName, modules, caps);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setAdminId(admin.getId());
        response.setUsername(admin.getUsername());
        response.setRole(roleName);
        response.setModules(modules);
        response.setCaps(caps);
        response.setLandingPage(resolveLandingPage(roleName));

        return Result.success(response);
    }

    /**
     * 修改自身密码（T28 卡片 766 行 / PRD 4.6.5 的 465 行、PRD 9.2 的 628 行把它列在
     * 「认证授权 | 管理员登录、登出、<b>修改密码</b>」而不是系统设置那一行，所以路由挂在 /auth 下，
     * 与 /auth/login 同族——它需要的是"是我本人"，不是"我有管理能力"）。
     *
     * <p>不挂 {@code @RequireCap}：四个后台角色都该能改自己的口令。
     * 主体从 token 取，因此没有"改别人密码"的路径（替别人重置在规格里不存在）。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        adminAccountService.changePassword(request);
        return Result.success(null);
    }

    private String resolveLandingPage(String roleName) {
        switch (roleName) {
            case "doctor":
                return "/schedule";
            case "nurse":
                return "/appointments";
            default:
                return "/dashboard";
        }
    }
}
