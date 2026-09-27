package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.InpatientBindRequest;
import com.hospital.dto.InpatientResponse;
import com.hospital.security.SecurityUtils;
import com.hospital.service.InpatientService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端住院人管理（T09）。
 *
 * <p>路径挂在 {@code /user/**} 下，因此自动继承 SecurityConfig 里
 * {@code .requestMatchers("/user/**").hasRole(LoginPatient.ROLE)} 的角色隔离——
 * 员工 token 打进来是 403，本卡不需要改任何安全配置（与 T08 的 PatientController 同）。
 *
 * <p>userId 一律 {@link SecurityUtils#currentUserId()} 从 token 取；
 * 路径上只有住院人 id，请求体里没有 userId（附录 B 第 806 条）。
 * 归属校验在 {@link InpatientService} 里还做了一遍，不是只信这一层。
 *
 * <p><b>只有三个端点，没有 PUT / DELETE</b>：PRD §9.1 的「住院人管理」行只写了
 * 「绑定住院号、查询住院人信息」（对比就诊人那行明写「添加/编辑/删除/查询」），
 * 卡片 DoD 是「住院人绑定通」，§6.1 页面清单里也没有编辑页或解绑页。
 * 四路证据一致，不是漏做——判定过程记在 docs/WORK_LOG.md 的 T09 节。
 */
@RestController
@RequestMapping("/user/inpatients")
public class InpatientController {

    private final InpatientService inpatientService;

    public InpatientController(InpatientService inpatientService) {
        this.inpatientService = inpatientService;
    }

    @GetMapping
    public Result<List<InpatientResponse>> list() {
        return Result.success(inpatientService.list(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<InpatientResponse> detail(@PathVariable Long id) {
        return Result.success(inpatientService.detail(SecurityUtils.currentUserId(), id));
    }

    @PostMapping
    public Result<InpatientResponse> bind(@Valid @RequestBody InpatientBindRequest request) {
        return Result.success(inpatientService.bind(SecurityUtils.currentUserId(), request));
    }
}
