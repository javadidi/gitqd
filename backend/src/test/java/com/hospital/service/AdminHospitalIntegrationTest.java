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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端医院管理（T27）：J59 医生管理 → CRUD 通、J60 反馈处理 → 状态更新。
 *
 * <h2>本卡第一次真的提供「删除」入口</h2>
 * T25 让我们发现「后台读不到软删行」会做出 {@code 就诊时间=— —} 这种缺陷（自动化三层全绿时它是坏的），
 * T26 在钱表上又遇一次（人删了、钱还在，姓名读不出来）。本卡是第一次给医生/科室/套餐/内容
 * 提供删除，所以两件事同时做：
 * <ul>
 *   <li>三处删除守卫（2008 科室下还有医生、2009 医生还有活排班或活预约、2010 套餐还有活预约）
 *       拒绝把历史弄成无主；</li>
 *   <li>医生与科室的<b>读名字</b>改成穿软删（{@code selectByIdsIncludingDeleted}），
 *       于是「删掉归档」与「历史仍说得出是谁」能同时成立。
 *       这一条由 {@link #deletedDoctorAndDepartmentStillNamedInBothAppointmentLists} 钉住，
 *       不给它留下"等浏览器验收再抓一次"的机会。</li>
 * </ul>
 *
 * <h2>编辑必须能把可空栏位清空，这一条也是本卡才暴露的</h2>
 * MyBatis-Plus 的 {@code updateById} 默认跳过 null 字段。所以「把简介抹掉」「去掉分类」
 * 这种编辑用 updateById 会静默不生效，而接口回 200——测试若只断言 200 就是绿的假象。
 * 服务层因此统一走 {@code LambdaUpdateWrapper.set(...)} 显式赋值，
 * 本类里 {@link #departmentIntroCanBeClearedNotJustOverwritten} 是这条的证。
 *
 * <h2>探针与清理</h2>
 * 探针走真实 API（写路径本身就是被测对象），日期落在 +6 年（T25 用 +4、T26 用 +5，互不撞清理窗口）。
 * 清理一律裸 SQL 物理删：这些表都有 {@code deleted} 列，用 {@code mapper.delete} 收尾
 * 会每跑一次留一行死数据（T10 中过这招，累计 11 行）。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class AdminHospitalIntegrationTest {

    private static final int PROBE_YEAR_OFFSET = 6;
    private static final String DOCTOR_NAME = "T27 探针医生";
    private static final String DEPARTMENT_NAME = "T27 探针科室";
    private static final String TITLE_NAME = "T27 探针职称";
    private static final String PACKAGE_NAME = "T27 探针套餐";
    private static final String ITEM_NAME = "T27 探针项目";
    private static final String TYPE_NAME = "T27 探针类型";
    private static final String ARTICLE_TITLE = "T27 探针文章";
    private static final String GUIDE_TITLE = "T27 探针指南";
    private static final String NOTICE_TITLE = "T27 探针须知";
    private static final String PROFILE_TITLE = "T27 探针医院";
    private static final String FEEDBACK_CONTENT = "T27 探针反馈内容";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    private long auditIdFloor;
    private String adminToken;
    private String doctorToken;
    private String nurseToken;
    private String patientToken;
    private long probeUserId;
    private Map<?, ?> lastDepartmentCreateResponse;
    private final List<Long> userIds = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    @BeforeEach
    void setUp() throws Exception {
        countsBefore = snapshotCounts();
        Long maxAuditId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM audit_log", Long.class);
        auditIdFloor = maxAuditId == null ? 0L : maxAuditId;
        adminToken = staffToken(1L, "admin");
        doctorToken = staffToken(3L, "doctor");
        nurseToken = staffToken(4L, "nurse");
        patientToken = newPatientToken("j59");
        probeUserId = userIds.get(userIds.size() - 1);
    }

    @AfterEach
    void cleanupAndAssertSeedUntouched() {
        jdbcTemplate.update("DELETE FROM feedback WHERE content = ?", FEEDBACK_CONTENT);
        jdbcTemplate.update("DELETE FROM physical_appointment WHERE order_no LIKE 'T27PA%'");
        jdbcTemplate.update("DELETE FROM physical_package WHERE name = ?", PACKAGE_NAME);
        jdbcTemplate.update("DELETE FROM physical_item WHERE name = ?", ITEM_NAME);
        jdbcTemplate.update("DELETE FROM package_type WHERE name = ?", TYPE_NAME);
        jdbcTemplate.update("DELETE FROM health_article WHERE title = ?", ARTICLE_TITLE);
        jdbcTemplate.update("DELETE FROM guide_article WHERE title = ?", GUIDE_TITLE);
        jdbcTemplate.update("DELETE FROM appointment_notice WHERE title = ?", NOTICE_TITLE);
        jdbcTemplate.update("DELETE FROM hospital_profile WHERE title = ?", PROFILE_TITLE);
        jdbcTemplate.update("DELETE FROM appointment WHERE order_no LIKE 'T27A%' OR order_no LIKE 'T27H%'");
        jdbcTemplate.update("DELETE FROM schedule WHERE `date` >= DATE_ADD(CURDATE(), INTERVAL ? YEAR)",
                PROBE_YEAR_OFFSET);
        // 前缀用 'T27 '，不用 'T27 探针科室%'：这一条踩过一次——常量改名后旧名不再匹配清理谓词，
        // 一行科室留在库里（id=93），T10 的 CatalogIntegrationTest「列表正好 3 个 seed 科室」当场变 4。
        // seed 的科室/医生名里没有 'T27 '，所以宽前缀不会误删真数据。
        jdbcTemplate.update("DELETE FROM doctor WHERE name LIKE 'T27 %'");
        jdbcTemplate.update("DELETE FROM department WHERE name LIKE 'T27 %'");
        jdbcTemplate.update("DELETE FROM title WHERE name LIKE 'T27 %'");
        jdbcTemplate.update("DELETE FROM patient WHERE user_id IN (" + placeholders() + ")", userIdArgs());
        for (Long userId : userIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", userId);
        }
        jdbcTemplate.update("DELETE FROM audit_log WHERE id > ?", auditIdFloor);
        assertEquals(countsBefore, snapshotCounts(),
                "探针行必须清干净，且 seed 的行一张都不能少（清理按 name/title 精确删，不用宽 LIKE）");
    }

    // ============================================================
    // J59 科室 / 医生 CRUD
    // ============================================================

    @Test
    void j59_departmentCreateReadUpdateDeleteRoundTrip() throws Exception {
        Map<?, ?> created = expectData(admin(post("/admin/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", DEPARTMENT_NAME, "intro", "探针简介",
                        "location", "门诊楼 9 层")))));
        long departmentId = number(created.get("id"));
        assertEquals(DEPARTMENT_NAME, created.get("name"));
        assertEquals("门诊楼 9 层", created.get("location"));

        expectData(admin(put("/admin/departments/" + departmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", DEPARTMENT_NAME + "改", "location", "门诊楼 8 层")))));
        Map<?, ?> after = expectData(admin(get("/admin/departments/" + departmentId)));
        assertEquals(DEPARTMENT_NAME + "改", after.get("name"));

        expectCode(admin(delete("/admin/departments/" + departmentId)), 200);
        expectCode(admin(get("/admin/departments/" + departmentId)), 5001);
        assertEquals(1, count("SELECT COUNT(*) FROM department WHERE id = ? AND deleted = 1", departmentId),
                "删除是软删：行还在库里，只是默认读法读不到（历史单据的主体不能从库里消失）");
    }

    @Test
    void departmentIntroCanBeClearedNotJustOverwritten() throws Exception {
        long departmentId = seedDepartmentViaApi();
        assertTrue(departmentId > 0, "科室建单必须回自己的 id，后续 PUT/DELETE 全靠它");
        assertEquals(1, count("SELECT COUNT(*) FROM department WHERE id = ? AND deleted = 0", departmentId),
                "建完就该读得到：这一行断的是「API 写的行真的落了库」，不是响应里的字段");

        expectData(admin(put("/admin/departments/" + departmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", DEPARTMENT_NAME, "location", "门诊楼 7 层")))));
        Map<?, ?> after = expectData(admin(get("/admin/departments/" + departmentId)));
        assertFalse(after.containsKey("intro"),
                "编辑时不传简介就该把这一栏清空。MP 的 updateById 会跳过 null，"
                        + "接口照样回 200——只断言 200 就是一次绿的假象");
        assertEquals("门诊楼 7 层", after.get("location"));
    }

    @Test
    void j59_doctorCreateReadUpdateDeleteRoundTrip() throws Exception {
        long departmentId = seedDepartment();
        long titleId = seedTitle();

        Map<?, ?> created = expectData(admin(post("/admin/doctors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", DOCTOR_NAME, "departmentId", departmentId,
                        "titleId", titleId, "intro", "探针简介", "specialty", "探针方向")))));
        long doctorId = number(created.get("id"));
        assertEquals(DEPARTMENT_NAME, created.get("departmentName"),
                "科室名必须服务端经 doctor.department_id 两跳解析（表里只有 id）");
        assertEquals(TITLE_NAME, created.get("titleName"));
        assertFalse(created.containsKey("avatar"),
                "PRD 398 行列了头像，但全系统没有上传通道：这一栏既不写入也不回显");

        expectData(admin(put("/admin/doctors/" + doctorId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", DOCTOR_NAME, "departmentId", departmentId)))));
        Map<?, ?> after = expectData(admin(get("/admin/doctors/" + doctorId)));
        assertFalse(after.containsKey("titleName"), "不传职称就是取消挂靠（title_id 在 V1:88 可空）");
        assertFalse(after.containsKey("intro"), "同理，简介也要能被清空");

        expectCode(admin(delete("/admin/doctors/" + doctorId)), 200);
        expectCode(admin(get("/admin/doctors/" + doctorId)), 5001);
    }

    @Test
    void j59_doctorFormRejectsMissingDepartmentAndUnknownTitle() throws Exception {
        mockMvc.perform(admin(post("/admin/doctors").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", DOCTOR_NAME)))))
                .andExpect(status().isBadRequest());

        long departmentId = seedDepartment();
        expectCode(admin(post("/admin/doctors").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", DOCTOR_NAME, "departmentId", departmentId,
                        "titleId", 987654321L)))), 5001);
        expectCode(admin(post("/admin/doctors").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", DOCTOR_NAME, "departmentId", 987654321L)))), 5001);
        assertEquals(0, count("SELECT COUNT(*) FROM doctor WHERE name = ?", DOCTOR_NAME),
                "校验没过就不该留下一行");
    }

    @Test
    void doctorOptionsEndpointIsNotEatenByTheIdPath() throws Exception {
        long departmentId = seedDepartment();
        long titleId = seedTitle();
        Map<?, ?> options = expectData(admin(get("/admin/doctors/options")));

        // 这一条首先断的是路由：/options 若被 /admin/doctors/{id} 吃掉，这里直接 5001，
        // 医生表单的科室下拉就是空的，而页面测试只会看到「请求失败」。
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> departments = (List<Map<?, ?>>) options.get("departments");
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> titles = (List<Map<?, ?>>) options.get("titles");
        assertEquals(count("SELECT COUNT(*) FROM department WHERE deleted = 0"), departments.size(),
                "下拉给全部在册科室（seed 的 3 个 + 探针），漏掉真科室是线上事故");
        assertEquals(count("SELECT COUNT(*) FROM title WHERE deleted = 0"), titles.size());

        Map<?, ?> department = findById(departments, departmentId);
        assertEquals(DEPARTMENT_NAME, department.get("name"));
        assertEquals(2, department.size(),
                "选项只给 id 与名字：科室与职称的管理各归本卡的科室页与 T28，这里不是它们的替身");
        assertEquals(TITLE_NAME, findById(titles, titleId).get("name"));
    }

    // ============================================================
    // 三处删除守卫
    // ============================================================

    @Test
    void j59_deleteDepartmentWithDoctorsIsRefusedAndChangesNothing() throws Exception {
        long departmentId = seedDepartment();
        seedDoctor(departmentId);

        expectCode(admin(delete("/admin/departments/" + departmentId)), 2008);
        assertEquals(0, count("SELECT COUNT(*) FROM department WHERE id = ? AND deleted = 1", departmentId),
                "被拒的删除一行都不改：不能先删科室、再让医生挂在一个已不存在的归属上");
    }

    @Test
    void j59_deleteDoctorWithUpcomingScheduleIsRefusedThenAllowed() throws Exception {
        long departmentId = seedDepartment();
        long doctorId = seedDoctor(departmentId);
        seedSchedule(doctorId, LocalDate.now().plusYears(PROBE_YEAR_OFFSET));

        expectCode(admin(delete("/admin/doctors/" + doctorId)), 2009);
        assertEquals(0, count("SELECT COUNT(*) FROM doctor WHERE id = ? AND deleted = 1", doctorId));

        jdbcTemplate.update("DELETE FROM schedule WHERE doctor_id = ?", doctorId);
        expectCode(admin(delete("/admin/doctors/" + doctorId)), 200);
    }

    @Test
    void j59_packageDeleteGuardItemDeleteAndNoTypeDeleteEndpoint() throws Exception {
        long packageId = seedPackage();
        long patientId = createPatient("T27 体检患");
        jdbcTemplate.update("INSERT INTO physical_appointment (order_no, patient_id, package_id, "
                        + "appointment_date, status, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, ?, ?, 'CONFIRMED', NOW(3), NOW(3), 0)",
                "T27PA" + UUID.randomUUID().toString().substring(0, 8), patientId, packageId,
                LocalDate.now().plusDays(2));
        expectCode(admin(delete("/admin/physical-packages/" + packageId)), 2010);

        jdbcTemplate.update("DELETE FROM physical_appointment WHERE package_id = ?", packageId);
        expectCode(admin(delete("/admin/physical-packages/" + packageId)), 200);

        // 项目没有引用关系（套餐里是名字快照），删除不拦
        long itemId = seedItem();
        expectCode(admin(delete("/admin/physical-items/" + itemId)), 200);

        // 类型：PRD 416-417 行只给列表与新增，卡片 740 行的 CRUD 不构成删除授权。
        // 未映射的路径在本仓库目前落 500（跨卡 TODO），所以这里断言"不是业务成功"而不是具体码。
        long typeId = seedType();
        Map<?, ?> root = expectRoot(admin(delete("/admin/package-types/" + typeId)));
        assertFalse(Integer.valueOf(200).equals(((Number) root.get("code")).intValue()),
                "不该存在 DELETE /admin/package-types/{id}，实际：" + root);
        assertEquals(0, count("SELECT COUNT(*) FROM package_type WHERE id = ? AND deleted = 1", typeId));
    }

    // ============================================================
    // 软删之后名字还在（两张列表都要）
    // ============================================================

    @Test
    void deletedDoctorAndDepartmentStillNamedInBothAppointmentLists() throws Exception {
        long departmentId = seedDepartment();
        long doctorId = seedDoctor(departmentId);
        long patientId = createPatient("T27 已删医生患");
        long historyId = insertHistoricalAppointment(patientId, doctorId);

        jdbcTemplate.update("UPDATE doctor SET deleted = 1 WHERE id = ?", doctorId);
        jdbcTemplate.update("UPDATE department SET deleted = 1 WHERE id = ?", departmentId);

        Map<?, ?> adminRow = findById(expectList(admin(get("/admin/appointments"))), historyId);
        assertEquals(DOCTOR_NAME, adminRow.get("doctorName"),
                "后台预约列表不能因为医生被删就空掉——这正是 T25 那个破折号缺陷的形状");
        assertEquals(DEPARTMENT_NAME, adminRow.get("departmentName"));

        Map<?, ?> patientRow = findById(
                withTokenResult(get("/user/appointments"), patientToken), historyId);
        assertEquals(DOCTOR_NAME, patientRow.get("doctorName"),
                "患者自己的历史记录里，医生名同样不能消失");
    }

    // ============================================================
    // 权限与审计
    // ============================================================

    @Test
    void writesBehindManageHospitalCapReadsAreOpenToStaff() throws Exception {
        for (String token : List.of(doctorToken, nurseToken)) {
            expectCode(withToken(post("/admin/departments").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("name", DEPARTMENT_NAME))), token), 4001);
        }
        assertEquals(0, count("SELECT COUNT(*) FROM department WHERE name = ?", DEPARTMENT_NAME),
                "RequireCap 切面在 service 之前，被挡的请求一行都不写");

        for (String token : List.of(adminToken, doctorToken, nurseToken)) {
            expectCode(withToken(get("/admin/departments"), token), 200);
        }
        // 患者 token 走的是另一道门：SecurityConfig 的 anyRequest().hasAnyRole(4 个员工角色) 先挡，
        // HTTP 状态是 403（T25 j55 同一条理由），而医生/护士是进了门才被 @RequireCap 拒掉，
        // 落 BizException → HTTP 200 + 业务码 4001。两道门都在关人，但关的位置不同，值得分开钉。
        mockMvc.perform(withToken(post("/admin/departments").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", DEPARTMENT_NAME))), patientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void catalogWritesAuditEachWithTheirOwnAction() throws Exception {
        long departmentId = seedDepartmentViaApi();
        expectCode(admin(delete("/admin/departments/" + departmentId)), 200);

        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_DEPARTMENT' "
                + "AND target_type = 'department' AND id > ?", auditIdFloor));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'DELETE_DEPARTMENT' "
                + "AND target_type = 'department' AND target_id = ? AND id > ?",
                departmentId, auditIdFloor));
    }

    // ============================================================
    // 体检三兄弟（卡片 738/739/740 行）
    // ============================================================

    @Test
    void j59_packageItemsAreSnapshotAndPatientSideStillReadsThem() throws Exception {
        long typeId = seedType();
        Map<?, ?> created = expectData(admin(post("/admin/physical-packages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", PACKAGE_NAME, "typeId", typeId, "priceFen", 28800,
                        "targetAudience", "入职员工",
                        "items", List.of(Map.of("name", ITEM_NAME, "priceFen", 3000)))))));
        long packageId = number(created.get("id"));
        assertEquals(TYPE_NAME, created.get("typeName"),
                "V1:254 那个悬空的 type_id 从本卡起有归属表了（V7）");
        Map<?, ?> firstItem = (Map<?, ?>) ((List<?>) created.get("items")).get(0);
        assertEquals(ITEM_NAME, firstItem.get("name"));
        assertEquals(3000, ((Number) firstItem.get("priceFen")).intValue());

        // 患者侧（T22）读的是同一列。本卡是该列的写入方，形状必须让它那句逐项取 name 仍取得到。
        Map<?, ?> patientView = expectData(withToken(
                get("/user/physical-packages/" + packageId), patientToken));
        Map<?, ?> patientItem = (Map<?, ?>) ((List<?>) patientView.get("items")).get(0);
        assertEquals(ITEM_NAME, patientItem.get("name"),
                "改了 items 的形状就等于把 T22 的 UI 验收作废");

        expectCode(admin(delete("/admin/physical-packages/" + packageId)), 200);
    }

    // ============================================================
    // 内容五组（卡片 741/742/744/745/746 行）
    // ============================================================

    @Test
    void j59_articleEditKeepsPublishTimeAndGuideHasNoCategory() throws Exception {
        Map<?, ?> article = expectData(admin(post("/admin/health-articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", ARTICLE_TITLE, "content", "探针正文",
                        "category", "探针分类")))));
        long articleId = number(article.get("id"));
        assertNotNull(article.get("publishTime"),
                "发布时间由服务层落：患者侧列表按它倒序（V6 建这一列的理由）");
        assertEquals("探针分类", article.get("category"),
                "PRD 421 行括号里有分类二字，V7 为此补列；同一句的封面图不补，因为没有上传通道");
        Object publishTime = article.get("publishTime");

        Map<?, ?> edited = expectData(admin(put("/admin/health-articles/" + articleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", ARTICLE_TITLE, "content", "探针正文改")))));
        assertEquals("探针正文改", edited.get("content"));
        assertEquals(publishTime, edited.get("publishTime"),
                "修个错字不该把文章挪到刚刚发布，那会重排患者侧的列表");
        assertFalse(edited.containsKey("category"), "不传分类就是清空它");

        Map<?, ?> guide = expectData(admin(post("/admin/guide-articles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", GUIDE_TITLE, "content", "探针流程说明")))));
        assertFalse(guide.containsKey("category"),
                "指南没有分类列：PRD 262 行只要流程说明，V6 据此裁列。两页字段差是规格差");
        expectCode(admin(get("/admin/guide-articles/" + number(guide.get("id")))), 200);
    }

    @Test
    void j59_profileIsSingleRowUpsert() throws Exception {
        assertEquals(0, count("SELECT COUNT(*) FROM hospital_profile WHERE deleted = 0"),
                "前置：这张表首版是空的（T24 的纪律：不替医院编介绍）");

        Map<?, ?> first = expectData(admin(put("/admin/hospital-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", PROFILE_TITLE, "intro", "探针简介正文")))));
        long profileId = number(first.get("id"));
        assertEquals("探针简介正文", first.get("intro"),
                "空表上 PUT 必须能建出第一行，否则这个编辑页对新装的库既是空又救不回来");

        expectData(admin(put("/admin/hospital-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", PROFILE_TITLE, "intro", "探针第二版")))));
        assertEquals(1, count("SELECT COUNT(*) FROM hospital_profile WHERE title = ?", PROFILE_TITLE),
                "卡片 744 行的词是编辑不是 CRUD：第二次 PUT 改同一行，不插第二行");
        assertEquals(profileId, countLong("SELECT id FROM hospital_profile WHERE title = ?", PROFILE_TITLE));
        expectData(withToken(get("/user/hospital-profile"), patientToken));
    }

    @Test
    void noticesHaveOneSourceSharedByAdminAndPatient() throws Exception {
        Map<?, ?> seeded = expectData(withToken(get("/user/notices/appointment"), patientToken));
        String originalTitle = String.valueOf(seeded.get("title"));
        String originalContent = String.valueOf(seeded.get("content"));
        assertTrue(originalContent.contains("同一就诊人同一时段只能挂一个号"),
                "前置：seed 里有从 appointment/notice.js 逐字搬来的四条");

        expectData(admin(put("/admin/notices/appointment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", NOTICE_TITLE, "content", "第一条\n第二条")))));

        Map<?, ?> patientAfter = expectData(withToken(get("/user/notices/appointment"), patientToken));
        assertEquals(NOTICE_TITLE, patientAfter.get("title"));
        assertEquals("第一条\n第二条", patientAfter.get("content"),
                "后台改完患者侧必须立刻读到；两处各读各的，改了没生效这种 bug 前端查不出来");
        assertEquals(number(seeded.get("id")), number(patientAfter.get("id")), "同一行被改，不是新插一行");

        // 复原 seed 的那一行：本类的收尾断言按计数比对，删掉 seed 行会伪装成"清干净了"
        expectData(admin(put("/admin/notices/appointment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("title", originalTitle, "content", originalContent)))));

        Map<?, ?> delivery = expectData(withToken(get("/user/notices/delivery"), patientToken));
        assertTrue(String.valueOf(delivery.get("content")).contains("本版本不上传证件照片"),
                "病案配送那四条是从 case-delivery/notice.js 搬来的，一条都不能少");
    }

    // ============================================================
    // J60 反馈处理 → 状态更新
    // ============================================================

    @Test
    void j60_feedbackReplyMovesStatusToReplied() throws Exception {
        long feedbackId = seedFeedback();
        jdbcTemplate.update("UPDATE `user` SET nickname = ? WHERE id = ?", "T27 探针昵称", probeUserId);

        Map<?, ?> row = findById(expectList(admin(get("/admin/feedbacks"))), feedbackId);
        assertEquals("PENDING", row.get("status"));
        assertFalse(row.containsKey("reply"), "没回复过就不回这个键，而不是回一个空字符串");
        assertEquals(0, ((List<?>) row.get("images")).size(),
                "images 首版无人能写（没有上传通道）；给空数组让页面能说清没有附件");
        assertEquals("T27 探针昵称", row.get("nickname"),
                "反馈表只有 user_id（V1:359），让管理员对着一个数字处理患者投诉是不成立的："
                        + "nickname 是解析列，不是新事实。用户没填昵称时这一栏会整个消失"
                        + "（default-property-inclusion: non_null），页面要按可空处理");

        Map<?, ?> replied = expectData(admin(post("/admin/feedbacks/" + feedbackId + "/reply")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reply", "已联系处理")))));
        assertEquals("REPLIED", replied.get("status"), "J60：回复这个动作把单子推到已回复");
        assertEquals("已联系处理", replied.get("reply"));
        assertEquals("REPLIED", jdbcTemplate.queryForObject(
                "SELECT status FROM feedback WHERE id = ?", String.class, feedbackId));

        assertEquals(1, count("SELECT COUNT(*) FROM audit_log WHERE action = 'REPLY_FEEDBACK' "
                + "AND target_type = 'feedback' AND target_id = ? AND id > ?", feedbackId, auditIdFloor));
    }

    @Test
    void j60_secondReplyIsRefusedAndStatusIsNotAnInput() throws Exception {
        long feedbackId = seedFeedback();
        expectCode(admin(post("/admin/feedbacks/" + feedbackId + "/reply")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reply", "第一次")))), 200);

        // 第二次：5002。规格没写追加回复也没写改回复，而 reply 是单列 TEXT——
        // 允许覆盖等于让管理员悄悄改掉已经发给患者看过的话。
        expectCode(admin(post("/admin/feedbacks/" + feedbackId + "/reply")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reply", "第二次")))), 5002);
        assertEquals("第一次", jdbcTemplate.queryForObject(
                "SELECT reply FROM feedback WHERE id = ?", String.class, feedbackId));

        expectCode(admin(post("/admin/feedbacks/" + feedbackId + "/reply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reply", "越权改状态", "status", "CLOSED")))), 5002);
        assertEquals(0, count("SELECT COUNT(*) FROM feedback WHERE status = 'CLOSED'"),
                "CLOSED 在 V1:362 的取值域里，但规格没有任何一句关闭反馈，所以本卡也不给那个按钮");
    }

    @Test
    void j60_replyRequiresTheCap() throws Exception {
        long feedbackId = seedFeedback();
        for (String token : List.of(doctorToken, nurseToken)) {
            expectCode(withToken(post("/admin/feedbacks/" + feedbackId + "/reply")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("reply", "不该成功"))), token), 4001);
        }
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM feedback WHERE id = ?", String.class, feedbackId));
    }

    // ============================================================
    // 端点清单
    // ============================================================

    @Test
    void t27EndpointsAreExactlyWhatTheCardNamed() {
        TreeSet<String> found = new TreeSet<>();
        TreeSet<String> forbidden = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                boolean mine = pattern.startsWith("/admin/departments") || pattern.startsWith("/admin/doctors")
                        || pattern.startsWith("/admin/physical-packages")
                        || pattern.startsWith("/admin/physical-items")
                        || pattern.startsWith("/admin/package-types")
                        || pattern.startsWith("/admin/health-articles")
                        || pattern.startsWith("/admin/guide-articles")
                        || pattern.startsWith("/admin/hospital-profile")
                        || pattern.startsWith("/admin/notices")
                        || pattern.startsWith("/admin/feedbacks");
                if (mine) {
                    found.add(verb + " " + pattern);
                }
                boolean navOrInventedDelete = pattern.contains("navigation") || pattern.contains("campus")
                        || pattern.contains("floor")
                        || (pattern.startsWith("/admin/package-types") && "DELETE".equals(verb))
                        || (pattern.startsWith("/admin/feedbacks") && "DELETE".equals(verb))
                        || (pattern.startsWith("/admin/feedbacks") && "PUT".equals(verb));
                if (navOrInventedDelete) {
                    forbidden.add(verb + " " + pattern);
                }
            }
        });

        assertEquals("[DELETE /admin/departments/{id}, DELETE /admin/doctors/{id}, "
                        + "DELETE /admin/guide-articles/{id}, DELETE /admin/health-articles/{id}, "
                        + "DELETE /admin/physical-items/{id}, DELETE /admin/physical-packages/{id}, "
                        + "GET /admin/departments, GET /admin/departments/{id}, GET /admin/doctors, "
                        + "GET /admin/doctors/options, GET /admin/doctors/{id}, GET /admin/feedbacks, "
                        + "GET /admin/feedbacks/{id}, GET /admin/guide-articles, "
                        + "GET /admin/guide-articles/{id}, GET /admin/health-articles, "
                        + "GET /admin/health-articles/{id}, GET /admin/hospital-profile, "
                        + "GET /admin/notices/appointment, GET /admin/notices/delivery, "
                        + "GET /admin/package-types, GET /admin/physical-items, "
                        + "GET /admin/physical-items/{id}, GET /admin/physical-packages, "
                        + "GET /admin/physical-packages/{id}, POST /admin/departments, POST /admin/doctors, "
                        + "POST /admin/feedbacks/{id}/reply, POST /admin/guide-articles, "
                        + "POST /admin/health-articles, POST /admin/package-types, "
                        + "POST /admin/physical-items, POST /admin/physical-packages, "
                        + "PUT /admin/departments/{id}, PUT /admin/doctors/{id}, PUT /admin/guide-articles/{id}, "
                        + "PUT /admin/health-articles/{id}, PUT /admin/hospital-profile, "
                        + "PUT /admin/notices/appointment, PUT /admin/notices/delivery, "
                        + "PUT /admin/package-types/{id}, PUT /admin/physical-items/{id}, "
                        + "PUT /admin/physical-packages/{id}]",
                found.toString(), "本卡开的全部端点");
        assertEquals("[]", forbidden.toString(),
                "医院导航三条独立证据指向不做（附录 A 784 行二期 + 卡片 684 行红线 + 无上传通道）；"
                        + "类型没有删除；反馈只有回复这一个动作，改状态与删单都不在规格里");
    }

    // ============================================================
    // 夹具
    // ============================================================

    private long seedDepartment() {
        jdbcTemplate.update("INSERT INTO department (name, intro, location, sort_order, "
                + "created_at, updated_at, deleted) VALUES (?, '探针简介', '门诊楼 9 层', 90, "
                + "NOW(3), NOW(3), 0)", DEPARTMENT_NAME);
        return maxId("department");
    }

    /**
     * 走真实 API 建一个科室，返回它的 id。
     *
     * <p>id 从库里取而不是从响应里读：{@code 建单响应必须带 id} 这件事已经由
     * {@link #j59_departmentCreateReadUpdateDeleteRoundTrip} 直接钉住了（它读的就是响应的 id），
     * 夹具只要一个能打的靶子。两处都依赖响应 id 的话，一个形状问题会把四条测试一起拖红，
     * 而其中两条测的根本不是这件事。
     */
    private long seedDepartmentViaApi() throws Exception {
        expectData(admin(post("/admin/departments").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", DEPARTMENT_NAME)))));
        return jdbcTemplate.queryForObject("SELECT id FROM department WHERE name = ? "
                + "ORDER BY id DESC LIMIT 1", Long.class, DEPARTMENT_NAME);
    }

    private long seedTitle() {
        jdbcTemplate.update("INSERT INTO title (name, sort_order, created_at, updated_at, deleted) "
                + "VALUES (?, 90, NOW(3), NOW(3), 0)", TITLE_NAME);
        return maxId("title");
    }

    private long seedDoctor(long departmentId) {
        jdbcTemplate.update("INSERT INTO doctor (name, department_id, title_id, intro, specialty, "
                + "created_at, updated_at, deleted) VALUES (?, ?, NULL, '探针简介', '探针方向', "
                + "NOW(3), NOW(3), 0)", DOCTOR_NAME, departmentId);
        return maxId("doctor");
    }

    private long seedSchedule(long doctorId, LocalDate date) {
        jdbcTemplate.update("INSERT INTO schedule (`date`, time_slot, doctor_id, total_slots, "
                + "remaining_slots, created_at, updated_at, deleted) VALUES (?, 'MORNING', ?, 10, 9, "
                + "NOW(3), NOW(3), 0)", date, doctorId);
        return maxId("schedule");
    }

    private long seedPackage() {
        jdbcTemplate.update("INSERT INTO physical_package (name, type_id, price_fen, target_audience, "
                + "items, created_at, updated_at, deleted) VALUES (?, NULL, 28800, '探针', "
                + "'[{\"name\":\"探针项目\",\"priceFen\":3000}]', NOW(3), NOW(3), 0)", PACKAGE_NAME);
        return maxId("physical_package");
    }

    private long seedItem() {
        jdbcTemplate.update("INSERT INTO physical_item (name, category, price_fen, description, "
                + "created_at, updated_at, deleted) VALUES (?, '探针类', 3000, '探针说明', "
                + "NOW(3), NOW(3), 0)", ITEM_NAME);
        return maxId("physical_item");
    }

    private long seedType() {
        jdbcTemplate.update("INSERT INTO package_type (name, created_at, updated_at, deleted) "
                + "VALUES (?, NOW(3), NOW(3), 0)", TYPE_NAME);
        return maxId("package_type");
    }

    private long seedFeedback() {
        jdbcTemplate.update("INSERT INTO feedback (user_id, content, images, status, reply, "
                + "created_at, updated_at, deleted) VALUES (?, ?, '[]', 'PENDING', NULL, "
                + "NOW(3), NOW(3), 0)", probeUserId, FEEDBACK_CONTENT);
        return maxId("feedback");
    }

    private long createPatient(String name) throws Exception {
        String cardNo = "T27" + UUID.randomUUID().toString().substring(0, 12);
        expectData(withToken(post("/user/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", name, "cardNo", cardNo,
                        "idCard", "110101199003001234", "phone", "13900002345",
                        "relation", "SELF"))), patientToken));
        // 同上：id 从库里按 card_no 取（uk_card_no 保证唯一），响应形状由各自的 J 测试负责
        return jdbcTemplate.queryForObject(
                "SELECT id FROM patient WHERE card_no = ?", Long.class, cardNo);
    }

    /** 已就诊完的历史预约：守卫不该管它，但读名字必须读得到。 */
    private long insertHistoricalAppointment(long patientId, long doctorId) {
        jdbcTemplate.update("INSERT INTO appointment (order_no, patient_id, doctor_id, schedule_id, "
                + "status, appointment_time, fee_fen, created_at, updated_at, deleted) "
                + "VALUES (?, ?, ?, 1, 'COMPLETED', NOW(3), 5000, NOW(3), NOW(3), 0)",
                "T27H" + UUID.randomUUID().toString().substring(0, 8), patientId, doctorId);
        return maxId("appointment");
    }

    private long maxId(String table) {
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM " + table, Long.class);
        return id == null ? 0L : id;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long countLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? -1L : value;
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String table : List.of("department", "doctor", "title", "schedule", "appointment",
                "physical_package", "physical_item", "package_type", "physical_appointment",
                "health_article", "guide_article", "hospital_profile", "appointment_notice",
                "delivery_notice", "feedback", "patient", "`user`")) {
            counts.put(table, count("SELECT COUNT(*) FROM " + table));
        }
        return counts;
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + adminToken);
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private List<Map<?, ?>> withTokenResult(MockHttpServletRequestBuilder builder, String token)
            throws Exception {
        return expectList(withToken(builder, token));
    }

    private void expectCode(MockHttpServletRequestBuilder builder, int code) throws Exception {
        Map<?, ?> root = expectRoot(builder);
        assertEquals(code, ((Number) root.get("code")).intValue(),
                "期望业务码 " + code + "，实际：" + root);
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
                .findFirst().orElseThrow(() -> new AssertionError("列表里没有 id=" + id
                        + " 这一行，列表里有 " + rows.size() + " 行、id 依次是 "
                        + rows.stream().map(row -> String.valueOf(row.get("id")))
                                .collect(Collectors.joining(","))));
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
        return jwtUtil.generateToken(adminId, role, role,
                List.of("dashboard", "hospital"), List.of());
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
}
