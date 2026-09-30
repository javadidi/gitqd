package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.RoleResponse;
import com.hospital.dto.RoleSaveRequest;
import com.hospital.entity.Admin;
import com.hospital.entity.Role;
import com.hospital.exception.BizException;
import com.hospital.mapper.AdminMapper;
import com.hospital.mapper.RoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色的读写与「权限配置」（T28 卡片 763 行 / PRD 4.6.2 的 453–454 行）。
 *
 * <h2>权限配置配的是哪一列，以及另一列为什么配不了</h2>
 * PRD 596 行「角色 | 角色ID、名称、<b>权限列表</b>」的落点是 {@code role.permissions}（V1:392 JSON），
 * 本卡让它成为真值：登录时由 {@code PermissionService.resolveModules} 读它（T28 之前只读代码里的静态表）。
 * 页面上管理员能配的因此是"<b>这一类人能看见哪几块</b>"（8 个模块键）。
 *
 * <p><b>写能力（{@code Capability}）不在这张表单里，本卡也不给它开配置口</b>：
 * 那套权限从 T03 起就只活在代码里（{@code PermissionService.ROLE_CAPS}），
 * V2 的 role 表没有 caps 列， Capability 枚举注释当时就把这件事写明了
 * （"能力列表不落库……所以加一个枚举不需要迁移文件"）。
 * 于是本卡的产物是一个<b>只读角色</b>：自定义角色能看见页面，
 * 但每一把写端点都会回 4001。这不是漏做——给它一个"勾选能力"的复选框
 * 需要新加一列、需要一个 caps 的持久化读取点、需要改 @RequireCap 的判定源，
 * 而规格里没有任何一句授权这一串；把它做一半（存进库却没人读）就是假功能。
 * 页面与类注释都按"只读角色"这条口径写，管理员不会以为勾了就能写。
 *
 * <h2>四个内置角色为什么整行锁死</h2>
 * 它们的<b>名字</b>就是 {@code ROLE_CAPS} 的键：改名不报错，只会让那一类人静默失去全部写权限，
 * 而界面上看不出任何异常；删除则会让 V2 的四个演示账号变成无主角色，
 * 顺带打掉每一条集成测试的登录凭据。规格里没有一句"误改了怎么恢复"，所以只开读（4011）。
 */
@Service
public class AdminRoleService {

