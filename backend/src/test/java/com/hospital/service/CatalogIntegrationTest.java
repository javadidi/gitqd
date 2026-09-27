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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10 科室与医生管理的必做场景：J22（科室列表 → 数据正确）、J23（科室详情 → 医生列表正确），
 * 外加医生列表/医生详情/排班口径、角色隔离，以及**「本卡只读」的机器证明**。
 *
 * <p>需要本机 MySQL 与 Redis 都在跑（与 PatientIntegrationTest / InpatientIntegrationTest 同一前提）。
 * 期望值一律来自 seed.sql 的**字面数据**（3 科室 / 3 职称 / 5 医生）或本类自己插的探针行，
 * 不调 CatalogService 反算——CONVENTIONS「期望值不要用生产代码算」。
 *
 * <p><b>只读红线怎么证：</b>{@link #snapshotCatalogCounts()} 在每个用例前给
 * department / doctor / title / schedule 四张表各拍一次行数，用例结束后
 * （先清掉本类插的探针行）再拍一次并断言完全相等。T10 的红线是「不做排班管理（T11）」，
 * 光说"我没写 update 语句"是自查不是证据；行数不变才是能重跑的证据。
 *
 * <p><b>测试数据自净：</b>探针行一律用 {@code T10} 前缀命名，@AfterEach 按前缀批量物理删除
 * （不走 MyBatis-Plus 的 deleteById——那是逻辑删，行还在）。清理规则不依赖"登记列表"，
 * 所以内联插行的用例漏登记也不会往开发库留垃圾（T08 那次事故的教训）。
 * 删除顺序：schedule → doctor → department，先删子表再删父表。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class CatalogIntegrationTest {

    private static final String PROBE_PREFIX = "T10";
    private static final String PROBE_LIKE = PROBE_PREFIX + "%";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;
    private String patientToken;

    @BeforeEach
    void setUp() throws Exception {
        countsBefore = snapshotCatalogCounts();
        patientToken = newUserToken("j22");
    }

    @AfterEach
    void cleanupAndAssertReadOnly() {
        deleteProbeRows();
        for (Long userId : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        createdUserIds.clear();
        assertEquals(countsBefore, snapshotCatalogCounts(),
                "T10 全程只读：department/doctor/title/schedule 四张表的行数必须一行不变");
    }

    // ============================================================
    // J22 科室列表 → 数据正确
    // ============================================================

    /** seed.sql:43-46 三个科室，按 sort_order 升序 */
    @Test
    void j22_departmentList_returnsSeedDepartmentsInSortOrder() throws Exception {
        mockMvc.perform(get("/user/departments").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("消化内科"))
                .andExpect(jsonPath("$.data[0].intro").value("从事食管、胃、肠、肝胆胰疾病的门诊与内镜诊治。"))
                .andExpect(jsonPath("$.data[0].location").value("门诊楼 1 层 A 区"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data[1].name").value("普外科"))
                .andExpect(jsonPath("$.data[1].sortOrder").value(2))
                .andExpect(jsonPath("$.data[2].name").value("儿科"))
                .andExpect(jsonPath("$.data[2].sortOrder").value(3))
                // 内部存储细节不出参
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist());
    }

    /** PRD 76 行「展示医院所有科室列表，支持搜索」：按名称模糊匹配 */
    @Test
    void j22_departmentList_keywordFiltersByName() throws Exception {
        mockMvc.perform(get("/user/departments").param("keyword", "消化")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("消化内科"));
    }

    /** 搜不到必须是空数组而不是 null：小程序的 wx:for 拿到 null 渲染不出空态 */
    @Test
    void j22_departmentList_keywordWithoutMatch_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/user/departments").param("keyword", "不存在的科室")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    /** 空白 keyword 等同于不搜（CatalogService.trimToNull），不能变成"匹配名字里有空格的科室" */
    @Test
    void j22_departmentList_blankKeyword_returnsAll() throws Exception {
        mockMvc.perform(get("/user/departments").param("keyword", "   ")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    /** 软删的科室既不出现在列表里，也不能按 id 查到（@TableLogic 自动补 deleted=0） */
    @Test
    void j22_softDeletedDepartment_isInvisibleEverywhere() throws Exception {
        Long id = insertProbeDepartment(PROBE_PREFIX + "_SOFT_DELETED", 99);
        jdbcTemplate.update("UPDATE department SET deleted = 1 WHERE id = ?", id);

        mockMvc.perform(get("/user/departments").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/user/departments/" + id).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.message").value("数据不存在"));
    }

    @Test
    void j22_departmentDetail_unknownId_returnsDataNotFound() throws Exception {
        mockMvc.perform(get("/user/departments/99999999").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    // ============================================================
    // J23 科室详情 → 医生列表正确
    // ============================================================

    /**
     * seed.sql:59-64 —— 消化内科下是张伟（title_id=1 主任医师）与李慧敏（title_id=2 副主任医师），
     * 按 doctor.id 升序。断言的是**职称中文名**而不是 title_id：患者端不出内部主键。
     */
    @Test
    void j23_departmentDetail_returnsDepartmentFieldsAndItsDoctors() throws Exception {
        Long deptId = seedDepartmentId("消化内科");

        mockMvc.perform(get("/user/departments/" + deptId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(deptId))
                .andExpect(jsonPath("$.data.name").value("消化内科"))
                .andExpect(jsonPath("$.data.location").value("门诊楼 1 层 A 区"))
                .andExpect(jsonPath("$.data.doctors.length()").value(2))
                .andExpect(jsonPath("$.data.doctors[0].name").value("张伟"))
                .andExpect(jsonPath("$.data.doctors[0].titleName").value("主任医师"))
                .andExpect(jsonPath("$.data.doctors[0].specialty").value("胃炎、胃食管反流、消化道息肉"))
                .andExpect(jsonPath("$.data.doctors[1].name").value("李慧敏"))
                .andExpect(jsonPath("$.data.doctors[1].titleName").value("副主任医师"))
                // title_id 不外泄
                .andExpect(jsonPath("$.data.doctors[0].titleId").doesNotExist())
                // seed 造了 CURDATE()-7..+7 的排班，所以每个 seed 医生都该有可约时段
                .andExpect(jsonPath("$.data.doctors[0].availableCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.data.doctors[1].availableCount").value(greaterThan(0)));
    }

    /** 没有医生的科室必须回空数组，且 doctors 键**存在**（不能因 non_null 整键消失） */
    @Test
    void j23_departmentDetail_departmentWithoutDoctors_returnsEmptyArray() throws Exception {
        Long deptId = insertProbeDepartment(PROBE_PREFIX + "_EMPTY", 98);

        mockMvc.perform(get("/user/departments/" + deptId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value(PROBE_PREFIX + "_EMPTY"))
                .andExpect(jsonPath("$.data.doctors").isArray())
                .andExpect(jsonPath("$.data.doctors.length()").value(0));
    }

    /** 医生列表：departmentId 选填。不传 = 全院 5 人（seed.sql:59-64） */
    @Test
    void j23_doctorList_withoutFilter_returnsAllSeedDoctors() throws Exception {
        mockMvc.perform(get("/user/doctors").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].name").value("张伟"))
                .andExpect(jsonPath("$.data[4].name").value("刘一鸣"));
    }

    @Test
    void j23_doctorList_filtersByDepartment() throws Exception {
        Long deptId = seedDepartmentId("普外科");

        mockMvc.perform(get("/user/doctors").param("departmentId", String.valueOf(deptId))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("王建国"))
                .andExpect(jsonPath("$.data[0].titleName").value("主任医师"))
                .andExpect(jsonPath("$.data[1].name").value("陈雪"))
                .andExpect(jsonPath("$.data[1].titleName").value("主治医师"))
                .andExpect(jsonPath("$.data[0].departmentId").value(deptId));
    }

    /**
     * 不存在的科室 id 必须报错，不能静默返回空数组——
     * 空数组会把"你给的科室不存在"伪装成"该科室没有医生"这个合法业务结果。
     */
    @Test
    void j23_doctorList_unknownDepartmentId_returnsDataNotFound() throws Exception {
        mockMvc.perform(get("/user/doctors").param("departmentId", "99999999")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    /** title_id 为 NULL 的医生：titleName 键整个消失（non_null），小程序用 '—' 兜 */
    @Test
    void j23_doctorWithoutTitle_omitsTitleNameKey() throws Exception {
        Long deptId = seedDepartmentId("儿科");
        Long doctorId = insertProbeDoctor(PROBE_PREFIX + "_NO_TITLE", deptId, null);

        mockMvc.perform(get("/user/doctors").param("departmentId", String.valueOf(deptId))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                // 儿科原本只有刘一鸣 1 人，加探针后 2 人，探针 id 更大所以排在后面
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].id").value(doctorId))
                .andExpect(jsonPath("$.data[1].titleName").doesNotExist())
                .andExpect(jsonPath("$.data[1].availableCount").value(0));
    }

    /**
     * 「可约时段数」口径的精确证明：只数 date&gt;=今天 且 remaining_slots&gt;0 的排班。
     * 探针医生插 4 条排班——过去有号、今天已满、未来已满、未来有号，只有最后一条该被数进去。
     */
    @Test
    void j23_availableCount_ignoresPastAndFullyBookedSlots() throws Exception {
        Long deptId = seedDepartmentId("儿科");
        Long doctorId = insertProbeDoctor(PROBE_PREFIX + "_COUNT", deptId, seedTitleId("主治医师"));
        insertProbeSchedule(doctorId, -30, "MORNING", 20, 5);   // 过去、有号 → 不数
        insertProbeSchedule(doctorId, 0, "MORNING", 20, 0);     // 今天、已满 → 不数
        insertProbeSchedule(doctorId, 3, "AFTERNOON", 15, 0);   // 未来、已满 → 不数
        insertProbeSchedule(doctorId, 5, "MORNING", 20, 7);     // 未来、有号 → 数

        // 不用 jsonPath 的过滤表达式 $.data[?(@.id == N)]：匹配不上时它返回 null 而不是空集，
        // 断言信息分不清"字段值不对"和"根本没找到这个人"。改成取回响应体在 Java 侧挑。
        Map<String, Object> probe = findDoctor(fetchDoctors(deptId), doctorId);
        assertEquals(1, ((Number) probe.get("availableCount")).intValue(),
                "只该数「今天及以后且还有号」的那一条");
        assertEquals(PROBE_PREFIX + "_COUNT", probe.get("name"));
    }

    // ============================================================
    // 医生详情（卡片 415 行：简介 + 排班时间）
    // ============================================================

    @Test
    void j23_doctorDetail_returnsIntroSpecialtyAndDepartmentName() throws Exception {
        Long doctorId = seedDoctorId("张伟");

        mockMvc.perform(get("/user/doctors/" + doctorId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(doctorId))
                .andExpect(jsonPath("$.data.name").value("张伟"))
                .andExpect(jsonPath("$.data.departmentName").value("消化内科"))
                .andExpect(jsonPath("$.data.titleName").value("主任医师"))
                .andExpect(jsonPath("$.data.intro").value("从事消化内科临床工作 25 年，主导内镜下治疗。"))
                .andExpect(jsonPath("$.data.specialty").value("胃炎、胃食管反流、消化道息肉"))
                // avatar 在 seed 里是 NULL，配合 non_null 整键消失；小程序用占位图标兜
                .andExpect(jsonPath("$.data.avatar").doesNotExist())
                .andExpect(jsonPath("$.data.schedules").isArray())
                .andExpect(jsonPath("$.data.schedules.length()").value(greaterThan(0)));
    }

    /**
     * 排班明细的口径与排序：
     * ① 过去的一律不返回（seed 造了 CURDATE()-7..+7，不过滤会把一半废数据推给小程序）；
     * ② 今天及以后都返回，**包括号已满的**（详情页是"出诊时间表"，藏起已满时段会让患者
     *    以为医生那天不出诊；可否预约由 remainingSlots 交给前端置灰）；
     * ③ 排序是 date 升序、同日 上午→下午→晚上（不是字母序：AFTERNOON &lt; EVENING &lt; MORNING）；
     * ④ date 钉成 ISO 字符串而不是 Jackson 的数组形态，理由同 T09 的 boundAt：
     *    spring.jackson.date-format 只管 java.util.Date，管不到 LocalDate，
     *    所以真实形态只能靠断言证实、不能靠配置推定。
     */
    @Test
    void j23_doctorDetail_schedulesAreTodayOnwardSortedByDateThenSlot() throws Exception {
        Long doctorId = insertProbeDoctor(PROBE_PREFIX + "_SCHED", seedDepartmentId("普外科"), seedTitleId("主任医师"));
        insertProbeSchedule(doctorId, -30, "MORNING", 20, 20);
        insertProbeSchedule(doctorId, 0, "EVENING", 10, 10);
        insertProbeSchedule(doctorId, 0, "MORNING", 20, 0);
        insertProbeSchedule(doctorId, 2, "AFTERNOON", 15, 15);
        insertProbeSchedule(doctorId, 2, "MORNING", 20, 20);

        List<Map<String, Object>> schedules = fetchSchedules(doctorId);

        assertEquals(4, schedules.size(), "过去那条排班必须被过滤掉");

        String today = mysqlDate(0);
        String plusTwo = mysqlDate(2);
        assertEquals(List.of(today, today, plusTwo, plusTwo),
                schedules.stream().map(row -> String.valueOf(row.get("date"))).toList(),
                "先按日期升序，今天必须包含在内");
        assertEquals(List.of("MORNING", "EVENING", "MORNING", "AFTERNOON"),
                schedules.stream().map(row -> String.valueOf(row.get("timeSlot"))).toList(),
                "同一天内按 上午→下午→晚上，不是字母序");

        Map<String, Object> first = schedules.get(0);
        assertEquals(0, ((Number) first.get("remainingSlots")).intValue(), "号已满的时段仍要列出，由前端置灰");
        assertEquals(20, ((Number) first.get("totalSlots")).intValue(), "号源是个数不是金额，不走金额裁剪");
        assertEquals(today, String.valueOf(first.get("date")));
    }

    @Test
    void j23_doctorDetail_unknownId_returnsDataNotFound() throws Exception {
        mockMvc.perform(get("/user/doctors/99999999").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.message").value("数据不存在"));
    }

    /** 没有排班的医生：schedules 是空数组而不是缺键；科室名仍要能解析出来 */
    @Test
    void j23_doctorDetail_withoutSchedules_returnsEmptyArray() throws Exception {
        Long doctorId = insertProbeDoctor(PROBE_PREFIX + "_NO_SCHED", seedDepartmentId("儿科"), null);

        mockMvc.perform(get("/user/doctors/" + doctorId).header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedules").isArray())
                .andExpect(jsonPath("$.data.schedules.length()").value(0))
                .andExpect(jsonPath("$.data.departmentName").value("儿科"));
    }

    // ============================================================
    // 角色隔离（附录 B 第 804 条：权限判断不能只写在 UI）
    // ============================================================

    /**
     * 员工 token 打患者端目录接口必须 403。
     * SecurityConfig:42 的 {@code /user/** → hasRole(patient)} 是既有规则，
     * 本卡新增两个 controller 却没改一行安全配置，所以要证实它们确实落在保护伞下。
     */
    @Test
    void staffToken_cannotReachCatalogEndpoints() throws Exception {
        String staffToken = jwtUtil.generateToken(1L, "admin", "admin", List.of("dashboard"), List.of());
        Long deptId = seedDepartmentId("消化内科");
        Long doctorId = seedDoctorId("张伟");

        for (String path : List.of("/user/departments", "/user/departments/" + deptId,
                "/user/doctors", "/user/doctors/" + doctorId)) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(4001));
        }
    }

    /** 匿名请求 401：目录数据虽不含隐私，但患者端接口一律要 token，不开"免登录浏览"的口子 */
    @Test
    void anonymousRequest_isRejected() throws Exception {
        mockMvc.perform(get("/user/departments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ============================================================
    // 助手
    // ============================================================

    /** 取某科室的医生列表响应体，解析成 List&lt;Map&gt; 供 Java 侧按 id 挑人断言 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchDoctors(Long departmentId) throws Exception {
        String body = mockMvc.perform(get("/user/doctors").param("departmentId", String.valueOf(departmentId))
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> root = objectMapper.readValue(body, Map.class);
        return (List<Map<String, Object>>) root.get("data");
    }

    private Map<String, Object> findDoctor(List<Map<String, Object>> doctors, Long doctorId) {
        return doctors.stream()
                .filter(row -> doctorId.equals(((Number) row.get("id")).longValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("响应里没有 id=" + doctorId + " 的医生，实际有 " + doctors.size() + " 条"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchSchedules(Long doctorId) throws Exception {        String body = mockMvc.perform(get("/user/doctors/" + doctorId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> root = objectMapper.readValue(body, Map.class);
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        return (List<Map<String, Object>>) data.get("schedules");
    }

    /** 「今天 + N 天」以 MySQL 为唯一口径，避免 JVM 与 MySQL 时区不一致导致用例假红 */
    private String mysqlDate(int dayOffset) {
        return jdbcTemplate.queryForObject(
                "SELECT DATE_ADD(CURDATE(), INTERVAL ? DAY)", String.class, dayOffset);
    }

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

    private Map<String, Integer> snapshotCatalogCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : List.of("department", "doctor", "title", "schedule")) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Integer.class);
            counts.put(table, count == null ? -1 : count);
        }
        return counts;
    }

    /** 按前缀删而不是按登记删：内联插行的用例漏登记也不会留垃圾（CONVENTIONS 测试数据自净约定） */
    private void deleteProbeRows() {
        jdbcTemplate.update("DELETE s FROM schedule s JOIN doctor d ON d.id = s.doctor_id WHERE d.name LIKE ?",
                PROBE_LIKE);
        jdbcTemplate.update("DELETE FROM doctor WHERE name LIKE ?", PROBE_LIKE);
        jdbcTemplate.update("DELETE FROM department WHERE name LIKE ?", PROBE_LIKE);
    }

    private Long seedDepartmentId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM department WHERE name = ?", Long.class, name);
    }

    private Long seedDoctorId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM doctor WHERE name = ?", Long.class, name);
    }

    private Long seedTitleId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM title WHERE name = ?", Long.class, name);
    }

    private Long insertProbeDepartment(String name, int sortOrder) {
        jdbcTemplate.update("INSERT INTO department (name, intro, location, sort_order) VALUES (?, ?, ?, ?)",
                name, name + " 简介", name + " 位置", sortOrder);
        return jdbcTemplate.queryForObject("SELECT id FROM department WHERE name = ?", Long.class, name);
    }

    private Long insertProbeDoctor(String name, Long departmentId, Long titleId) {
        jdbcTemplate.update(
                "INSERT INTO doctor (name, department_id, title_id, intro, specialty, avatar) VALUES (?, ?, ?, ?, ?, NULL)",
                name, departmentId, titleId, name + " 简介", name + " 擅长");
        return jdbcTemplate.queryForObject("SELECT id FROM doctor WHERE name = ?", Long.class, name);
    }

    /**
     * dayOffset 用 MySQL 的 {@code DATE_ADD(CURDATE(), INTERVAL ? DAY)} 而不是 Java 侧算日期，
     * 这样"今天"的定义与被测代码里 {@code LocalDate.now()} 看到的是同一个来源
     * （连接串 serverTimezone=Asia/Shanghai）。
     */
    private void insertProbeSchedule(Long doctorId, int dayOffset, String timeSlot, int total, int remaining) {
        jdbcTemplate.update("INSERT INTO schedule (doctor_id, `date`, time_slot, total_slots, remaining_slots) "
                        + "VALUES (?, DATE_ADD(CURDATE(), INTERVAL ? DAY), ?, ?, ?)",
                doctorId, dayOffset, timeSlot, total, remaining);
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
    }
}
