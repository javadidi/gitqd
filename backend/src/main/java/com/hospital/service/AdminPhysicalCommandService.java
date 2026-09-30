package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminPackageTypeResponse;
import com.hospital.dto.AdminPhysicalItemResponse;
import com.hospital.dto.AdminPhysicalPackageResponse;
import com.hospital.dto.PackageTypeSaveRequest;
import com.hospital.dto.PhysicalItemSaveRequest;
import com.hospital.dto.PhysicalPackageSaveRequest;
import com.hospital.entity.PackageType;
import com.hospital.entity.PhysicalAppointment;
import com.hospital.entity.PhysicalItem;
import com.hospital.entity.PhysicalPackage;
import com.hospital.exception.BizException;
import com.hospital.mapper.PackageTypeMapper;
import com.hospital.mapper.PhysicalAppointmentMapper;
import com.hospital.mapper.PhysicalItemMapper;
import com.hospital.mapper.PhysicalPackageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端体检三兄弟的写入（T27 卡片 738/739/740 行 / PRD §4.5.3–§4.5.5 的 406–417 行）：
 * 套餐、项目、套餐类型。
 *
 * <h2>三类删除守卫的差别是算过的，不是随手加的</h2>
 * <ul>
 *   <li><b>套餐</b>：{@code physical_appointment.package_id} 是 NOT NULL（V1:283），
 *       还有未取消的预约就删套餐 = 让患者的体检记录指着一个不存在的套餐 → {@code 2010}；</li>
 *   <li><b>项目</b>：没有任何表引用它（套餐存的是名字快照），删了不弄坏任何已存单据 → <b>不加守卫</b>；</li>
 *   <li><b>类型</b>：{@code physical_package.type_id} 可空（V1:254），
 *       但 PRD 416–417 行只给了「类型列表」与「新增套餐类型」两句，<b>规格连删除都没要</b>，
 *       所以这一类只做列表 + 新增 + 编辑，不开删除这把端点。</li>
 * </ul>
 *
 * <h2>为什么类型没有删除而不是"顺手补全 CRUD"</h2>
 * 卡片 740 行写的是「套餐类型管理：CRUD」，PRD 那一节只列了两条（列表、新增）。
 * 按 T08-G 的教训，卡片的 CRUD 不能一律砍成动词清单——但那条教训讲的是"别把 DoD 里明写的砍掉"，
 * 而这里是反方向：规格里没有"删"这句话，而删一个正在被套餐引用的类型会让列表出现空类型名。
 * 两难之下取"规格写了什么就做什么"，并把这一条记进有意未做，不悄悄发明也不悄悄省略。
 */
@Service
public class AdminPhysicalCommandService {

    /** V1:284 的四个取值里"还活着"的两个。 */
    private static final List<String> LIVE_PHYSICAL_STATUSES = List.of("PENDING", "CONFIRMED");

    private final PhysicalPackageMapper packageMapper;
    private final PhysicalItemMapper itemMapper;
    private final PackageTypeMapper typeMapper;
    private final PhysicalAppointmentMapper appointmentMapper;
    private final ObjectMapper objectMapper;

