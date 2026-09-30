package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminPackageTypeResponse;
import com.hospital.dto.PackageTypeSaveRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminPhysicalCommandService;
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
 * 管理端套餐类型（T27 卡片 740 行 / PRD 4.5.5 的 415–417 行）。
 *
 * <p><b>只有三把端点：列表、新增、修改，没有 {@code @DeleteMapping}。</b>
 * PRD 那一节只写了两句——「类型列表 — 展示套餐分类」与「新增套餐类型」——
 * 连删除都没提；卡片 740 行的「CRUD」在这里不能反过来当作授权，
 * 因为删一个正被套餐引用的类型会让列表出现空类型名，而规格里没有任何一句教管理员怎么处理这种情况。
 * 取舍与理由同时记在 {@code AdminPhysicalCommandService} 的类注释和 WORK_LOG 的"有意未做"表里。
 */
@RestController
@RequestMapping("/admin/package-types")
public class AdminPackageTypeController {

    private final AdminPhysicalCommandService adminPhysicalCommandService;

    public AdminPackageTypeController(AdminPhysicalCommandService adminPhysicalCommandService) {
        this.adminPhysicalCommandService = adminPhysicalCommandService;
    }

    /** 类型列表（PRD 416 行）。seed 里那两个名字出自 PRD 417 行的括号举例。 */
    @GetMapping
    public Result<List<AdminPackageTypeResponse>> list() {
        return Result.success(adminPhysicalCommandService.listTypes());
    }

    @PostMapping
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminPackageTypeResponse> create(@Valid @RequestBody PackageTypeSaveRequest request) {
        return Result.success(adminPhysicalCommandService.createType(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminPackageTypeResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody PackageTypeSaveRequest request) {
        return Result.success(adminPhysicalCommandService.updateType(id, request));
    }
}
