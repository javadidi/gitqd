package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.DepartmentDetailResponse;
import com.hospital.dto.DepartmentResponse;
import com.hospital.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序端科室查询（T10）。
 *
 * <p>只有两个 GET，没有 POST/PUT/DELETE：DoD 是「科室/医生<b>查询</b>通」，
 * 而科室的新增/编辑/删除属管理后台「医院管理」（PRD §4.5.2 行 401-404、
 * §9.2 行 632「医生/科室/套餐… CRUD」），在任务卡里是 T27 的活。
 *
 * <p>路径挂 {@code /user/**}，自动继承 SecurityConfig 的
 * {@code .requestMatchers("/user/**").hasRole(LoginPatient.ROLE)}：
 * 员工 token 打进来 403、匿名 401，本卡不改任何安全配置（与 T08/T09 同）。
 *
 * <p><b>不注入 userId</b>：科室是全院共享目录，department 表没有 user_id 列，
 * 没有归属可校验（附录 B 第 806 条在本卡 N/A，判定见 CatalogService 类注释）。
 */
@RestController
@RequestMapping("/user/departments")
public class DepartmentController {

    private final CatalogService catalogService;

    public DepartmentController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** 科室列表；keyword 选填，出处 PRD 76 行「支持搜索」，只匹配科室名称 */
    @GetMapping
    public Result<List<DepartmentResponse>> list(@RequestParam(required = false) String keyword) {
        return Result.success(catalogService.listDepartments(keyword));
    }

    /** 科室详情：科室自身字段 + 该科室下所有医生（PRD 77 行） */
    @GetMapping("/{id}")
    public Result<DepartmentDetailResponse> detail(@PathVariable Long id) {
        return Result.success(catalogService.departmentDetail(id));
    }
}