    public AdminPhysicalCommandService(PhysicalPackageMapper packageMapper,
                                       PhysicalItemMapper itemMapper,
                                       PackageTypeMapper typeMapper,
                                       PhysicalAppointmentMapper appointmentMapper,
                                       ObjectMapper objectMapper) {
        this.packageMapper = packageMapper;
        this.itemMapper = itemMapper;
        this.typeMapper = typeMapper;
        this.appointmentMapper = appointmentMapper;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 套餐
    // ============================================================

    public List<AdminPhysicalPackageResponse> listPackages() {
        List<PhysicalPackage> rows = packageMapper.selectList(
                new LambdaQueryWrapper<PhysicalPackage>().orderByDesc(PhysicalPackage::getId));
        // 类型名一次取全，不在循环里逐行查（T10 的 doctorNamesOf 同一条纪律）
        Map<Long, String> typeNames = typeNames();
        List<AdminPhysicalPackageResponse> result = new ArrayList<>();
        for (PhysicalPackage row : rows) {
            result.add(toPackageResponse(row, typeNames));
        }
        return result;
    }

    public AdminPhysicalPackageResponse packageDetail(Long id) {
        return toPackageResponse(requirePackage(id), typeNames());
    }

    @AuditLog(action = "CREATE_PHYSICAL_PACKAGE", targetType = "physical_package")
    @Transactional
    public AdminPhysicalPackageResponse createPackage(PhysicalPackageSaveRequest request) {
        requireTypeWhenPresent(request.getTypeId());
        PhysicalPackage entity = new PhysicalPackage();
        entity.setName(request.getName().trim());
        entity.setTypeId(request.getTypeId());
        entity.setPriceFen(request.getPriceFen());
        entity.setTargetAudience(blankToNull(request.getTargetAudience()));
        entity.setItems(writeItems(request.getItems()));
        packageMapper.insert(entity);
        return toPackageResponse(packageMapper.selectById(entity.getId()), typeNames());
    }

    @AuditLog(action = "UPDATE_PHYSICAL_PACKAGE", targetType = "physical_package")
    @Transactional
    public AdminPhysicalPackageResponse updatePackage(@AuditTarget Long id,
                                                      PhysicalPackageSaveRequest request) {
        requirePackage(id);
        requireTypeWhenPresent(request.getTypeId());
        // 显式 SET（含 null）：updateById 会跳过 null 字段，
        // "把适用人群抹掉/把类型改回未分类"这类编辑就会静默不生效。
        packageMapper.update(null, new LambdaUpdateWrapper<PhysicalPackage>()
                .eq(PhysicalPackage::getId, id)
                .set(PhysicalPackage::getName, request.getName().trim())
                .set(PhysicalPackage::getTypeId, request.getTypeId())
                .set(PhysicalPackage::getPriceFen, request.getPriceFen())
                .set(PhysicalPackage::getTargetAudience, blankToNull(request.getTargetAudience()))
                .set(PhysicalPackage::getItems, writeItems(request.getItems())));
        return toPackageResponse(packageMapper.selectById(id), typeNames());
    }

    @AuditLog(action = "DELETE_PHYSICAL_PACKAGE", targetType = "physical_package")
    @Transactional
    public void deletePackage(@AuditTarget Long id) {
        requirePackage(id);
        long live = appointmentMapper.selectCount(new LambdaQueryWrapper<PhysicalAppointment>()
                .eq(PhysicalAppointment::getPackageId, id)
                .in(PhysicalAppointment::getStatus, LIVE_PHYSICAL_STATUSES));
        if (live > 0) {
            throw new BizException(ErrorCode.PHYSICAL_PACKAGE_HAS_APPOINTMENTS);
        }
        packageMapper.deleteById(id);
    }

    // ============================================================
    // 项目
    // ============================================================

    public List<AdminPhysicalItemResponse> listItems() {
        List<AdminPhysicalItemResponse> result = new ArrayList<>();
        for (PhysicalItem row : itemMapper.selectList(
                new LambdaQueryWrapper<PhysicalItem>().orderByAsc(PhysicalItem::getCategory)
                        .orderByAsc(PhysicalItem::getId))) {
            result.add(toItemResponse(row));
        }
        return result;
    }

    public AdminPhysicalItemResponse itemDetail(Long id) {
        return toItemResponse(requireItem(id));
    }

    @AuditLog(action = "CREATE_PHYSICAL_ITEM", targetType = "physical_item")
    @Transactional
    public AdminPhysicalItemResponse createItem(PhysicalItemSaveRequest request) {
        PhysicalItem entity = new PhysicalItem();
        entity.setName(request.getName().trim());
        entity.setCategory(blankToNull(request.getCategory()));
        entity.setPriceFen(request.getPriceFen());
        entity.setDescription(blankToNull(request.getDescription()));
        itemMapper.insert(entity);
        return toItemResponse(itemMapper.selectById(entity.getId()));
    }

    @AuditLog(action = "UPDATE_PHYSICAL_ITEM", targetType = "physical_item")
    @Transactional
    public AdminPhysicalItemResponse updateItem(@AuditTarget Long id, PhysicalItemSaveRequest request) {
        requireItem(id);
        itemMapper.update(null, new LambdaUpdateWrapper<PhysicalItem>()
                .eq(PhysicalItem::getId, id)
                .set(PhysicalItem::getName, request.getName().trim())
                .set(PhysicalItem::getCategory, blankToNull(request.getCategory()))
                .set(PhysicalItem::getPriceFen, request.getPriceFen())
                .set(PhysicalItem::getDescription, blankToNull(request.getDescription())));
        return toItemResponse(itemMapper.selectById(id));
    }

    /**
     * 删除项目。<b>不校验"有没有套餐在用"</b>：套餐的 items 存的是名字快照
     * （{@link PhysicalPackageSaveRequest} 的类注释），没有外键可查，
     * 真要判断只能对 JSON 列做字符串匹配——那是一个规格没要也不可信的依据。
     * 所以这里就是软删，删完已存套餐照旧显示它当初抄下的那一份。
     */
    @AuditLog(action = "DELETE_PHYSICAL_ITEM", targetType = "physical_item")
    @Transactional
    public void deleteItem(@AuditTarget Long id) {
        requireItem(id);
        itemMapper.deleteById(id);
    }

    // ============================================================
    // 类型（列表 + 新增 + 编辑，没有删除——见类注释）
    // ============================================================

    public List<AdminPackageTypeResponse> listTypes() {
        List<AdminPackageTypeResponse> result = new ArrayList<>();
        for (PackageType row : typeMapper.selectList(
                new LambdaQueryWrapper<PackageType>().orderByAsc(PackageType::getId))) {
            result.add(toTypeResponse(row));
        }
        return result;
    }

    @AuditLog(action = "CREATE_PACKAGE_TYPE", targetType = "package_type")
    @Transactional
    public AdminPackageTypeResponse createType(PackageTypeSaveRequest request) {
        PackageType entity = new PackageType();
        entity.setName(request.getName().trim());
        typeMapper.insert(entity);
        return toTypeResponse(typeMapper.selectById(entity.getId()));
    }

    @AuditLog(action = "UPDATE_PACKAGE_TYPE", targetType = "package_type")
    @Transactional
    public AdminPackageTypeResponse updateType(@AuditTarget Long id, PackageTypeSaveRequest request) {
        requireType(id);
        PackageType entity = new PackageType();
        entity.setId(id);
        entity.setName(request.getName().trim());
        typeMapper.updateById(entity);
        return toTypeResponse(typeMapper.selectById(id));
    }

    // ============================================================
    // 内部
    // ============================================================

    /**
     * 写 items JSON 列。用 ObjectMapper 而不是拼字符串（与 T12 的 {@code itemsJson} 同一条理由：
     * 项目名称里可能有引号，拼字符串就是一条语法错误的 JSON 写进 NOT NULL 列）。
     */
    private String writeItems(List<PhysicalPackageSaveRequest.PackageItemInput> items) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (PhysicalPackageSaveRequest.PackageItemInput item : items) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", item.getName().trim());
            row.put("priceFen", item.getPriceFen());
            payload.add(row);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
    }

