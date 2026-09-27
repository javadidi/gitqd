package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.PatientCreateRequest;
import com.hospital.dto.PatientResponse;
import com.hospital.dto.PatientUpdateRequest;
import com.hospital.security.SecurityUtils;
import com.hospital.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端就诊人管理（T08）。
 *
 * <p>路径挂在 {@code /user/**} 下，因此自动继承 SecurityConfig 里
 * {@code .requestMatchers("/user/**").hasRole(LoginPatient.ROLE)} 的角色隔离——
 * 员工 token 打进来是 403，不需要为本卡改任何安全配置。
 *
 * <p>userId 一律 {@link SecurityUtils#currentUserId()} 从 token 取；
 * 路径上只有就诊人 id，请求体里没有 userId（附录 B 第 806 条）。
 * 归属校验在 {@link PatientService} 里还做了一遍，不是只信这一层。
 *
 * <p>没有 DELETE：PRD §3.11.1 与卡片 372-374 行都只列了列表/添加/编辑三项，
 * 而软删与 uk_card_no 的语义冲突尚未决策（见 PatientService 里的说明）。
 */
@RestController
@RequestMapping("/user/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    public Result<List<PatientResponse>> list() {
        return Result.success(patientService.list(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<PatientResponse> detail(@PathVariable Long id) {
        return Result.success(patientService.detail(SecurityUtils.currentUserId(), id));
    }

    @PostMapping
    public Result<PatientResponse> create(@Valid @RequestBody PatientCreateRequest request) {
        return Result.success(patientService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public Result<PatientResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody PatientUpdateRequest request) {
        return Result.success(patientService.update(SecurityUtils.currentUserId(), id, request));
    }
}
