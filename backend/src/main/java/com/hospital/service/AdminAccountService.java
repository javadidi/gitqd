package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminCreateRequest;
import com.hospital.dto.AdminResponse;
import com.hospital.dto.AdminUpdateRequest;
import com.hospital.dto.ChangePasswordRequest;
import com.hospital.entity.Admin;
import com.hospital.entity.Role;
import com.hospital.enums.OperatorType;
import com.hospital.exception.BizException;
import com.hospital.mapper.AdminMapper;
import com.hospital.mapper.RoleMapper;
import com.hospital.security.SecurityUtils;
import com.hospital.util.MaskUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员账号的读写与"改自己密码"（T28 卡片 762 行「管理员管理：CRUD」+ 766 行「修改密码」，
 * 出处 PRD 4.6.1 的 449–450 行与 4.6.5 的 465 行）。
 *
 * <h2>为什么本卡的审计不走 {@code @AuditLog} 切面（本卡唯一的结构性偏离）</h2>
 * 切面的 detail 是 {@code buildDetail(sig, args)}——把<b>方法入参整个序列化成 JSON</b>存进
 * {@code audit_log.detail}。这个设计对排班、退款、套餐都是对的（参数就是要留痕的东西），
 * 但本卡的三个方法里躺着凭据：
 * <ul>
 *   <li>{@code create(AdminCreateRequest)} 带<b>明文新密码</b>；</li>
 *   <li>{@code changePassword(ChangePasswordRequest)} 带<b>明文旧密码 + 明文新密码</b>。</li>
 * </ul>
 * 挂上切面就等于把口令抄送进一张任何人都能在后台"审计流水"页翻到的表
 * （{@code AuditTimeline} 组件是 T04-E 做的，读的正是这一列）。
 * 所以本卡三处写入全部改为在事务内显式调 {@link AuditLogService#write}，
 * detail 由代码逐个字段构造：密码字段一律不进，手机号只记"填了没有"，不记号码本身。
 *
 * <p>同事务这一条没有放松：写入发生在 {@code @Transactional} 方法体内，
 * 与 J8（{@code AuditLogTest.j8_auditRollsBackWithBusiness}）验的是同一件事。
 *
 * <h2>删除为什么有两把守卫</h2>
 * admin 表是后台<b>唯一</b>的登录主体来源：PRD 没有任何注册通道（4.1 只写"账号密码登录"），
 * 卡片 762 行的 CRUD 一旦开删除，删掉谁就可能永久删掉一类人。
 * 于是：不能删自己（4008，删完当前 token 还活着但账号再也登不回来），
 * 不能删 V2 建的四个演示账号（4009，它们是四个角色的入口，也是每一条集成测试的登录凭据）。
 * 规格里没有任何一句写"误删了怎么恢复"，所以只能拒绝并说清原因。
 */
@Service
public class AdminAccountService {

    /** V2__init_admin.sql:14-17 建的四个账号。见类注释「删除为什么有两把守卫」。 */
    static final Set<String> BUILT_IN_USERNAMES = Set.of("admin", "system", "doctor", "nurse");

    private final AdminMapper adminMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final CryptoService cryptoService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AdminAccountService(AdminMapper adminMapper,
                               RoleMapper roleMapper,
                               PasswordEncoder passwordEncoder,
                               CryptoService cryptoService,
                               AuditLogService auditLogService,
                               ObjectMapper objectMapper) {
        this.adminMapper = adminMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.cryptoService = cryptoService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 读
    // ============================================================

    /** 管理员列表（PRD 449 行）。角色名一次取全，不在循环里逐行查（T10 起的同一条纪律）。 */
    public List<AdminResponse> list() {
        List<Admin> rows = adminMapper.selectList(
                new LambdaQueryWrapper<Admin>().orderByAsc(Admin::getId));
        Map<Long, String> roleNames = roleNames();
        List<AdminResponse> result = new ArrayList<>();
        for (Admin row : rows) {
            result.add(toResponse(row, roleNames));
        }
        return result;
    }

    // ============================================================
    // 写
    // ============================================================

    /**
     * 新增管理员（PRD 450 行）。撞 {@code uk_username}（V1:383）时
     * {@code DuplicateKeyException} 原样抛出，由 controller 在事务外翻译成 4007——
     * 与 T08 就诊卡号、T11 排班唯一键同一条"前置查只是第一层，索引才是第二层"的纪律。
     */
    @Transactional
    public AdminResponse create(AdminCreateRequest request) {
        String username = request.getUsername().trim();
        Role role = requireRole(request.getRoleId());
        String phone = blankToNull(request.getPhone());

        Admin existing = adminMapper.selectByUsernameIncludingDeleted(username);
        if (existing != null && existing.getDeleted() == 0) {
            throw new BizException(ErrorCode.ADMIN_USERNAME_EXISTS);
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        if (existing != null) {
            // 软删过的名字可以原样重建（T08-G 的就诊人复活同一条路数）：
            // 名字被库级唯一索引占着，不做复活就只能让这个名字永久失踪。
            // 走 AdminMapper.reviveById 而不是 wrapper：@TableLogic 会给 wrapper 的 UPDATE
            // 也追加 deleted = 0，那条语句永远匹配不到 deleted = 1 的行（T11 排班复活踩过）。
            if (adminMapper.reviveById(existing.getId(), passwordHash, role.getId(),
                    cryptoService.encrypt(phone)) != 1) {
                throw new BizException(ErrorCode.ADMIN_USERNAME_EXISTS);
            }
            audit("CREATE_ADMIN", existing.getId(),
                    Map.of("username", username, "roleName", role.getName(),
                            "revived", true, "phoneFilled", phone != null));
            return toResponse(adminMapper.selectById(existing.getId()), roleNames());
        }

        Admin entity = new Admin();
        entity.setUsername(username);
        entity.setPasswordHash(passwordHash);
        entity.setRoleId(role.getId());
        entity.setPhone(cryptoService.encrypt(phone));
        adminMapper.insert(entity);

        audit("CREATE_ADMIN", entity.getId(),
                Map.of("username", username, "roleName", role.getName(),
                        "revived", false, "phoneFilled", phone != null));
        return toResponse(adminMapper.selectById(entity.getId()), roleNames());
    }

    /**
     * 编辑管理员（卡片 762 行 CRUD 里的那把 U；PRD 4.6.1 只写了列表和新增两句）。
     * 能改的只有角色与联系方式两列，理由见 {@link AdminUpdateRequest} 的类注释。
     */
    @Transactional
    public AdminResponse update(Long id, AdminUpdateRequest request) {
        Admin admin = requireAdmin(id);
        Role role = requireRole(request.getRoleId());
        String phone = blankToNull(request.getPhone());

        // 显式 SET（含 null）：updateById 会跳过 null 字段，"把联系方式抹掉"就静默失效（T27 同一条）。
        adminMapper.update(null, new LambdaUpdateWrapper<Admin>()
                .eq(Admin::getId, id)
                .set(Admin::getRoleId, role.getId())
                .set(Admin::getPhone, cryptoService.encrypt(phone)));

        audit("UPDATE_ADMIN", id,
                Map.of("username", admin.getUsername(), "roleName", role.getName(),
                        "phoneFilled", phone != null));
        return toResponse(adminMapper.selectById(id), roleNames());
    }

    @Transactional
    public void delete(Long id) {
        Admin admin = requireAdmin(id);
        if (BUILT_IN_USERNAMES.contains(admin.getUsername())) {
            throw new BizException(ErrorCode.ADMIN_IS_BUILT_IN);
        }
        Long currentId = SecurityUtils.currentAdminId();
        if (currentId != null && currentId.equals(id)) {
            throw new BizException(ErrorCode.ADMIN_CANNOT_DELETE_SELF);
        }
        adminMapper.deleteById(id);
        audit("DELETE_ADMIN", id, Map.of("username", admin.getUsername()));
    }

    /**
     * 修改<b>自身</b>密码（卡片 766 行 / PRD 4.6.5 的 465 行）。
     * 主语从 token 取（{@code SecurityUtils.currentAdminId()}，与 T26 的 reviewer_id 同一条取法），
     * 所以这一把端点不需要 EDIT_SETTINGS：护士和医生也该能改自己的口令。
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long adminId = SecurityUtils.currentAdminId();
        Admin admin = requireAdmin(adminId);

        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPasswordHash())) {
            // 与登录同一句话：不告诉调用方是"账号不对"还是"口令不对"。
            throw new BizException(ErrorCode.UNAUTHORIZED.getCode(), "原密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), admin.getPasswordHash())) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "新密码不能与原密码相同");
        }

        adminMapper.update(null, new LambdaUpdateWrapper<Admin>()
                .eq(Admin::getId, adminId)
                .set(Admin::getPasswordHash, passwordEncoder.encode(request.getNewPassword())));

        // detail 里没有任何口令字段——见类注释「为什么本卡的审计不走切面」。
        audit("CHANGE_PASSWORD", adminId, Map.of("username", admin.getUsername()));
    }

    // ============================================================
    // 内部
    // ============================================================

    private Admin requireAdmin(Long id) {
        Admin admin = id == null ? null : adminMapper.selectById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND.getCode(), "管理员账号不存在");
        }
        return admin;
    }

    private Role requireRole(Long roleId) {
        Role role = roleId == null ? null : roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private Map<Long, String> roleNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Role role : roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId))) {
            names.put(role.getId(), role.getName());
        }
        return names;
    }

    private AdminResponse toResponse(Admin row, Map<Long, String> roleNames) {
        AdminResponse response = new AdminResponse();
        response.setId(row.getId());
        response.setUsername(row.getUsername());
        response.setRoleId(row.getRoleId());
        response.setRoleName(roleNames.get(row.getRoleId()));
        response.setPhone(MaskUtil.maskPhone(cryptoService.decrypt(row.getPhone())));
        response.setCreatedAt(row.getCreatedAt());
        response.setBuiltIn(BUILT_IN_USERNAMES.contains(row.getUsername()));
        return response;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 审计写入。detail 由调用方逐字段构造，凭据类字段一律不进。 */
    private void audit(String action, Long targetId, Map<String, ?> detail) {
        Long operatorId = SecurityUtils.currentAdminId();
        String json;
        try {
            json = objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "审计详情序列化失败");
        }
        auditLogService.write(operatorId, OperatorType.ADMIN.name(),
                action, "admin", targetId, null, json);
    }
}