    private PhysicalPackage requirePackage(Long id) {
        PhysicalPackage row = id == null ? null : packageMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return row;
    }

    private PhysicalItem requireItem(Long id) {
        PhysicalItem row = id == null ? null : itemMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return row;
    }

    private PackageType requireType(Long id) {
        PackageType row = id == null ? null : typeMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return row;
    }

    private void requireTypeWhenPresent(Long typeId) {
        if (typeId != null) {
            requireType(typeId);
        }
    }

    private Map<Long, String> typeNames() {
        Map<Long, String> names = new HashMap<>();
        for (PackageType row : typeMapper.selectList(null)) {
            names.put(row.getId(), row.getName());
        }
        return names;
    }

    private AdminPhysicalPackageResponse toPackageResponse(PhysicalPackage row, Map<Long, String> typeNames) {
        AdminPhysicalPackageResponse response = new AdminPhysicalPackageResponse();
        response.setId(row.getId());
        response.setName(row.getName());
        response.setTypeId(row.getTypeId());
        response.setTypeName(row.getTypeId() == null ? null : typeNames.get(row.getTypeId()));
        response.setPriceFen(row.getPriceFen());
        response.setTargetAudience(row.getTargetAudience());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        response.setItems(readItems(row.getItems()));
        return response;
    }

    /**
     * 读 items：认得 {@code [{"name":…,"priceFen":…}]}（本卡写的形状），
     * 其余形态（T22 之前若有人手工插过别的形状）按"取不到 name 就整项转字符串"容错——
     * 与 {@code miniprogram/utils/format.js} 的 reportItemsText 同一条兜底，
     * 目的是让一条脏历史行不会把整个套餐列表变成 500。
     */
    private List<AdminPhysicalPackageResponse.Item> readItems(String json) {
        List<AdminPhysicalPackageResponse.Item> items = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return items;
        }
        try {
            for (Object element : objectMapper.readValue(json, List.class)) {
                if (!(element instanceof java.util.Map<?, ?> map)) {
                    continue;
                }
                AdminPhysicalPackageResponse.Item item = new AdminPhysicalPackageResponse.Item();
                Object name = map.get("name");
                item.setName(name == null ? null : String.valueOf(name));
                Object price = map.get("priceFen");
                item.setPriceFen(price instanceof Number number ? number.longValue() : null);
                items.add(item);
            }
        } catch (JsonProcessingException e) {
            return List.of();
        }
        return items;
    }

    private AdminPhysicalItemResponse toItemResponse(PhysicalItem row) {
        AdminPhysicalItemResponse response = new AdminPhysicalItemResponse();
        response.setId(row.getId());
        response.setName(row.getName());
        response.setCategory(row.getCategory());
        response.setPriceFen(row.getPriceFen());
        response.setDescription(row.getDescription());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    private AdminPackageTypeResponse toTypeResponse(PackageType row) {
        AdminPackageTypeResponse response = new AdminPackageTypeResponse();
        response.setId(row.getId());
        response.setName(row.getName());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
