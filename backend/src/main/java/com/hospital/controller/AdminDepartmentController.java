package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminDepartmentResponse;
import com.hospital.dto.DepartmentSaveRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminCatalogCommandService;
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
 * 管理端科室管理（T27 卡片 737 行 / PRD 4.5.2 的 401–404 行）。
 *
 * <p>读不要能力、写要 {@code MANAGE_HOSPITAL}：与 T25 排班、T26 退款一个口径——
 * 权限在切面层，不在按钮上。
 */
@RestController
@RequestMapping("/admin/departments")
public class AdminDepartmentController {

    private final AdminCatalogCommandService adminCatalogCommandService;

    public AdminDepartmentController(AdminCatalogCommandService adminCatalogCommandService) {
        this.adminCatalogCommandService = adminCatalogCommandService;
    }

    /** 科室列表（PRD 402 行「展示所有科室」——所以不带任何筛选参数）。 */
    @GetMapping
    public Result<List<AdminDepartmentResponse>> list() {
        return Result.success(adminCatalogCommandService.listDepartments());
    }

    @GetMapping("/{id}")
    public Result<AdminDepartmentResponse> detail(@PathVariable Long id) {
        return Result.success(adminCatalogCommandService.departmentDetail(id));
    }

    @PostMapping
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminDepartmentResponse> create(@Valid @RequestBody DepartmentSaveRequest request) {
        return Result.success(adminCatalogCommandService.createDepartment(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminDepartmentResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody DepartmentSaveRequest request) {
        return Result.success(adminCatalogCommandService.updateDepartment(id, request));
    }

    /** 删除（软删）。科室下还有活医生时回 2008，不会静默把医生的归属删空。 */
    @DeleteMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<Void> delete(@PathVariable Long id) {
        adminCatalogCommandService.deleteDepartment(id);
        return Result.success(null);
    }
}
