package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.AdminTitleResponse;
import com.hospital.dto.TitleSaveRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminTitleService;
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
 * 管理端职称（T28 卡片 764 行 / PRD 4.6.3 的 456–458 行）。
 *
 * <p><b>只有三把端点：列表、新增、编辑，没有 {@code @DeleteMapping}</b>——
 * 与 T27 的套餐类型（卡片 740 行同样写 CRUD、PRD 416–417 行同样只给两句）同一条取舍，
 * 完整理由在 {@code AdminTitleService} 的类注释。这里补一句本卡独有的：
 * 职称这张表<b>已经有生产者也有消费者</b>（T27 的医生表单读 {@code /admin/doctors/options}），
 * 所以"删一行会发生什么"是有真实后果的，而那后果规格里没写。
 *
 * <p>列表这一把读端点也挂 {@code EDIT_SETTINGS}，与医生表单用的 options 是两把不同的口子：
 * {@code /admin/doctors/options} 只回 id + name + sort_order（T27 为了表单开的），
 * 这里回的是带"几位医生在用"的完整管理视图。表单能用 ≠ 管理页能给所有人看。
 */
@RestController
@RequestMapping("/admin/titles")
public class AdminTitleController {

    private final AdminTitleService adminTitleService;

    public AdminTitleController(AdminTitleService adminTitleService) {
        this.adminTitleService = adminTitleService;
    }

    @GetMapping
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<List<AdminTitleResponse>> list() {
        return Result.success(adminTitleService.list());
    }

    @PostMapping
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<AdminTitleResponse> create(@Valid @RequestBody TitleSaveRequest request) {
        return Result.success(adminTitleService.create(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<AdminTitleResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody TitleSaveRequest request) {
        return Result.success(adminTitleService.update(id, request));
    }
}
