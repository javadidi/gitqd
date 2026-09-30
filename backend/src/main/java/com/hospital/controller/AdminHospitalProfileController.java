package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.HospitalProfileResponse;
import com.hospital.dto.ProfileSaveRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminContentCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端医院简介（T27 卡片 744 行 / PRD 4.5.9 的 431–432 行，两个字：「编辑」）。
 *
 * <p>两把端点，没有 POST/DELETE：{@code hospital_profile}（V6）是单行语义，
 * T24 的读取侧就是"按 id 升序取第一行"。PUT 在空表上会建出第一行（upsert），
 * 否则后台这个编辑页面对新装的库既打不开也救不回来——那是一把自锁死的门。
 */
@RestController
@RequestMapping("/admin/hospital-profile")
public class AdminHospitalProfileController {

    private final AdminContentCommandService adminContentCommandService;

    public AdminHospitalProfileController(AdminContentCommandService adminContentCommandService) {
        this.adminContentCommandService = adminContentCommandService;
    }

    /** 当前内容；没配置时 data 为 null，页面显示"还没有简介"。 */
    @GetMapping
    public Result<HospitalProfileResponse> profile() {
        return Result.success(adminContentCommandService.profile());
    }

    @PutMapping
    @RequireCap(Capability.MANAGE_HOSPITAL)
    public Result<HospitalProfileResponse> save(@Valid @RequestBody ProfileSaveRequest request) {
        return Result.success(adminContentCommandService.saveProfile(request));
    }
}
