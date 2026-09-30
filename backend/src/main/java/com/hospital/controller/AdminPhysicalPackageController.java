package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminPhysicalPackageResponse;
import com.hospital.dto.PhysicalPackageSaveRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminPhysicalCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端体检套餐管理（T27 卡片 738 行 / PRD 4.5.3 的 406–409 行）。
 *
 * <p>患者侧那两把读端点（{@code /user/physical-packages*}，T22）不动：
 * 本卡是那张 items JSON 列的<b>生产者</b>，读写各走自己的路径，
 * 患者侧继续按"逐项取 name"渲染，形状由 {@link PhysicalPackageSaveRequest} 的类注释定义。
 */
@RestController
@RequestMapping("/admin/physical-packages")
public class AdminPhysicalPackageController {

    private final AdminPhysicalCommandService adminPhysicalCommandService;

    public AdminPhysicalPackageController(AdminPhysicalCommandService adminPhysicalCommandService) {
        this.adminPhysicalCommandService = adminPhysicalCommandService;
    }

    /** 套餐列表（PRD 407 行「展示所有体检套餐」，无筛选参数）。 */
    @GetMapping
    public Result<List<AdminPhysicalPackageResponse>> list() {
        return Result.success(adminPhysicalCommandService.listPackages());
    }

    @GetMapping("/{id}")
    public Result<AdminPhysicalPackageResponse> detail(@PathVariable Long id) {
        return Result.success(adminPhysicalCommandService.packageDetail(id));
    }

    @PostMapping
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminPhysicalPackageResponse> create(
            @Valid @RequestBody PhysicalPackageSaveRequest request) {
        return Result.success(adminPhysicalCommandService.createPackage(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminPhysicalPackageResponse> update(
            @PathVariable Long id, @Valid @RequestBody PhysicalPackageSaveRequest request) {
        return Result.success(adminPhysicalCommandService.updatePackage(id, request));
    }

    /** 删除（软删）。还有未取消的体检预约时回 2010。 */
    @DeleteMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<Void> delete(@PathVariable Long id) {
        adminPhysicalCommandService.deletePackage(id);
        return Result.success(null);
    }
}
