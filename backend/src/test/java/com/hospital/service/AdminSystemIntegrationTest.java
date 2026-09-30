package com.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
import com.hospital.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端系统设置与数据看板（T28）：J61 管理员管理 → CRUD 通、J62 数据看板 → 数据正确。
 *
 * <h2>本卡把"权限"这件事从代码搬到数据里，所以测试必须走一次真登录</h2>
 * T03 起的四个角色、模块、能力全部硬编在 {@code PermissionService} 的静态表里，
 * {@code SecurityConfig} 又把可进后台的角色名列成那四个。卡片 763 行「角色管理：CRUD + 权限配置」
 * 一落地，这两处都会自相矛盾：后台新建的角色既进不了门（403），也没有模块（侧边栏空白）。
 * 所以本类里 {@link #clerkWithCustomRoleCanReadDashboardButNotWrite} 不满足于"接口回 200"，
 * 它做完一整条链：后台建角色 → 建用它登录的账号 → 读 Redis 里的验证码 → 真的 POST /auth/login
 * → 拿回来的 token 里 modules 必须等于页面上勾的那两项 → 这把 token 能读看板、写职称要 4001。
 * 中间任何一环回到硬编表上，这条测试就红。
 *
 * <h2>指标的"数据正确"怎么证</h2>
 * J62 只有一句「数据看板 → 数据正确」。正确没有绝对值可取（seed 的缴费/充值行落在哪一天，
 * 取决于这个库是哪天 seed 的），所以本卡用三件事合起来钉：
 * <ul>
 *   <li><b>同窗口对账</b>：每个数字与一条按类注释口径独立写出的 SQL 相等；</li>
 *   <li><b>窗口外必须有数据而且没被算进来</b>：探针刻意再插一行"昨天"的单，
 *       断言看板值<b>严格小于</b>不带日期条件的总额——这条是防止"整表 SUM"这种看起来也对的实现蒙过去；</li>
 *   <li><b>护士视角金额为 null 而不是 0</b>：读原始 JSON 断言键还在、值是 null
 *       （裁剪层写的是 null；如果哪天字段改名绕过裁剪，这里会红）。</li>
 * </ul>
 *
 * <h2>探针与清理</h2>
 * 探针走真实 API（写路径本身是被测对象），日期用"今天/昨天"而不是远期年份——
 * 本卡的指标窗口就是 CURDATE()，落在 +N 年的行永远不会进窗口，测不出边界。
 * 清理一律裸 SQL 物理删（含软删行），并把 admin/role 两张 V2 的表也纳入行数快照：
 * 这两张表 seed.sql 不管（它的第 1 条注释就写"不碰 role/admin"），漏一行没人会发现。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class AdminSystemIntegrationTest {

    private static final String ROLE_NAME = "T28 探针角色";
    private static final String ROLE_IN_USE_NAME = "T28 探针在用角色";
    private static final String ADMIN_USER = "t28_probe_admin";
    private static final String CLERK_USER = "t28_probe_clerk";
    private static final String OTHER_USER = "t28_probe_other";
    private static final String SELF_DELETE_USER = "t28_probe_self";
    private static final String PASSWORD = "probe123456";
    private static final String TITLE_NAME = "T28 探针职称";
    private static final String DOCTOR_NAME = "T28 探针医生";
    private static final String ANNOUNCEMENT_TITLE = "T28 探针停诊公告";
    private static final String ANNOUNCEMENT_BODY = "T28 探针正文：消化内科门诊 10 月 1 日停诊一天。";
    private static final String FEEDBACK_CONTENT = "T28 探针反馈内容";

    /** 探针流水的金额（分）。取互不相同的数字，任何两处串了都会立刻红。 */
    private static final long CONSUME_TODAY_FEN = 12345L;
    private static final long RECHARGE_OUTPATIENT_FEN = 5000L;
    private static final long RECHARGE_INPATIENT_FEN = 7000L;
    private static final long CONSUME_YESTERDAY_FEN = 999999L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    private long auditIdFloor;
    private String adminToken;
    private String nurseToken;
    private String doctorToken;
    private String patientToken;
    private long customRoleId;
    private final List<Long> userIds = new ArrayList<>();
    private Map<String, Long> countsBefore;

    @BeforeEach
    void setUp() throws Exception {
        countsBefore = snapshotCounts();
        Long maxAuditId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM audit_log", Long.class);
        auditIdFloor = maxAuditId == null ? 0L : maxAuditId;
        adminToken = staffToken(1L, "admin");
        nurseToken = staffToken(4L, "nurse");
        doctorToken = staffToken(3L, "doctor");
        patientToken = newPatientToken("j61");
    }

    @AfterEach
    void cleanupAndAssertSeedUntouched() {
        jdbcTemplate.update("DELETE FROM feedback WHERE content = ?", FEEDBACK_CONTENT);
        jdbcTemplate.update("DELETE FROM refund_record WHERE order_no LIKE 'T28RF%'");
        jdbcTemplate.update("DELETE FROM payment_record WHERE order_no LIKE 'T28PY%'");
        jdbcTemplate.update("DELETE FROM recharge_record WHERE order_no LIKE 'T28RC%'");
        jdbcTemplate.update("DELETE FROM appointment WHERE order_no LIKE 'T28AP%'");
        jdbcTemplate.update("DELETE FROM announcement WHERE title LIKE 'T28 %'");
        jdbcTemplate.update("DELETE FROM doctor WHERE name LIKE 'T28 %'");
        jdbcTemplate.update("DELETE FROM title WHERE name LIKE 'T28 %'");
        jdbcTemplate.update("DELETE FROM `admin` WHERE username LIKE 't28_%'");
        jdbcTemplate.update("DELETE FROM role WHERE name LIKE 'T28 %'");
        jdbcTemplate.update("DELETE FROM patient WHERE user_id IN (" + placeholders() + ")", userIdArgs());
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM audit_log WHERE id > ?", auditIdFloor);
        assertEquals(countsBefore, snapshotCounts(),
                "探针行必须清干净，且 V2/seed 的行一张都不能少（admin 与 role 也在快照里）");
    }

    // ============================================================
    // J61 管理员管理 → CRUD 通
    // ============================================================

    @Test
    void j61_adminCreateReadUpdateDeleteRoundTrip() throws Exception {
        long roleId = createRole(ROLE_NAME, List.of("dashboard", "appointment"));

        Map<?, ?> created = expectData(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", ADMIN_USER,
                        "password", PASSWORD,
                        "roleId", roleId,
                        "phone", "13900001111")))));
        long adminId = number(created.get("id"));
        assertEquals(ADMIN_USER, created.get("username"));
        assertEquals(ROLE_NAME, created.get("roleName"));
        assertEquals("139****1111", created.get("phone"), "列表回的是脱敏值");
        assertEquals(Boolean.FALSE, created.get("builtIn"));

        // 库里必须是密文：附录 B 第 812 行「身份证/手机号是否加密存储」在 admin 列上的兑现点
        String stored = jdbcTemplate.queryForObject(
                "SELECT phone FROM `admin` WHERE id = ?", String.class, adminId);
        assertNotNull(stored);
        assertFalse(stored.contains("13900001111"), "手机号必须加密存储，实际库里是：" + stored);
        assertFalse(stored.startsWith("SEED_ENC"), "这一列是本卡写进去的真密文，不是 seed 占位串");

        Map<?, ?> updated = expectData(admin(put("/admin/admins/" + adminId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("roleId", roleId, "phone", "")))));
        assertNull(updated.get("phone"), "PUT 带空串就是把联系方式清掉，不是忽略");
        assertNull(jdbcTemplate.queryForObject("SELECT phone FROM `admin` WHERE id = ?",
                String.class, adminId), "清空必须真的落到库里");

        expectOk(admin(delete("/admin/admins/" + adminId)));
        assertFalse(expectList(admin(get("/admin/admins"))).stream()
                .anyMatch(row -> ADMIN_USER.equals(row.get("username"))), "删除后列表不该还有这一行");
        assertEquals(1, count("SELECT COUNT(*) FROM `admin` WHERE id = ? AND deleted = 1", adminId),
                "删的是软删标记，行还在（登录凭据的历史要能追）");

        // 软删之后同名可以原样重建：uk_username（V1:383）是库级约束，名字不会永久失踪
        Map<?, ?> revived = expectData(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", ADMIN_USER, "password", PASSWORD, "roleId", roleId)))));
        assertEquals(adminId, number(revived.get("id")),
                "复活的是原来那一行，id 不变——审计里那个 operator_id 才有连续意义");
        assertEquals(0, count("SELECT deleted FROM `admin` WHERE id = ?", adminId));
    }

    @Test
    void adminUsernameCannotDuplicateALiveAccount() throws Exception {
        long roleId = createRole(ROLE_NAME, List.of("dashboard"));
        expectData(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", ADMIN_USER, "password", PASSWORD, "roleId", roleId)))));

        expectCode(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", ADMIN_USER, "password", PASSWORD, "roleId", roleId)))), 4007);

        expectCode(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", "  " + ADMIN_USER + "  ", "password", PASSWORD,
                        "roleId", roleId)))), 4007);
    }

    @Test
    void adminCreateRejectsUnknownRoleAndShortPassword() throws Exception {
        // 角色不存在：不能用一个没查出来的 roleId 建账号，否则 V2 的四个角色之外会出现悬空归属
        expectCode(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", ADMIN_USER, "password", PASSWORD, "roleId", 999999L)))), 4002);

        expectCode(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", ADMIN_USER, "password", "123", "roleId", 2L)))), 400);

        expectCode(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", ADMIN_USER, "password", PASSWORD, "roleId", 2L,
                        "phone", "12345")))), 400);
    }

    @Test
    void builtInAccountsCannotBeDeletedAndSelfCannotBeDeleted() throws Exception {
        long roleId = createRole(ROLE_NAME, List.of("dashboard"));
        Map<?, ?> probe = expectData(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", SELF_DELETE_USER, "password", PASSWORD, "roleId", roleId)))));
        long probeId = number(probe.get("id"));

        // V2 的四个内置账号（admin/system/doctor/nurse）在任何 token 下都删不掉
        for (String username : List.of("admin", "system", "doctor", "nurse")) {
            long builtInId = jdbcTemplate.queryForObject(
                    "SELECT id FROM `admin` WHERE username = ?", Long.class, username);
            expectCode(admin(delete("/admin/admins/" + builtInId)), 4009);
        }

        // 自己删自己：换成"以那个探针账号的身份"再删自己，这条才会走到 4008 而不是 4009
        String probeToken = staffToken(probeId, "admin");
        expectCode(withToken(delete("/admin/admins/" + probeId), probeToken), 4008);

        // 同一个探针账号删别人是允许的，证明 4008 不是把所有删除都挡死
        expectOk(admin(delete("/admin/admins/" + probeId)));
        assertEquals(1, count("SELECT COUNT(*) FROM `admin` WHERE id = ? AND deleted = 1", probeId));
    }

    // ============================================================
    // 系统设置的权限边界：读也要能力
    // ============================================================

    @Test
    void systemReadsRequireEditSettings() throws Exception {
        List<String> readPaths = List.of("/admin/admins", "/admin/roles", "/admin/titles",
                "/admin/announcements", "/admin/announcements/options");
        for (String path : readPaths) {
            // 护士与医生有 dashboard/appointment 模块，但没有任何能力 → 业务码 4001
            expectCode(withToken(get(path), nurseToken), 4001);
            expectCode(withToken(get(path), doctorToken), 4001);
            // 患者 token 连后台的门都进不来：HTTP 403 + body 4001
            mockMvc.perform(withToken(get(path), patientToken))
                    .andExpect(status().isForbidden());
            expectCode(withToken(get(path), patientToken), 4001);
        }
        // 看板是唯一四角色都能读的（PRD 333 行：登录后进入首页数据看板）
        expectCode(withToken(get("/admin/dashboard"), nurseToken), 200);
        expectCode(withToken(get("/admin/dashboard"), doctorToken), 200);
    }

    @Test
    void editSettingsCapGatesEverySystemWrite() throws Exception {
        // 没有 EDIT_SETTINGS 的角色打四把写端点，一律 4001 且不产生任何行
        expectCode(withToken(post("/admin/titles").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", TITLE_NAME))), nurseToken), 4001);
        expectCode(withToken(post("/admin/roles").contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderlyMap("name", ROLE_NAME, "modules", List.of("dashboard")))),
                doctorToken), 4001);
        expectCode(withToken(post("/admin/announcements").contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderlyMap("title", ANNOUNCEMENT_TITLE,
                                "content", ANNOUNCEMENT_BODY, "type", "STOP_CLINIC"))), nurseToken), 4001);
        expectCode(withToken(delete("/admin/admins/1"), nurseToken), 4001);
        assertEquals(0, count("SELECT COUNT(*) FROM title WHERE name = ?", TITLE_NAME));
        assertEquals(0, count("SELECT COUNT(*) FROM role WHERE name = ?", ROLE_NAME));
        assertEquals(0, count("SELECT COUNT(*) FROM announcement WHERE title = ?", ANNOUNCEMENT_TITLE));
        assertEquals(4, count("SELECT COUNT(*) FROM `admin` WHERE deleted = 0"));
    }

    /**
     * 卡片 763 行「权限配置」的全链验证：后台建的角色 → 用它登录的账号 → 真登录 →
     * token 里的模块 = 页面上勾的那两项 → 能读看板、写不动任何东西。
     *
     * <p>顺带钉住 {@code SecurityConfig} 换成类别角色 STAFF 的那一步：
     * 在改造之前，这个 token 打任何一把 {@code /admin/**} 都会在外面吃 403。
     */
    @Test
    void clerkWithCustomRoleCanReadDashboardButNotWrite() throws Exception {
        customRoleId = createRole("T28 探针角色", List.of("dashboard", "appointment"));
        expectData(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", CLERK_USER, "password", PASSWORD, "roleId", customRoleId)))));

        String captchaKey = captchaKey();
        Map<?, ?> login = expectData(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("username", CLERK_USER, "password", PASSWORD,
                        "captchaKey", captchaKey, "captchaCode", captchaCode(captchaKey)))));
        assertEquals(List.of("dashboard", "appointment"), login.get("modules"),
                "登录必须读 role.permissions（V1:392），否则页面上的勾选不会生效");
        assertEquals(List.of(), login.get("caps"),
                "写能力不落库（Capability 枚举注释），所以自定义角色拿到的是只读角色");
        String clerkToken = String.valueOf(login.get("token"));

        expectCode(withToken(get("/admin/dashboard"), clerkToken), 200);
        expectCode(withToken(get("/admin/admins"), clerkToken), 4001);
        expectCode(withToken(post("/admin/titles").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", TITLE_NAME))), clerkToken), 4001);
        assertEquals(0, count("SELECT COUNT(*) FROM title WHERE name = ?", TITLE_NAME));
    }

    @Test
    void builtInRolesAreReadOnlyAndCustomRoleDeletesAreGuarded() throws Exception {
        // 内置四个角色：改名/改权限/删除全部 4011，读得到
        List<Map<?, ?>> roles = expectList(admin(get("/admin/roles")));
        assertEquals(4, roles.size(), "V2 建的角色是四行：" + roleNames(roles));
        for (Map<?, ?> role : roles) {
            assertEquals(Boolean.TRUE, role.get("core"));
            long id = number(role.get("id"));
            expectCode(admin(put("/admin/roles/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content(json(orderlyMap("name", "T28 改名试试", "modules", List.of("dashboard"))))), 4011);
            expectCode(admin(delete("/admin/roles/" + id)), 4011);
        }
        // system 那一行在库里是 ["*"]，解析出来必须等于全部 8 个模块
        Map<?, ?> systemRole = roles.stream()
                .filter(role -> "system".equals(role.get("name"))).findFirst().orElseThrow();
        assertEquals(8, ((List<?>) systemRole.get("modules")).size());
        assertEquals(Boolean.TRUE, systemRole.get("wildcard"));

        // 自定义角色：还挂着管理员时删不掉（4010），改名字可以
        long roleId = createRole(ROLE_IN_USE_NAME, List.of("dashboard"));
        expectData(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", OTHER_USER, "password", PASSWORD, "roleId", roleId)))));
        expectCode(admin(delete("/admin/roles/" + roleId)), 4010);

        Map<?, ?> renamed = expectData(admin(put("/admin/roles/" + roleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", ROLE_IN_USE_NAME + "改",
                        "modules", List.of("dashboard", "schedule"))))));
        assertEquals(ROLE_IN_USE_NAME + "改", renamed.get("name"));
        assertEquals(List.of("dashboard", "schedule"), renamed.get("modules"));
        assertEquals(1L, number(renamed.get("adminCount")), "PRD 453 行没要这个数，但删除守卫要看的正是它");

        // 账号删掉以后角色就能删了
        expectOk(admin(delete("/admin/admins/"
                + number(expectList(admin(get("/admin/admins"))).stream()
                .filter(row -> OTHER_USER.equals(row.get("username"))).findFirst().orElseThrow()
                .get("id")))));
        expectOk(admin(delete("/admin/roles/" + roleId)));
        assertEquals(0, count("SELECT COUNT(*) FROM role WHERE id = ? AND deleted = 0", roleId));
    }

    @Test
    void roleCreateDropsUnknownModuleKeysAndRejectsEmptySelection() throws Exception {
        // 未知键丢弃、不报错也不发明模块：写进库的只能是那 8 个键
        Map<?, ?> created = expectData(admin(post("/admin/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", ROLE_NAME,
                        "modules", List.of("dashboard", "not_a_module", "dashboard"))))));
        assertEquals(List.of("dashboard"), created.get("modules"),
                "未知键丢弃、重复键折叠，落库的是模块键的干净列表");

        // 一个都不勾 = 建出来的人连首页都看不见，这种角色没有存在意义，直接拒
        expectCode(admin(post("/admin/roles").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", ROLE_NAME + "空", "modules", List.of())))), 400);

        // 重名：PermissionService 的两张表按名字取，两行同名会永远读成同一份权限
        expectCode(admin(post("/admin/roles").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", ROLE_NAME, "modules", List.of("schedule"))))), 5002);
    }

    // ============================================================
    // 职称
    // ============================================================

    @Test
    void titleRoundTripAndCountedInUseDoctors() throws Exception {
        Map<?, ?> created = expectData(admin(post("/admin/titles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", TITLE_NAME, "sortOrder", 9)))));
        long titleId = number(created.get("id"));
        assertEquals(TITLE_NAME, created.get("name"));
        assertEquals(0L, number(created.get("doctorCount")));

        // 挂一个医生在这个职称上，在用数就该变成 1
        long departmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM department ORDER BY id LIMIT 1", Long.class);
        expectData(admin(post("/admin/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", DOCTOR_NAME, "departmentId", departmentId,
                        "titleId", titleId, "intro", "T28 探针简介")))));
        assertEquals(1L, number(findById(expectList(admin(get("/admin/titles"))), titleId)
                .get("doctorCount")));

        Map<?, ?> edited = expectData(admin(put("/admin/titles/" + titleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", TITLE_NAME + "改")))));
        assertEquals(TITLE_NAME + "改", edited.get("name"));
        assertEquals(0, number(edited.get("sortOrder")),
                "PUT 少带的 sort_order 是按清空处理（显式 SET），不是保留旧值");
    }

    // ============================================================
    // 消息公告：本卡是 announcement 的第一个写侧
    // ============================================================

    @Test
    void announcementStopClinicBecomesVisibleInMiniProgram() throws Exception {
        Map<?, ?> created = expectData(admin(post("/admin/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("title", ANNOUNCEMENT_TITLE,
                        "content", ANNOUNCEMENT_BODY, "type", "STOP_CLINIC")))));
        long announcementId = number(created.get("id"));
        assertNotNull(created.get("publishTime"), "创建时必须落发布时间（患者侧按它倒序排）");

        // 跨卡断言：T24 的停诊通知页从上线起读到的都是空列表，本卡补上写侧以后必须看见这一条
        List<Map<?, ?>> notices = expectList(withToken(get("/user/stop-notices"), patientToken));
        assertTrue(notices.stream().anyMatch(row -> ANNOUNCEMENT_TITLE.equals(row.get("title"))),
                "后台发的停诊公告必须出现在小程序停诊通知页，实际列表：" + notices);

        Map<?, ?> edited = expectData(admin(put("/admin/announcements/" + announcementId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("title", ANNOUNCEMENT_TITLE + "（更正版）",
                        "content", ANNOUNCEMENT_BODY, "type", "STOP_CLINIC")))));
        assertEquals(created.get("publishTime"), edited.get("publishTime"),
                "改正文不许把公告顶回最新一条（与 T27 updateArticle 同一条 SET 白名单）");

        expectOk(admin(delete("/admin/announcements/" + announcementId)));
        List<Map<?, ?>> afterDelete = expectList(withToken(get("/user/stop-notices"), patientToken));
        assertFalse(afterDelete.stream().anyMatch(row -> (ANNOUNCEMENT_TITLE + "（更正版）")
                .equals(row.get("title"))), "删除=撤回，患者侧不该还挂着这条停诊");
    }

    @Test
    void announcementTypeMustBeKnownAndOptionsListThreeTypes() throws Exception {
        List<Map<?, ?>> options = expectList(admin(get("/admin/announcements/options")));
        assertEquals(List.of("NOTICE", "ACTIVITY", "STOP_CLINIC"),
                options.stream().map(row -> String.valueOf(row.get("value"))).collect(Collectors.toList()),
                "类型候选只有一个来源（AnnouncementType），前端不许自己抄一份");

        expectCode(admin(post("/admin/announcements").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("title", ANNOUNCEMENT_TITLE,
                        "content", ANNOUNCEMENT_BODY, "type", "STOP")))), 400);
        // 未知类型不静默兜底成 NOTICE：那等于替管理员选了一个他没选的类型
        assertEquals(0, count("SELECT COUNT(*) FROM announcement WHERE title = ?", ANNOUNCEMENT_TITLE));
    }

    // ============================================================
    // J62 数据看板 → 数据正确
    // ============================================================

    @Test
    void j62_dashboardCountsOnlyTodayWindowAndExcludesYesterdayProbe() throws Exception {
        insertMoneyProbes();
        long feedbackBefore = count("SELECT COUNT(*) FROM feedback WHERE deleted = 0 AND status = 'PENDING'");
        long refundBefore = count("SELECT COUNT(*) FROM refund_record WHERE status = 'PENDING'");
        insertPendingProbes();

        Map<?, ?> data = expectData(admin(get("/admin/dashboard")));

        assertEquals(String.valueOf(jdbcTemplate.queryForObject("SELECT CURDATE()", Object.class)),
                data.get("statDate"), "页面显示的日期必须就是 SQL 用的那个 CURDATE()，两处一个时钟");
        assertEquals(count("SELECT COUNT(*) FROM appointment WHERE deleted = 0 "
                + "AND DATE(created_at) = CURDATE()"), number(data.get("todayAppointmentCount")));
        assertEquals(count("SELECT COUNT(*) FROM appointment WHERE deleted = 0 "
                + "AND status = 'COMPLETED' AND DATE(appointment_time) = CURDATE()"),
                number(data.get("todayVisitCount")));
        assertEquals(count("SELECT COALESCE(SUM(amount_fen), 0) FROM payment_record "
                + "WHERE status = 'SUCCESS' AND DATE(created_at) = CURDATE()"),
                number(data.get("outpatientConsumeFen")));
        assertEquals(count("SELECT COALESCE(SUM(amount_fen), 0) FROM recharge_record "
                + "WHERE status = 'SUCCESS' AND patient_id IS NOT NULL AND DATE(created_at) = CURDATE()"),
                number(data.get("outpatientRechargeFen")));
        assertEquals(count("SELECT COALESCE(SUM(amount_fen), 0) FROM recharge_record "
                + "WHERE status = 'SUCCESS' AND inpatient_id IS NOT NULL AND DATE(created_at) = CURDATE()"),
                number(data.get("inpatientRechargeFen")));

        // 探针自己必须真被算进窗口，否则上面五处"与 SQL 相等"可能只是两边都算了 0
        assertTrue(number(data.get("outpatientConsumeFen")) >= CONSUME_TODAY_FEN,
                "今天的消费探针要进数字，实际：" + data.get("outpatientConsumeFen"));
        assertTrue(number(data.get("todayAppointmentCount")) >= 2,
                "今天的两条预约探针要进数字，实际：" + data.get("todayAppointmentCount"));

        // 窗口外确实有数据、而且没被算进来：昨天的 99 分探针比今天所有探针加起来都大
        long consumeIgnoringDate = count("SELECT COALESCE(SUM(amount_fen), 0) FROM payment_record "
                + "WHERE status = 'SUCCESS'");
        assertTrue(consumeIgnoringDate > number(data.get("outpatientConsumeFen")),
                "库里窗口外还有成功缴费单，看板值必须严格小于不带日期的总额");
        assertTrue(consumeIgnoringDate - number(data.get("outpatientConsumeFen")) >= CONSUME_YESTERDAY_FEN,
                "少掉的那部分至少包含昨天的探针金额，说明 DATE 过滤真的起作用了");

        // PENDING / REFUNDED 都不算收入：seed 里那笔 REFUNDED 的缴费单不能被加回来
        long pendingConsume = count("SELECT COALESCE(SUM(amount_fen), 0) FROM payment_record "
                + "WHERE status <> 'SUCCESS'");
        assertTrue(pendingConsume > 0, "探针与 seed 提供的未成功单据是这条断言的前提");
        assertFalse(number(data.get("outpatientConsumeFen")) >= CONSUME_TODAY_FEN + pendingConsume,
                "非 SUCCESS 的流水不许进收入");

        List<Map<?, ?>> pending = pendingItems(data);
        assertEquals(List.of("REFUND_REVIEW", "FEEDBACK_REPLY"),
                pending.stream().map(row -> String.valueOf(row.get("type"))).collect(Collectors.toList()),
                "待处理事项只列真有两把写端点能消掉的那两类");
        assertEquals(feedbackBefore + 1, number(findByKey(pending, "type", "FEEDBACK_REPLY").get("count")));
        assertEquals(refundBefore + 1, number(findByKey(pending, "type", "REFUND_REVIEW").get("count")));

        // 卡片 769 行 + 附录 B 第 803 行：口径文字必须随数字一起返回
        List<Map<?, ?>> notes = listAt(data, "metricNotes");
        assertEquals(7, notes.size(), "五个指标 + 两条待处理的口径，一处都不能少：" + notes);
        for (Map<?, ?> note : notes) {
            assertFalse(String.valueOf(note.get("definition")).isBlank(), "口径文字不能是空串：" + note);
            assertNotNull(note.get("field"));
        }

        // 待处理项不带跳转 URL：路由白名单在前端（与 T03 的 landingPage 同一条做法）
        assertFalse(objectMapper.writeValueAsString(pending).contains("/finance/"),
                "服务端不许吐路径，前端按 type 换路由：" + pending);
    }

    @Test
    void nurseSeesNullMoneyButRealCountsOnDashboard() throws Exception {
        insertMoneyProbes();

        String body = mockMvc.perform(withToken(get("/admin/dashboard"), nurseToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 裁剪层写的是 null（键还在），不是把字段抹掉——前端要能区分"没权限看"和"这个数不存在"
        for (String field : List.of("outpatientConsumeFen", "outpatientRechargeFen",
                "inpatientRechargeFen")) {
            assertTrue(body.contains("\"" + field + "\":null"),
                    "护士视角金额必须是 null，实际响应：" + body);
        }
        Map<?, ?> data = expectData(withToken(get("/admin/dashboard"), nurseToken));
        assertTrue(number(data.get("todayAppointmentCount")) >= 1,
                "非金额字段不受裁剪影响，条数照给");
        assertEquals(7, listAt(data, "metricNotes").size());
    }

    @Test
    void dashboardResponseCarriesExactlyTheDocumentedFields() throws Exception {
        Map<?, ?> data = expectData(admin(get("/admin/dashboard")));
        assertEquals(new TreeSet<>(List.of("statDate", "todayAppointmentCount", "todayVisitCount",
                "outpatientConsumeFen", "outpatientRechargeFen", "inpatientRechargeFen",
                "pendingItems", "metricNotes")), new TreeSet<>(data.keySet()),
                "看板字段是 PRD 4.2 四行的落点，多一个就是发明了指标，少一个就是漏做");
        assertFalse(data.containsKey("physicalIncomeFen"),
                "体检收入给不出：physical_appointment（V1:280-292）没有金额列，见类注释");
    }

    // ============================================================
    // 改密码（PRD 4.6.5 / 9.2 的 628 行把它列在认证授权）
    // ============================================================

    @Test
    void changePasswordVerifiesOldOneAndNeverLogsThePassword() throws Exception {
        long roleId = createRole(ROLE_NAME, List.of("dashboard"));
        Map<?, ?> created = expectData(admin(post("/admin/admins")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "username", ADMIN_USER, "password", PASSWORD, "roleId", roleId)))));
        long adminId = number(created.get("id"));
        String originalHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM `admin` WHERE id = ?", String.class, adminId);

        String selfToken = staffToken(adminId, "admin");
        expectCode(withToken(put("/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("oldPassword", "wrong-password",
                        "newPassword", "new-password-9"))), selfToken), 401);
        assertEquals(originalHash, jdbcTemplate.queryForObject(
                "SELECT password_hash FROM `admin` WHERE id = ?", String.class, adminId),
                "旧密码不对时一行都不许改");

        // 新密码与旧密码相同也要拒：否则"改过密码"这件事在审计里是一次假的留痕
        expectCode(withToken(put("/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("oldPassword", PASSWORD, "newPassword", PASSWORD))),
                selfToken), 400);

        expectOk(withToken(put("/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("oldPassword", PASSWORD,
                        "newPassword", "new-password-9"))), selfToken));

        String updatedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM `admin` WHERE id = ?", String.class, adminId);
        assertFalse(originalHash.equals(updatedHash), "改过以后哈希必须换掉");
        assertTrue(passwordEncoder.matches("new-password-9", updatedHash));
        assertFalse(passwordEncoder.matches(PASSWORD, updatedHash), "旧密码从此不再可用");

        // 审计留痕，但 detail 里一个口令字段都不能有（切面会把入参整个 dump 出来，本卡因此不走切面）
        Map<?, ?> audit = lastAudit("CHANGE_PASSWORD");
        assertEquals(adminId, number(audit.get("operator_id")), "改密这件事的操作人就是本人");
        String detail = String.valueOf(audit.get("detail"));
        assertFalse(detail.contains("new-password-9"), "审计 detail 不许含新密码：" + detail);
        assertFalse(detail.contains(PASSWORD), "审计 detail 不许含旧密码：" + detail);
        assertFalse(detail.contains("password"), "审计 detail 连字段名都不该出现：" + detail);

        // 建账号那两条也要验一遍：CREATE_ADMIN 的 detail 里同样不能躺着明文口令
        String createDetail = String.valueOf(lastAudit("CREATE_ADMIN").get("detail"));
        assertFalse(createDetail.contains(PASSWORD), "建账号的审计不许含明文密码：" + createDetail);
        assertTrue(createDetail.contains(ADMIN_USER), "用户名是要留痕的：" + createDetail);
    }

    @Test
    void patientTokenCannotChangeStaffPassword() throws Exception {
        mockMvc.perform(withToken(put("/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(orderlyMap("oldPassword", PASSWORD,
                                "newPassword", "whatever-123"))), patientToken))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // T27 留下的跨卡 TODO：医生/科室改名以后，历史列表里的名字跟着变
    // ============================================================

    @Test
    void renamedDoctorStillNamedInAdminAppointmentList() throws Exception {
        long doctorId = jdbcTemplate.queryForObject(
                "SELECT id FROM doctor ORDER BY id LIMIT 1", Long.class);
        long appointmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM appointment WHERE doctor_id = ? AND deleted = 0 ORDER BY id LIMIT 1",
                Long.class, doctorId);
        String originalName = jdbcTemplate.queryForObject(
                "SELECT name FROM doctor WHERE id = ?", String.class, doctorId);

        Map<?, ?> before = expectData(admin(get("/admin/appointments/" + appointmentId)));
        assertEquals(originalName, before.get("doctorName"));

        Map<?, ?> detail = expectData(admin(get("/admin/doctors/" + doctorId)));
        String renamed = "T28 改名医生";
        expectData(admin(put("/admin/doctors/" + doctorId).contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "name", renamed,
                        "departmentId", number(detail.get("departmentId")),
                        "titleId", number(detail.get("titleId")),
                        "intro", detail.get("intro"),
                        "specialty", detail.get("specialty"))))));

        Map<?, ?> after = expectData(admin(get("/admin/appointments/" + appointmentId)));
        assertEquals(renamed, after.get("doctorName"),
                "T27 让医生可以改名，T25 的预约详情读的是活名字而不是快照——这条是那个联动的断言");

        // 改回原名：seed 的医生名字是别的卡要用的，探针不留痕
        expectData(admin(put("/admin/doctors/" + doctorId).contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap(
                        "name", originalName,
                        "departmentId", number(detail.get("departmentId")),
                        "titleId", number(detail.get("titleId")),
                        "intro", detail.get("intro"),
                        "specialty", detail.get("specialty"))))));
        assertEquals(originalName, jdbcTemplate.queryForObject(
                "SELECT name FROM doctor WHERE id = ?", String.class, doctorId));
    }

    // ============================================================
    // 端点注册表
    // ============================================================

    @Test
    void t28EndpointRegistryIsExactlyWhatTheCardAskedFor() {
        Set<String> found = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                boolean mine = pattern.startsWith("/admin/admins") || pattern.startsWith("/admin/roles")
                        || pattern.startsWith("/admin/titles") || pattern.startsWith("/admin/announcements")
                        || pattern.startsWith("/admin/dashboard") || pattern.equals("/auth/password");
                if (mine) {
                    found.add(verb + " " + pattern);
                }
            }
        });
        // 职称三把（没有 DELETE，理由见 AdminTitleService 类注释）；公告五把（含类型候选）；
        // /auth/password 挂在认证族而不是 /admin/** —— PRD 9.2 的 628 行就是这么归类的。
        // 18 把里 16 把挂 EDIT_SETTINGS（四组 CRUD 全部），只有看板和改密码两把不挂。
        assertEquals(new TreeSet<>(List.of(
                "DELETE /admin/admins/{id}",
                "DELETE /admin/announcements/{id}",
                "DELETE /admin/roles/{id}",
                "GET /admin/admins",
                "GET /admin/announcements",
                "GET /admin/announcements/options",
                "GET /admin/dashboard",
                "GET /admin/roles",
                "GET /admin/titles",
                "POST /admin/admins",
                "POST /admin/announcements",
                "POST /admin/roles",
                "POST /admin/titles",
                "PUT /admin/admins/{id}",
                "PUT /admin/announcements/{id}",
                "PUT /admin/roles/{id}",
                "PUT /admin/titles/{id}",
                "PUT /auth/password")), found, "本卡的端点清单（18 把，其中 16 把挂 EDIT_SETTINGS）");
    }

    // ============================================================
    // 探针数据
    // ============================================================

    /**
     * 钱表与预约表的探针行只能裸插 SQL：本卡的四组 CRUD 里没有一张能凭空造出一笔缴费单，
     * 而 T12/T15 那条支付链路要跑通得过验证码、号源、余额三关（T15 定案里就记着"探针只能裸插 SQL"）。
     * 裸插的代价是把 {@code created_at} 写成昨天/今天两种值，这正好是 J62 要测的窗口边界。
     */
    private void insertMoneyProbes() {
        jdbcTemplate.update("INSERT INTO appointment (order_no, patient_id, doctor_id, schedule_id, "
                + "status, appointment_time, fee_fen, created_at) VALUES "
                + "('T28AP0001', 1, 1, 900001, 'CONFIRMED', NOW(), 3000, NOW())");
        jdbcTemplate.update("INSERT INTO appointment (order_no, patient_id, doctor_id, schedule_id, "
                + "status, appointment_time, fee_fen, created_at) VALUES "
                + "('T28AP0002', 2, 1, 900002, 'COMPLETED', NOW(), 3000, NOW())");
        // 昨天完成的一条：就诊量的窗口边界负对照
        jdbcTemplate.update("INSERT INTO appointment (order_no, patient_id, doctor_id, schedule_id, "
                + "status, appointment_time, fee_fen, created_at) VALUES "
                + "('T28AP0003', 3, 1, 900003, 'COMPLETED', "
                + "DATE_SUB(NOW(), INTERVAL 1 DAY), 3000, DATE_SUB(NOW(), INTERVAL 1 DAY))");
        jdbcTemplate.update("INSERT INTO payment_record (order_no, patient_id, items, amount_fen, "
                + "pay_method, status, created_at) VALUES "
                + "('T28PY0001', 1, '[]', ?, 'BALANCE', 'SUCCESS', NOW())", CONSUME_TODAY_FEN);
        jdbcTemplate.update("INSERT INTO payment_record (order_no, patient_id, items, amount_fen, "
                + "pay_method, status, created_at) VALUES "
                + "('T28PY0002', 1, '[]', ?, 'BALANCE', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 1 DAY))",
                CONSUME_YESTERDAY_FEN);
        jdbcTemplate.update("INSERT INTO recharge_record (order_no, patient_id, amount_fen, "
                + "pay_method, status, created_at) VALUES "
                + "('T28RCO001', 1, ?, 'BALANCE', 'SUCCESS', NOW())", RECHARGE_OUTPATIENT_FEN);
        jdbcTemplate.update("INSERT INTO recharge_record (order_no, inpatient_id, amount_fen, "
                + "pay_method, status, created_at) VALUES "
                + "('T28RCI001', 1, ?, 'BALANCE', 'SUCCESS', NOW())", RECHARGE_INPATIENT_FEN);
    }

    private void insertPendingProbes() {
        jdbcTemplate.update("INSERT INTO refund_record (order_no, related_id, related_type, "
                + "amount_fen, status, created_at) VALUES ('T28RF0001', 1, 'PAYMENT', 100, 'PENDING', NOW())");
        Long userId = userIds.get(userIds.size() - 1);
        jdbcTemplate.update("INSERT INTO feedback (user_id, content, status, created_at) VALUES "
                + "(?, ?, 'PENDING', NOW())", userId, FEEDBACK_CONTENT);
    }

    private long createRole(String name, List<String> modules) throws Exception {
        Map<?, ?> created = expectData(admin(post("/admin/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(orderlyMap("name", name, "modules", modules)))));
        return number(created.get("id"));
    }

    private Map<?, ?> lastAudit(String action) {
        return jdbcTemplate.queryForMap("SELECT action, operator_id, detail FROM audit_log "
                + "WHERE action = ? ORDER BY id DESC LIMIT 1", action);
    }

    /** 验证码走真通道：GET /auth/captcha 拿 key，正文从 Redis 读（与验收脚本同一个读法）。 */
    private String captchaKey() throws Exception {
        Map<?, ?> data = expectData(get("/auth/captcha"));
        return String.valueOf(data.get("captchaKey"));
    }

    private String captchaCode(String captchaKey) {
        String code = redisTemplate.opsForValue().get(CaptchaService.KEY_PREFIX + captchaKey);
        assertNotNull(code, "Redis 里没有这张验证码的正文，登录测不下去");
        return code;
    }

    // ============================================================
    // 断言与响应解析
    // ============================================================

    private List<Map<?, ?>> pendingItems(Map<?, ?> data) {
        return listAt(data, "pendingItems");
    }

    private List<Map<?, ?>> listAt(Map<?, ?> data, String field) {
        Object value = data.get(field);
        assertTrue(value instanceof List, field + " 应该是数组，实际：" + value);
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> rows = (List<Map<?, ?>>) value;
        return rows;
    }

    private Map<?, ?> findByKey(List<Map<?, ?>> rows, String key, Object value) {
        return rows.stream().filter(row -> value.equals(row.get(key)))
                .findFirst().orElseThrow(() -> new AssertionError(key + "=" + value + " 不在列表里：" + rows));
    }

    private List<String> roleNames(List<Map<?, ?>> rows) {
        return rows.stream().map(row -> String.valueOf(row.get("name"))).collect(Collectors.toList());
    }

    private Map<String, Long> snapshotCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : List.of("`admin`", "role", "title", "announcement", "doctor",
                "payment_record", "recharge_record", "refund_record", "feedback", "appointment",
                "patient", "`user`")) {
            counts.put(table, count("SELECT COUNT(*) FROM " + table));
        }
        return counts;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private void expectCode(MockHttpServletRequestBuilder builder, int code) throws Exception {
        Map<?, ?> root = expectRoot(builder);
        assertEquals(code, ((Number) root.get("code")).intValue(),
                "期望业务码 " + code + "，实际：" + root);
    }

    /** 删除类与改密码类端点回的是 {@code Result<Void>}，data 天生是 null，不能走 expectData。 */
    private void expectOk(MockHttpServletRequestBuilder builder) throws Exception {
        Map<?, ?> root = expectRoot(builder);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        assertNull(root.get("data"), "Void 型端点的 data 必须是 null，实际：" + root);
    }

    private Map<?, ?> expectRoot(MockHttpServletRequestBuilder builder) throws Exception {
        String body = mockMvc.perform(builder)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readValue(body, Map.class);
    }

    private Map<?, ?> expectData(MockHttpServletRequestBuilder builder) throws Exception {
        Map<?, ?> root = expectRoot(builder);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }

    private List<Map<?, ?>> expectList(MockHttpServletRequestBuilder builder) throws Exception {
        Map<?, ?> root = expectRoot(builder);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> rows = (List<Map<?, ?>>) data;
        return rows;
    }

    private Map<?, ?> findById(List<Map<?, ?>> rows, long id) {
        return rows.stream().filter(row -> id == number(row.get("id")))
                .findFirst().orElseThrow(() -> new AssertionError("列表里没有 id=" + id + "：" + rows));
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : -1L;
    }

    private String placeholders() {
        return userIds.isEmpty() ? "NULL"
                : userIds.stream().map(id -> "?").collect(Collectors.joining(","));
    }

    private Object[] userIdArgs() {
        return userIds.toArray();
    }

    private String staffToken(Long adminId, String role) {
        return jwtUtil.generateToken(adminId, role, role, List.of("dashboard"), List.of());
    }

    private String newPatientToken(String tag) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", tag + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
        userIds.add(((Number) data.get("userId")).longValue());
        return String.valueOf(data.get("token"));
    }

    private String json(Map<?, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * 请求体一律走这个有序 map：{@code Map.of} 的迭代顺序是随机的，
     * 同一份断言换一次 JDK 就可能把 password 排到 username 前面，
     * 出问题时 diff 出来的 JSON 和文档里的样子不一样，白花时间。
     */
    private Map<String, Object> orderlyMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
