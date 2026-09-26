package com.hospital.controller;

import com.hospital.common.ErrorCode;
import com.hospital.dto.LoginRequest;
import com.hospital.dto.LoginResponse;
import com.hospital.common.Result;
import com.hospital.entity.Admin;
import com.hospital.entity.Role;
import com.hospital.exception.BizException;
import com.hospital.mapper.AdminMapper;
import com.hospital.mapper.RoleMapper;
import com.hospital.security.LoginUser;
import com.hospital.service.PermissionService;
import com.hospital.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
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

    public AuthController(AdminMapper adminMapper,
                          RoleMapper roleMapper,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          PermissionService permissionService) {
        this.adminMapper = adminMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
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
        List<String> modules = permissionService.getModules(roleName);
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
