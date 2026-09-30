package com.hospital.controller;

import com.hospital.annotation.RequireCap;
import com.hospital.common.ErrorCode;
import com.hospital.common.Result;
import com.hospital.dto.AdminCreateRequest;
import com.hospital.dto.AdminResponse;
import com.hospital.dto.AdminUpdateRequest;
import com.hospital.exception.BizException;
import com.hospital.service.AdminAccountService;
import com.hospital.enums.Capability;
import jakarta.validation.Valid;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
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
 * 管理端管理员账号（T28 卡片 762 行 / PRD 4.6.1）。
 *
 * <h2>本卡与 T25–T27 唯一的一处口径差别：读端点也要能力</h2>
 * 前四张后台卡的读端点全部不挂 {@code @RequireCap}（只有写才拦），本卡把四把端点全拦在
 * {@code EDIT_SETTINGS} 后面。理由不是"更安全"这种口号，而是一条具体的泄漏：
 * {@code GET /admin/admins} 吐的是<b>登录主体清单</b>——用户名、角色归属、联系方式。
 * 护士/医生拿到这份名单就知道谁管什么、怎么联系，而这份数据对他们的日常工作没有任何用处
 * （PRD 31–40 行的角色表里，四类后台用户没有一类的工作对象是"其他管理员"）。
 * T27 之所以能让读全开，是因为那些行是科室、医生、套餐——本来就是给患者看的内容。
 *
 * <p>代价要说清并在前端配合：V2 给 nurse 的模块列表里含 {@code system}，
 * 所以护士的侧边栏会出现"系统设置"分组。前端因此对这一组按能力裁剪（见
 * {@code admin/src/components/layout/nav.ts}），但直接敲地址仍然进得来页面，
 * 然后在这里吃 4001——后端是唯一防线，前端只是把入口藏好，这与 T27 的口径一致。
 *
 * <p>患者 token 打到这里是 HTTP 403 + body 4001（{@code SecurityConfig} 的类别角色 STAFF 挡的）。
 */
@RestController
@RequestMapping("/admin/admins")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<List<AdminResponse>> list() {
        return Result.success(adminAccountService.list());
    }

    /**
     * 新增管理员（PRD 450 行）。
     *
     * <p>撞 {@code uk_username}（V1:383）时翻译成 4007。catch 放在 controller 而不是 service，
     * 与 {@code ScheduleController} 同一条理由：service 的写方法在事务内，
     * 事务里 catch DAO 异常会在提交时炸成查不出原因的 500。
     */
    @PostMapping
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<AdminResponse> create(@Valid @RequestBody AdminCreateRequest request) {
        try {
            return Result.success(adminAccountService.create(request));
        } catch (DuplicateKeyException | ConcurrencyFailureException e) {
            throw new BizException(ErrorCode.ADMIN_USERNAME_EXISTS);
        }
    }

    @PutMapping("/{id}")
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<AdminResponse> update(@PathVariable Long id,
                                        @Valid @RequestBody AdminUpdateRequest request) {
        return Result.success(adminAccountService.update(id, request));
    }

    /** 删除（软删）。两道守卫：不能删自己（4008）、不能删 V2 的四个内置账号（4009）。 */
    @DeleteMapping("/{id}")
    @RequireCap(Capability.EDIT_SETTINGS)
    public Result<Void> delete(@PathVariable Long id) {
        adminAccountService.delete(id);
        return Result.success(null);
    }
}
