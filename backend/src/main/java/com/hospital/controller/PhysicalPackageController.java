package com.hospital.controller;

import com.hospital.common.Result;
import com.hospital.dto.PhysicalPackageDetailResponse;
import com.hospital.dto.PhysicalPackageListItemResponse;
import com.hospital.service.PhysicalPackageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 患者侧的体检套餐接口（T22）：{@code GET /user/physical-packages} 与
 * {@code GET /user/physical-packages/{id}}。
 *
 * <p>两个端点，出处是 PRD §9.1 第 617 行的前两项：
 * {@code | 体检服务 | 套餐列表、套餐详情、创建体检预约、体检报告 |}。
 *
 * <p><b>没有筛选参数</b>：套餐表只有 {@code name/type_id/price_fen/target_audience/items}
 * 五列业务字段（V1:251-261），PRD 3.8 与 §6.1 第 522 行的六个页面名里也没有"按类型筛套餐"这一步
 * （卡片 638 行原话只有「展示可预约的体检套餐」）。附录 B 第 10 条对本卡 N/A——
 * 没有筛选可进 URL，硬造一个 {@code ?type=} 就得先造一张套餐类型表（它不存在）。
 *
 * <p>只读、无归属过滤（套餐是全院目录，理由见 {@link PhysicalPackageService}），
 * 所以没有审计、没有 {@code userId}；路径在 {@code /user/**} 下天然要求患者登录态，
 * {@code SecurityConfig} 一行未改。
 */
@RestController
@RequestMapping("/user/physical-packages")
public class PhysicalPackageController {

    private final PhysicalPackageService packageService;

    public PhysicalPackageController(PhysicalPackageService packageService) {
        this.packageService = packageService;
    }

    /** 可预约的体检套餐列表（卡片 638 行）。首版库里零行，页面按空态显示。 */
    @GetMapping
    public Result<List<PhysicalPackageListItemResponse>> list() {
        return Result.success(packageService.list());
    }

    /** 套餐详情（卡片 639 行）。不存在与软删同为 5001。 */
    @GetMapping("/{id}")
    public Result<PhysicalPackageDetailResponse> detail(@PathVariable Long id) {
        return Result.success(packageService.detail(id));
    }
}
