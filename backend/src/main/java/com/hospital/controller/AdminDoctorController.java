package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminCatalogOptionsResponse;
import com.hospital.dto.AdminDoctorResponse;
import com.hospital.dto.DoctorSaveRequest;
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
 * 管理端医生管理（T27 卡片 736 行 / PRD 4.5.1 的 396–399 行，J59「医生管理 → CRUD 通」）。
 */
@RestController
@RequestMapping("/admin/doctors")
public class AdminDoctorController {

    private final AdminCatalogCommandService adminCatalogCommandService;

    public AdminDoctorController(AdminCatalogCommandService adminCatalogCommandService) {
        this.adminCatalogCommandService = adminCatalogCommandService;
    }

    /** 医生列表（PRD 397 行「展示所有医生信息」）。科室名与职称名由服务端解析。 */
    @GetMapping
    public Result<List<AdminDoctorResponse>> list() {
        return Result.success(adminCatalogCommandService.listDoctors());
    }

    /**
     * 表单选项（科室 + 职称）。声明在 {@code /{id}} 之前，且 {@code /options} 是字面量段，
     * PathPattern 认"字面量比变量具体"，所以不会被 {@code /{id}} 吃掉——
     * 这条与 T25 的 {@code /admin/appointments/filters} 是同一件事，测试里有对应的锁。
     */
    @GetMapping("/options")
    public Result<AdminCatalogOptionsResponse> options() {
        return Result.success(adminCatalogCommandService.options());
    }

    @GetMapping("/{id}")
    public Result<AdminDoctorResponse> detail(@PathVariable Long id) {
        return Result.success(adminCatalogCommandService.doctorDetail(id));
    }

    @PostMapping
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminDoctorResponse> create(@Valid @RequestBody DoctorSaveRequest request) {
        return Result.success(adminCatalogCommandService.createDoctor(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<AdminDoctorResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody DoctorSaveRequest request) {
        return Result.success(adminCatalogCommandService.updateDoctor(id, request));
    }

    /** 删除（软删）。还有未来排班或未取消预约时回 2009。 */
    @DeleteMapping("/{id}")
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<Void> delete(@PathVariable Long id) {
        adminCatalogCommandService.deleteDoctor(id);
        return Result.success(null);
    }
}
