package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.PhysicalPackageDetailResponse;
import com.hospital.dto.PhysicalPackageListItemResponse;
import com.hospital.entity.PhysicalPackage;
import com.hospital.exception.BizException;
import com.hospital.mapper.PhysicalPackageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 体检套餐只读查询（T22 卡片 638–639 行：套餐列表 / 套餐详情）。
 *
 * <p>纯读，与 T10 科室医生目录、T17 报告、T18 病历同一个分工：<b>故意不加 {@code @Transactional}</b>。
 *
 * <h2>这是患者侧第一张"目录表"性质的读端点：不带归属</h2>
 * 套餐不是某个人的数据，是全院可预约的商品目录，所以与 {@code /user/departments}、
 * {@code /user/doctors} 一样<b>不做 {@code user_id} 归属过滤</b>（附录 B 第 8 条针对的是
 * "属于某人的数据"）。它仍然在 {@code /user/**} 下，因此要求患者登录态——
 * 游客不该翻医院的价目表，这一条与 T10 同口径。
 *
 * <h2>结构性事实：{@code physical_package} 首版零行，而它<strong>有明确的生产者</strong></h2>
 * {@code seed.sql} 里三张体检表（{@code physical_package}/{@code physical_item}/
 * {@code physical_appointment}）<b>一行都没有</b>（逐字 grep 过，无匹配；库里实测
 * {@code pkg 0 / item 0 / apt 0}）。但与 T16 的 {@code queue_status}、T17 的 {@code report}、
 * T18 的 {@code medical_record} 那三张"没有任何一张卡负责写"的表不同，
 * 套餐的生产者是<b>点名存在的</b>：PRD §4.5.3「体检套餐管理 — 套餐列表 / 新增套餐信息」（406–408 行）
 * 承接在卡片 <b>T27</b>（{@code App.tsx:73} 的占位路由已经写着 {@code card="T27"}）。
 *
 * <p>所以本卡的处理是：<b>接口照做、页面照做、列表为空时页面老实显示空态</b>，
 * 不往 {@code seed.sql} 里塞几个"入职体检/全面体检"来让页面好看——
 * 那等于替 T27 编它要管理的数据，而且验收时看起来像功能已通。
 * 取证因此分两层：接口的形状与守卫用真接口产生的预约行来验，
 * 套餐行本身用<strong>人工取证探针</strong>裸插（与 T16/T17/T18 同一口径，收尾删净）。
 */
@Service
public class PhysicalPackageService {

    private final PhysicalPackageMapper packageMapper;
    private final ObjectMapper objectMapper;

    public PhysicalPackageService(PhysicalPackageMapper packageMapper, ObjectMapper objectMapper) {
        this.packageMapper = packageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 全部可预约套餐。排序取 {@code id} 升序：表里没有 {@code sort_order} 列
     * （{@code department} 有、套餐没有），也没有别的规格依据，
     * 所以按建号顺序回——不自造"按价格排""按热度排"。
     */
    public List<PhysicalPackageListItemResponse> list() {
        List<PhysicalPackage> rows = packageMapper.selectList(
                new LambdaQueryWrapper<PhysicalPackage>().orderByAsc(PhysicalPackage::getId));
        return rows.stream().map(row -> {
            PhysicalPackageListItemResponse item = new PhysicalPackageListItemResponse();
            item.setPackageId(row.getId());
            item.setName(row.getName());
            item.setPriceFen(row.getPriceFen());
            item.setTargetAudience(row.getTargetAudience());
            return item;
        }).toList();
    }

    /** 套餐详情（卡片 639 行）。不存在与软删同为 5001。 */
    public PhysicalPackageDetailResponse detail(Long packageId) {
        PhysicalPackage row = packageMapper.selectById(packageId);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        PhysicalPackageDetailResponse detail = new PhysicalPackageDetailResponse();
        detail.setPackageId(row.getId());
        detail.setName(row.getName());
        detail.setPriceFen(row.getPriceFen());
        detail.setTargetAudience(row.getTargetAudience());
        detail.setItems(parseItems(row.getItems()));
        return detail;
    }

    /**
     * {@code physical_package.items} 是 JSON 列（V1:257），形状没有任何规格出处
     * （理由见 {@link PhysicalPackageDetailResponse} 类注释）。所以这里只做
     * "合法 JSON → JsonNode" 这一步转换，不挑字段、不改键名，原样透传。
     *
     * <p>解析失败按理不可能发生（MySQL 的 JSON 列自己会拒非法 JSON），但仍必须处理：
     * 按 {@code 5001} 报出去，与 T15/T17 的 {@code parseItems} 同一个选择——
     * 一条读不出来的套餐不该变成整页 500。
     */
    private JsonNode parseItems(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
    }
}