    private final RoleMapper roleMapper;
    private final AdminMapper adminMapper;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public AdminRoleService(RoleMapper roleMapper,
                            AdminMapper adminMapper,
                            PermissionService permissionService,
                            ObjectMapper objectMapper) {
        this.roleMapper = roleMapper;
        this.adminMapper = adminMapper;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    /** 角色列表（PRD 453 行）。adminCount 一次算完，不在循环里逐行查。 */
    public List<RoleResponse> list() {
        List<Role> rows = roleMapper.selectList(
                new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
        Map<Long, Long> adminCounts = adminCountByRole();
        List<RoleResponse> result = new ArrayList<>();
        for (Role row : rows) {
            result.add(toResponse(row, adminCounts.getOrDefault(row.getId(), 0L)));
        }
        return result;
    }

    @AuditLog(action = "CREATE_ROLE", targetType = "role")
    @Transactional
    public RoleResponse create(RoleSaveRequest request) {
        String name = requireFreeName(request.getName(), null);
        List<String> modules = requireKnownModules(request.getModules());

        Role entity = new Role();
        entity.setName(name);
        entity.setPermissions(writeModules(modules));
        roleMapper.insert(entity);

        return toResponse(roleMapper.selectById(entity.getId()), 0L);
    }

    /**
     * 编辑自定义角色。改名是允许的（未被内置表键引用的名字改了不影响任何判定），
     * 但改完仍要保证没有第二行叫同一个名字。
     */
    @AuditLog(action = "UPDATE_ROLE", targetType = "role")
    @Transactional
    public RoleResponse update(@AuditTarget Long id, RoleSaveRequest request) {
        Role role = requireRole(id);
        if (PermissionService.isCoreRole(role.getName())) {
            throw new BizException(ErrorCode.ROLE_IS_BUILT_IN);
        }
        String name = requireFreeName(request.getName(), id);
        List<String> modules = requireKnownModules(request.getModules());

        roleMapper.update(null, new LambdaUpdateWrapper<Role>()
                .eq(Role::getId, id)
                .set(Role::getName, name)
                .set(Role::getPermissions, writeModules(modules)));

        return toResponse(roleMapper.selectById(id), adminCountByRole().getOrDefault(id, 0L));
    }

    /** 删除角色。两把守卫：内置角色不能删（4011），还挂着人的不能删（4010）。 */
    @AuditLog(action = "DELETE_ROLE", targetType = "role")
    @Transactional
    public void delete(@AuditTarget Long id) {
        Role role = requireRole(id);
        if (PermissionService.isCoreRole(role.getName())) {
            throw new BizException(ErrorCode.ROLE_IS_BUILT_IN);
        }
        Long inUse = adminMapper.selectCount(
                new LambdaQueryWrapper<Admin>().eq(Admin::getRoleId, id));
        if (inUse != null && inUse > 0) {
            throw new BizException(ErrorCode.ROLE_IN_USE);
        }
        roleMapper.deleteById(id);
    }

    // ============================================================
    // 内部
    // ============================================================

    private Role requireRole(Long id) {
        Role role = id == null ? null : roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    /**
     * 名字查重。role.name 没有唯一索引（V1:389-396 里只有主键），
     * 而 {@code PermissionService} 的两张表和登录解析都按名字取，重名的两行会永远读成同一份权限，
     * 所以这一层没有索引兜底，只能显式查——这也是本卡不给角色开"重名并发"第二层的原因：
     * 没有唯一索引可用，加一列唯一约束又不是本卡的权限范围（属 V2 的地基）。
     */
    private String requireFreeName(String rawName, Long excludeId) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "角色名称不能为空");
        }
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>().eq(Role::getName, name);
        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }
        Long exists = roleMapper.selectCount(wrapper);
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS.getCode(), "角色名称已存在");
        }
        return name;
    }

    /** 只收 ALL_MODULES 里的 8 个键，未知键丢弃；全空则拒绝（一个都看不见的角色没有存在意义）。 */
    private List<String> requireKnownModules(List<String> modules) {
        List<String> known = new ArrayList<>();
        if (modules != null) {
            for (String item : modules) {
                if (item != null && PermissionService.ALL_MODULES.contains(item) && !known.contains(item)) {
                    known.add(item);
                }
            }
        }
        if (known.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "至少要勾选一个可见模块，取值只能是 " + PermissionService.ALL_MODULES);
        }
        return known;
    }

    private String writeModules(List<String> modules) {
        try {
            return objectMapper.writeValueAsString(modules);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "权限列表序列化失败");
        }
    }

    private Map<Long, Long> adminCountByRole() {
        // 只算活着的账号：软删账号不再登录，不构成"这个角色还在用"
        List<Admin> admins = adminMapper.selectList(
                new LambdaQueryWrapper<Admin>().select(Admin::getRoleId));
        Map<Long, Long> counts = new HashMap<>();
        for (Admin admin : admins) {
            if (admin.getRoleId() != null) {
                counts.merge(admin.getRoleId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private RoleResponse toResponse(Role row, Long adminCount) {
        RoleResponse response = new RoleResponse();
        response.setId(row.getId());
        response.setName(row.getName());
        response.setCore(PermissionService.isCoreRole(row.getName()));
        response.setWildcard(row.getPermissions() != null && row.getPermissions().contains("*"));
        response.setModules(permissionService.parseModules(row.getPermissions()));
        response.setAdminCount(adminCount);
        return response;
    }
}
