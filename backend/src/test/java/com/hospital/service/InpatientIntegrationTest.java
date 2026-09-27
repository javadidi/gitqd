package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
import com.hospital.entity.Inpatient;
import com.hospital.mapper.InpatientMapper;
import com.hospital.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesRegex;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T09 住院人管理的必做场景：J20（绑定住院号 → 验证通过/失败）与 J21（重复住院号 → 被拒），
 * 外加附录 B 第 806 条要求的横向越权回归。
 *
 * <p>需要本机 MySQL 与 Redis 都在跑（与 PatientIntegrationTest 同一前提）。
 * 用户用 mock 微信登录现建，不碰 seed 的 4 个 user、10 行 patient 和 5 行 inpatient；
 * 住院号一律随机生成，因为 {@code uk_inpatient_no} 是全局唯一，复用固定值会让重跑变成假失败。
 *
 * <p>本类建的行在 {@link #cleanup()} 里**物理删除**：MyBatis-Plus 的 deleteById 是逻辑删除，
 * 行还在、{@code uk_inpatient_no} 还占着（T07 的 uk_openid、T08 的 uk_card_no 同源教训）。
 * 登记待删键的动作**不能只写在助手里**——凡是内联发请求的用例都要自己
 * {@code createdInpatientNos.add(...)}，否则每跑一次就往开发库留一条占着唯一索引的垃圾。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class InpatientIntegrationTest {

    private static final Random RANDOM = new Random();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InpatientMapper inpatientMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<String> createdInpatientNos = new ArrayList<>();

    /** 按 inpatient_no 清而不是按 id：软删占位行是裸 SQL 插的，没有回填 id 到本类 */
    @AfterEach
    void cleanup() {
        for (String no : createdInpatientNos) {
            jdbcTemplate.update("DELETE FROM inpatient WHERE inpatient_no = ?", no);
        }
        createdInpatientNos.clear();
        for (Long id : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", id);
        }
        createdUserIds.clear();
    }

    // ============================================================
    // J20 绑定住院号 → 验证通过
    // ============================================================

    @Test
    void j20_bindInpatient_storesRowOwnedByCaller() throws Exception {
        String token = newUserToken("j20a");
        String no = randomInpatientNo();
        createdInpatientNos.add(no);

        mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("张守义", no, "消化内科", "03 层 12 床"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("张守义"))
                .andExpect(jsonPath("$.data.inpatientNo").value(no))
                .andExpect(jsonPath("$.data.department").value("消化内科"))
                .andExpect(jsonPath("$.data.bedNo").value("03 层 12 床"))
                // boundAt 必须是 ISO 字符串而不是 Jackson 的数组形态，否则小程序的 formatDate 会渲染成乱码。
                // 这条断言是"钉住格式"，不是"应该有值"——spring.jackson.date-format 只管 java.util.Date，
                // 管不到 LocalDateTime，所以真实形态只能靠断言证实、不能靠配置推定。
                .andExpect(jsonPath("$.data.boundAt").value(matchesRegex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*")));

        Inpatient row = selectByNo(no);
        assertNotNull(row, "住院人应已落库");
        // 归属只能来自 token：请求体里没有 userId 字段（附录 B 第 806 条）
        assertEquals(userIdOf(token), row.getUserId(), "user_id 必须取自 token，不是客户端报的");
        assertEquals("张守义", row.getName());
        assertEquals("消化内科", row.getDepartment());
        assertEquals("03 层 12 床", row.getBedNo());
        assertEquals(0, deletedFlagOf(no), "新绑定的行必须是活的");
    }

    /** 反过来验：列表里只看得到自己的 */
    @Test
    void j20_listReturnsOwnInpatientsOnly() throws Exception {
        String tokenA = newUserToken("j20l-a");
        String tokenB = newUserToken("j20l-b");
        String noA = randomInpatientNo();
        String noB = randomInpatientNo();
        bind(tokenA, noA);
        bind(tokenB, noB);

        mockMvc.perform(get("/user/inpatients").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].inpatientNo").value(noA));
    }

    @Test
    void j20_detailReturnsOwnInpatient() throws Exception {
        String token = newUserToken("j20d");
        String no = randomInpatientNo();
        Long id = bind(token, no, "李长顺", "普外科", "05 层 08 床");

        mockMvc.perform(get("/user/inpatients/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.name").value("李长顺"))
                .andExpect(jsonPath("$.data.inpatientNo").value(no))
                .andExpect(jsonPath("$.data.department").value("普外科"))
                .andExpect(jsonPath("$.data.bedNo").value("05 层 08 床"));
    }

    /**
     * 科室与床号是选填：留空时两列必须是 NULL（不是空串），
     * 且响应里这两个键**整个消失**——application.yml 的 {@code default-property-inclusion: non_null}。
     * 小程序侧因此一律用 {@code item.department || '—'} 兜住，不能假定键一定在。
     */
    @Test
    void j20_blankDepartmentAndBedNo_storedAsNullAndOmittedFromJson() throws Exception {
        String token = newUserToken("j20n");
        String no = randomInpatientNo();
        createdInpatientNos.add(no);

        mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("赵国强", no, "  ", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.department").doesNotExist())
                .andExpect(jsonPath("$.data.bedNo").doesNotExist());

        Inpatient row = selectByNo(no);
        assertNull(row.getDepartment(), "空白科室应存成 NULL，与 seed 第 4/5 行同形");
        assertNull(row.getBedNo(), "空白床号应存成 NULL");
    }

    // ============================================================
    // J20 绑定住院号 → 验证失败
    // ============================================================

    /** 验证的第一层是参数校验：空白必须在入库前拦下，走 400 而不是 500 或"成功建了个空名字的行" */
    @Test
    void j20_blankNameOrInpatientNo_rejectedWith400() throws Exception {
        String token = newUserToken("j20v");
        // 两次请求都预期被拦，但号仍登记待删：万一校验哪天被改坏、行真的建出来了，cleanup 能兜住
        String noForBlankName = randomInpatientNo();
        createdInpatientNos.add(noForBlankName);

        mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("  ", noForBlankName, null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("王丽华", "   ", null, null))))
                .andExpect(status().isBadRequest());
    }

    /** 别人的住院人：详情必须是 1005 而不是 403——403 等于确认"这个 id 存在" */
    @Test
    void j20_otherUsersInpatient_detailReturns1005Not403() throws Exception {
        String tokenA = newUserToken("j20o-a");
        String tokenB = newUserToken("j20o-b");
        Long idOfA = bind(tokenA, randomInpatientNo());

        mockMvc.perform(get("/user/inpatients/" + idOfA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.message").value("住院人不存在"));

        // 不存在的 id 与"不是你的 id"必须回同一个码，否则就能用响应差枚举别人的住院人
        mockMvc.perform(get("/user/inpatients/999999999").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005));
    }

    @Test
    void j20_anonymousAndStaffTokenCannotReachInpatientEndpoints() throws Exception {
        // 未认证 401（小程序 utils/request.js 靠 401 清 token 并跳登录页）
        mockMvc.perform(get("/user/inpatients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 员工 token 403：/user/** 的角色隔离覆盖了本卡新增的路径，不需要为 T09 改 SecurityConfig
        String adminToken = newStaffToken();
        mockMvc.perform(get("/user/inpatients").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));

        // POST 与 GET /{id} 也要覆盖到：用一个必然不存在的 id，
        // 万一角色隔离哪天被改坏，这条用例会走到 service 的 1005 而不是真动到 seed 的住院人。
        // 探针号也登记待删：这两次请求**预期**被拒所以不会建行，但"预期被拒"正是要验的事，
        // 万一哪天角色隔离坏了、请求真的建了行，cleanup 得能把它删掉（T08 的自净教训）。
        String probeNo = randomInpatientNo();
        createdInpatientNos.add(probeNo);
        mockMvc.perform(post("/user/inpatients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("探针", probeNo, null, null))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("探针", probeNo, null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
        mockMvc.perform(get("/user/inpatients/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
    }

    // ============================================================
    // J21 重复住院号 → 被拒（两层防护都要证明存在）
    // ============================================================

    /** 第一层：前置查命中，给友好提示 1006 */
    @Test
    void j21_duplicateInpatientNo_rejectedByPreCheck() throws Exception {
        String token = newUserToken("j21a");
        String no = randomInpatientNo();
        bind(token, no, "原名", null, null);

        mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("改名", no, "别的科室", "别的床"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.message").value("住院号已存在"));

        assertEquals(1, rawCountByNo(no), "被拒的那次不该留下第二行");
        assertEquals("原名", selectByNo(no).getName(), "被拒的那次不该改到已有行");
    }

    /** 跨用户也算重复：住院号是全局唯一（uk_inpatient_no 建在单列上），不是"每人唯一" */
    @Test
    void j21_duplicateInpatientNoAcrossUsers_alsoRejected() throws Exception {
        String tokenA = newUserToken("j21b-a");
        String tokenB = newUserToken("j21b-b");
        String no = randomInpatientNo();
        bind(tokenA, no, "周雅", "消化内科", null);

        mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("想抢别人的住院号", no, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));

        Inpatient untouched = selectByNo(no);
        assertEquals(userIdOf(tokenA), untouched.getUserId(), "越权绑定不能把归属改到自己名下");
        assertEquals("周雅", untouched.getName());
        assertEquals(1, rawCountByNo(no));
    }

    /**
     * 第二层：前置查看不见软删行（{@code @TableLogic} 自动补 {@code deleted=0}），
     * 但 {@code uk_inpatient_no} 不认 deleted 列，索引仍被占着 → 必须由 catch 分支翻成 1006，
     * 不能漏成 500。用裸 SQL 确定性地造出这个前提。
     *
     * <p>同时钉住 T09 与 T08 的那处刻意不同：**这里不复活**。
     * T08 的 create 会把本人软删过的同一卡号复活成原行，因为 T08 有删除入口；
     * T09 四路规格证据都没提删除/解绑，程序自己造不出软删行，所以撞上一律拒绝。
     */
    @Test
    void j21_inpatientNoHeldBySoftDeletedRow_rejectedWithoutReviving() throws Exception {
        String tokenA = newUserToken("j21c-a");
        String tokenB = newUserToken("j21c-b");
        String no = randomInpatientNo();

        jdbcTemplate.update(
                "INSERT INTO inpatient (user_id, name, inpatient_no, deleted) VALUES (?, '软删占位', ?, 1)",
                userIdOf(tokenB), no);
        createdInpatientNos.add(no);

        assertEquals(0, countByNo(no), "前置查看不见软删行，这正是本用例要的前提");

        mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload("想复活", no, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.message").value("住院号已存在"));

        assertEquals(1, deletedFlagOf(no), "被拒的那次不该把软删行复活");
        assertEquals(1, rawCountByNo(no), "也不该另插一行");
    }

    /** 兜底层的物理事实：同一住院号硬插两次，MySQL 必须拒绝 */
    @Test
    void j21_uniqueIndexReallyExists() {
        String no = randomInpatientNo();
        createdInpatientNos.add(no);
        jdbcTemplate.update(
                "INSERT INTO inpatient (user_id, name, inpatient_no) VALUES (1, '索引探针', ?)", no);

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(
                "INSERT INTO inpatient (user_id, name, inpatient_no) VALUES (1, '索引探针2', ?)", no));
    }

    // ============================================================
    // 助手
    // ============================================================

    private String newUserToken(String tag) throws Exception {
        String body = mockMvc.perform(post("/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", tag + "-" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> root = objectMapper.readValue(body, Map.class);
        Map<?, ?> data = (Map<?, ?>) root.get("data");
        createdUserIds.add(((Number) data.get("userId")).longValue());
        return String.valueOf(data.get("token"));
    }

    /** 员工 token 直接签发，不走登录：本用例只关心角色隔离，不关心密码 */
    private String newStaffToken() {
        return jwtUtil.generateToken(1L, "admin", "admin", List.of("dashboard"), List.of());
    }

    private Long userIdOf(String token) {
        return Long.valueOf(jwtUtil.parseToken(token).getSubject());
    }

    private Long bind(String token, String no) throws Exception {
        return bind(token, no, "测试住院人", null, null);
    }

    private Long bind(String token, String no, String name, String department, String bedNo) throws Exception {
        createdInpatientNos.add(no);
        String body = mockMvc.perform(post("/user/inpatients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(bindPayload(name, no, department, bedNo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> root = objectMapper.readValue(body, Map.class);
        return ((Number) ((Map<?, ?>) root.get("data")).get("id")).longValue();
    }

    private Inpatient selectByNo(String no) {
        return inpatientMapper.selectOne(
                new LambdaQueryWrapper<Inpatient>().eq(Inpatient::getInpatientNo, no));
    }

    private int countByNo(String no) {
        Long count = inpatientMapper.selectCount(
                new LambdaQueryWrapper<Inpatient>().eq(Inpatient::getInpatientNo, no));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 以下两个断言辅助走裸 SQL，因为 {@code @TableLogic} 让 MyBatis-Plus 看不见软删行，
     * 而"软删行仍占着唯一索引"恰恰是必须证明的事。
     */
    private int rawCountByNo(String no) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inpatient WHERE inpatient_no = ?", Integer.class, no);
        return count == null ? 0 : count;
    }

    private int deletedFlagOf(String no) {
        Integer flag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM inpatient WHERE inpatient_no = ?", Integer.class, no);
        return flag == null ? -1 : flag;
    }

    /** 每次跑用新住院号：唯一索引是全局的，复用固定值会让重跑变成假失败 */
    private static String randomInpatientNo() {
        return "T09" + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
    }

    private Map<String, ?> bindPayload(String name, String no, String department, String bedNo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("inpatientNo", no);
        payload.put("department", department);
        payload.put("bedNo", bedNo);
        return payload;
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
    }
}
