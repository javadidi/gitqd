package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.Result;
import com.hospital.dto.RoleResponse;
import com.hospital.dto.RoleSaveRequest;
import com.hospital.enums.Capability;
import com.hospital.service.AdminRoleService;
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
 * 管理端角色与权限配置（T28 卡片 763 行 / PRD 4.6.2 的 452–454 行）。
 *
 * <p>四把端点全部要 {@code EDIT_SETTINGS}，读也一样——见
 * {@code AdminAccountController} 的类注释，"谁能看见权限地图"这件事本身就是权限。
 *
 * <p>自定义角色建出来的是一个<b>只读角色</b>（能看见被勾选的页面，写端点一律 4001），
 * 因为写能力（{@code Capability}）从 T03 起就只活在代码里、不落库。
 * 这一条在 {@code AdminRoleService} 的类注释里有完整的推理，页面上也照着同一口径写。
 */
@RestController
@RequestMapping("/admin/roles")
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    public AdminRoleController(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    /** 角色列表（PRD 453 行）。带每个角色还挂着几个管理员，删除守卫要看的正是这个数。 */
    @GetMapping
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<List<RoleResponse>> list() {
        return Result.success(adminRoleService.list());
    }

    @PostMapping
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<RoleResponse> create(@Valid @RequestBody RoleSaveRequest request) {
        return Result.success(adminRoleService.create(request));
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<RoleResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody RoleSaveRequest request) {
        return Result.success(adminRoleService.update(id, request));
    }

    /** 删除。内置四角色 4011；还挂着管理员 4010。 */
    @DeleteMapping("/{id}")
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<Void> delete(@PathVariable Long id) {
        adminRoleService.delete(id);
        return Result.success(null);
    }
}
