package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminPhysicalItemResponse;
import com.hospital.dto.PhysicalItemSaveRequest;
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
 * 管理端体检项目管理（T27 卡片 739 行 / PRD 4.5.4 的 411–413 行）。
 *
 * <p>删除不加守卫，理由写在 {@code AdminPhysicalCommandService#deleteItem} 上：
 * 没有任何表引用 {@code physical_item}，套餐里存的是名字快照。
 */
@RestController
@RequestMapping("/admin/physical-items")
public class AdminPhysicalItemController {

    private final AdminPhysicalCommandService adminPhysicalCommandService;

    public AdminPhysicalItemController(AdminPhysicalCommandService adminPhysicalCommandService) {
        this.adminPhysicalCommandService = adminPhysicalCommandService;
    }

    /** 项目列表（PRD 412 行「展示所有体检项目」）。 */
    @GetMapping
    public Result<List<AdminPhysicalItemResponse>> list() {
        return Result.success(adminPhysicalCommandService.listItems());
    }

    @GetMapping("/{id}")
    public Result<AdminPhysicalItemResponse> detail(@PathVariable Long id) {
        return Result.success(adminPhysicalCommandService.itemDetail(id));
    }

    @PostMapping
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminPhysicalItemResponse> create(@Valid @RequestBody PhysicalItemSaveRequest request) {
        return Result.success(adminPhysicalCommandService.createItem(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminPhysicalItemResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody PhysicalItemSaveRequest request) {
        return Result.success(adminPhysicalCommandService.updateItem(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<Void> delete(@PathVariable Long id) {
        adminPhysicalCommandService.deleteItem(id);
        return Result.success(null);
    }
}
