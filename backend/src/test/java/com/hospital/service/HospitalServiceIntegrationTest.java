package com.hospital.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 医院服务（T24）：J53 医院介绍 → 内容正确、J54 健康百科 → 文章列表正确。
 *
 * <h2>本卡的测试有一半在断言"空"</h2>
 * V6 建的三张表与 announcement 首版都是零行（生产者是 T27，停诊通知甚至没人认领），
 * 所以"接口能用"的证据就是<b>空态回得干净</b>：列表回 {@code []}、简介回 {@code data:null}，
 * 而不是 500 或一条编出来的内容。"内容正确"这一半则靠人工裸插探针行来证，
 * 收尾按 id 删干净——与 T17/T21/T22 同一条做法。
 *
 * <h2>为什么探针数据全走 jdbcTemplate 而不是接口</h2>
 * 本卡<b>没有任何写端点</b>（写侧属 T27 后台），所以除了直连数据库没有第二条路能造出一篇文章。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class HospitalServiceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RequestMappingHandlerMapping handlerMapping;

    private final List<String> cleanupSql = new ArrayList<>();
    private Map<String, Integer> countsBefore;

    @BeforeEach
    void setUp() {
        countsBefore = snapshotCounts();
    }

    @AfterEach
    void cleanupAndAssertNothingLeaks() {
        for (String statement : cleanupSql) {
            jdbcTemplate.update(statement);
        }
        assertEquals(countsBefore, snapshotCounts(), "T24 是纯读卡：探针行删干净后四张表计数必须回到基线");
    }

    // ============================================================
    // J53 医院介绍 → 内容正确
    // ============================================================

    @Test
    void j53_profileIsEmptyUntilT27PublishesIt() throws Exception {
        MvcResult result = mockMvc.perform(get("/user/hospital-profile")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        assertNull(expectRoot(result).get("data"),
                "V6 零行时 data 必须是 null，不是 500、也不是编出来的一家医院");
    }

    @Test
    void j53_profileReturnsExactlyWhatIsStored() throws Exception {
        int id = insertProfile("探针医院", "本医院是一家演示用的区域医疗中心。", "全国文明单位");

        Map<?, ?> data = expectData(mockMvc.perform(get("/user/hospital-profile")
                .header("Authorization", "Bearer " + token())));

        assertEquals((long) id, ((Number) data.get("id")).longValue());
        assertEquals("探针医院", data.get("title"));
        assertEquals("本医院是一家演示用的区域医疗中心。", data.get("intro"),
                "J53「内容正确」= 库里存什么就回什么，一个字都不加工");
        assertEquals("全国文明单位", data.get("honors"));
        assertTrue(data.containsKey("updatedAt"), "简介页要显示「更新于」，所以这一列必须有");
        assertEquals(5, data.keySet().size(), "只有 id/title/intro/honors/updatedAt 五列，不发明地址电话床位数");
    }

    @Test
    void j53_honorsKeyDisappearsWhenNotFilled() throws Exception {
        insertProfile("探针医院乙", "荣誉资质尚未维护。", null);

        Map<?, ?> data = expectData(mockMvc.perform(get("/user/hospital-profile")
                .header("Authorization", "Bearer " + token())));

        assertFalse(data.containsKey("honors"),
                "NON_NULL 让未填的键整个消失，前端据此把这一栏连标题一起隐掉，不留空栏目");
    }

    @Test
    void j53_profileReadsLowestIdBecauseTheAdminPageEditsOneRow() throws Exception {
        int first = insertProfile("第一份", "最早建的那一行才是当前生效的简介。", null);
        insertProfile("第二份", "如果后台哪天变成多行，读取侧的取舍必须仍然是 id 最小那行。", null);

        Map<?, ?> data = expectData(mockMvc.perform(get("/user/hospital-profile")
                .header("Authorization", "Bearer " + token())));

        assertEquals(first, ((Number) data.get("id")).intValue(),
                "卡片 744 行写的是「医院简介管理：编辑」而不是 CRUD，单行语义据此定");
    }

    @Test
    void j53_softDeletedProfileIsNotReadable() throws Exception {
        int id = insertProfile("下架的简介", "内容还在库里，但已经不该对患者显示。", null);
        jdbcTemplate.update("UPDATE hospital_profile SET deleted = 1 WHERE id = ?", id);

        MvcResult result = mockMvc.perform(get("/user/hospital-profile")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andReturn();
        assertNull(expectRoot(result).get("data"), "BaseEntity 的 @TableLogic 让下架即失联");
    }

    // ============================================================
    // J54 健康百科 → 文章列表正确
    // ============================================================

    @Test
    void j54_articleListIsEmptyBeforeAnyArticleExists() throws Exception {
        List<?> rows = expectList(mockMvc.perform(get("/user/health-articles")
                .header("Authorization", "Bearer " + token())));

        assertEquals(List.of(), rows, "零行回空数组；不塞一篇「看着像科普」的文章让页面好看");
    }

    @Test
    void j54_articleListOrdersByPublishTimeDescAndCarriesNoContent() throws Exception {
        int oldId = insertArticle("旧文章", "正文一", "2026-01-05 09:00:00");
        int newId = insertArticle("新文章", "正文二", "2026-09-01 09:00:00");
        int noTimeId = insertArticle("未发布时间", "正文三", null);

        List<Map<?, ?>> rows = expectList(mockMvc.perform(get("/user/health-articles")
                .header("Authorization", "Bearer " + token())));

        assertEquals(3, rows.size());
        assertEquals(List.of("新文章", "旧文章", "未发布时间"),
                rows.stream().map(row -> row.get("title")).toList(),
                "发布时间倒序，没填时间的排最后（MySQL 的 NULL 在 DESC 里落尾）");
        assertEquals(newId, ((Number) rows.get(0).get("id")).intValue());
        assertEquals(oldId, ((Number) rows.get(1).get("id")).intValue());
        assertEquals(noTimeId, ((Number) rows.get(2).get("id")).intValue());
        assertFalse(rows.get(0).containsKey("content"),
                "列表不给正文：与 T14「列表不给余额」同一条做法，那一层本来就没有这个值");
        assertFalse(rows.get(0).containsKey("summary"),
                "V6 没有摘要列，PRD 265 行也只写「展示健康科普文章列表」，不替它发明");
    }

    @Test
    void j54_articleDetailCarriesTheFullText() throws Exception {
        int id = insertArticle("流感防控问答", "正文：勤洗手、开窗通风。", "2026-09-10 08:00:00");

        Map<?, ?> data = expectData(mockMvc.perform(get("/user/health-articles/" + id)
                .header("Authorization", "Bearer " + token())));

        assertEquals("流感防控问答", data.get("title"));
        assertEquals("正文：勤洗手、开窗通风。", data.get("content"), "J54 的详情侧：库里有什么就回什么");
        assertNotNullString(data.get("publishTime"));
        assertEquals(4, data.keySet().size(), "id/title/content/publishTime 四列，没有分类、封面、浏览量");
    }

    @Test
    void j54_unknownArticleIdIsDataNotFound() throws Exception {
        Map<?, ?> root = expectRoot(mockMvc.perform(get("/user/health-articles/999999")
                .header("Authorization", "Bearer " + token())));
        assertEquals(5001, ((Number) root.get("code")).intValue(),
                "内容类资源没有「存在但不属于你」这种可区分状态，所以不存在与读不到同码");
    }

    // ============================================================
    // 就医指南（卡片 680 行）
    // ============================================================

    @Test
    void guidesListAndDetailShareOneShapeAndSplitOnContent() throws Exception {
        int id = insertGuide("预约挂号流程", "第一步 选择科室；第二步 选择医生；第三步 确认支付。");

        List<Map<?, ?>> rows = expectList(mockMvc.perform(get("/user/guides")
                .header("Authorization", "Bearer " + token())));
        assertEquals(1, rows.size());
        assertEquals("预约挂号流程", rows.get(0).get("title"));
        assertFalse(rows.get(0).containsKey("content"));

        Map<?, ?> detail = expectData(mockMvc.perform(get("/user/guides/" + id)
                .header("Authorization", "Bearer " + token())));
        assertEquals("第一步 选择科室；第二步 选择医生；第三步 确认支付。", detail.get("content"));
    }

    @Test
    void guideUnknownIdIsDataNotFound() throws Exception {
        Map<?, ?> root = expectRoot(mockMvc.perform(get("/user/guides/999999")
                .header("Authorization", "Bearer " + token())));
        assertEquals(5001, ((Number) root.get("code")).intValue());
    }

    // ============================================================
    // 停诊通知（卡片 682 行）：只读 announcement 里 STOP_CLINIC 那一族
    // ============================================================

    @Test
    void stopNoticesDoNotLeakOtherAnnouncementKinds() throws Exception {
        int stopId = insertAnnouncement("消化内科周三停诊", "李医生周三全天停诊，已预约患者请改约。",
                "STOP_CLINIC");
        insertAnnouncement("国庆门诊安排", "国庆期间门诊照常。", "NOTICE");
        insertAnnouncement("义诊活动", "本周六院区义诊。", "ACTIVITY");

        List<Map<?, ?>> rows = expectList(mockMvc.perform(get("/user/stop-notices")
                .header("Authorization", "Bearer " + token())));

        assertEquals(1, rows.size(), "另外两类公告不外泄到停诊通知页");
        assertEquals(stopId, ((Number) rows.get(0).get("id")).intValue());
        assertEquals("消化内科周三停诊", rows.get(0).get("title"));
        assertEquals(Set.of("id", "title", "content", "publishTime"), rows.get(0).keySet(),
                "四列就是 announcement 能给的全部：没有 doctorId、没有停诊日期、不区分停诊与调班");
    }

    @Test
    void stopNoticesAreEmptyWhenNobodyHasPublishedOne() throws Exception {
        List<?> rows = expectList(mockMvc.perform(get("/user/stop-notices")
                .header("Authorization", "Bearer " + token())));
        assertEquals(List.of(), rows,
                "T25-T28 四张后台卡里没有任何「发布停诊通知」的条目，所以首版这一页注定是空的");
    }

    // ============================================================
    // 只读性、匿名与端点形状
    // ============================================================

    @Test
    void t24ReadsNothingWritesNothing() throws Exception {
        String token = token();
        for (String path : List.of("/user/hospital-profile", "/user/guides",
                "/user/health-articles", "/user/stop-notices")) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        assertEquals(countsBefore, snapshotCounts(),
                "四把只读端点全程不动任何一张表（与 T10 CatalogService 同一条机械证明）");
    }

    @Test
    void t24AnonymousCannotReadEvenPublicLookingContent() throws Exception {
        mockMvc.perform(get("/user/hospital-profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/user/health-articles")).andExpect(status().isUnauthorized());
    }

    @Test
    void t24EndpointsAreExactlyTheSixAndNavigationHasNone() {
        TreeSet<String> found = new TreeSet<>();
        TreeSet<String> navigation = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();
            Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
            String verb = httpMethods.isEmpty() ? "ANY"
                    : httpMethods.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
            for (String pattern : patterns) {
                if (pattern.startsWith("/user/hospital-profile") || pattern.startsWith("/user/guides")
                        || pattern.startsWith("/user/health-articles")
                        || pattern.startsWith("/user/stop-notices")) {
                    found.add(verb + " " + pattern);
                }
                if (pattern.contains("campus") || pattern.contains("navigation")
                        || pattern.contains("floor") || pattern.contains("/map")) {
                    navigation.add(verb + " " + pattern);
                }
            }
        });

        assertEquals("[GET /user/guides, GET /user/guides/{id}, GET /user/health-articles, "
                + "GET /user/health-articles/{id}, GET /user/hospital-profile, GET /user/stop-notices]",
                found.toString(), "简介一把 + 指南两把 + 文章两把 + 停诊一把");
        assertEquals("[]", navigation.toString(),
                "卡片 679 行的医院导航一个端点都不开：附录 A 第 784 行把多院区支持列为二期，"
                        + "卡片 684 行红线写不做真实地图，而图片没有存储通道");
    }

    // ============================================================
    // 助手
    // ============================================================

    private String tokenCache;

    private String token() throws Exception {
        if (tokenCache == null) {
            String body = mockMvc.perform(post("/auth/wechat-login")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("code", "t24-owner-" + System.nanoTime()))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            Map<?, ?> data = (Map<?, ?>) objectMapper.readValue(body, Map.class).get("data");
            tokenCache = String.valueOf(data.get("token"));
        }
        return tokenCache;
    }

    private int insertProfile(String title, String intro, String honors) {
        jdbcTemplate.update("INSERT INTO hospital_profile (title, intro, honors, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, ?, NOW(3), NOW(3), 0)", title, intro, honors);
        Integer id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM hospital_profile", Integer.class);
        int value = id == null ? 0 : id;
        cleanupSql.add("DELETE FROM hospital_profile WHERE id = " + value);
        return value;
    }

    private int insertGuide(String title, String content) {
        jdbcTemplate.update("INSERT INTO guide_article (title, content, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, NOW(3), NOW(3), 0)", title, content);
        Integer id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM guide_article", Integer.class);
        int value = id == null ? 0 : id;
        cleanupSql.add("DELETE FROM guide_article WHERE id = " + value);
        return value;
    }

    private int insertArticle(String title, String content, String publishTime) {
        jdbcTemplate.update("INSERT INTO health_article (title, content, publish_time, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, ?, NOW(3), NOW(3), 0)",
                title, content, publishTime == null ? null : java.time.LocalDateTime.parse(publishTime.replace(' ', 'T')));
        Integer id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM health_article", Integer.class);
        int value = id == null ? 0 : id;
        cleanupSql.add("DELETE FROM health_article WHERE id = " + value);
        return value;
    }

    private int insertAnnouncement(String title, String content, String type) {
        jdbcTemplate.update("INSERT INTO announcement (title, content, type, publish_time, created_at, updated_at, deleted) "
                        + "VALUES (?, ?, ?, NOW(3), NOW(3), NOW(3), 0)", title, content, type);
        Integer id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM announcement", Integer.class);
        int value = id == null ? 0 : id;
        cleanupSql.add("DELETE FROM announcement WHERE id = " + value);
        return value;
    }

    private Map<String, Integer> snapshotCounts() {
        Map<String, Integer> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("hospital_profile", count("SELECT COUNT(*) FROM hospital_profile"));
        snapshot.put("guide_article", count("SELECT COUNT(*) FROM guide_article"));
        snapshot.put("health_article", count("SELECT COUNT(*) FROM health_article"));
        snapshot.put("announcement", count("SELECT COUNT(*) FROM announcement"));
        return snapshot;
    }

    private int count(String sql) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0 : value.intValue();
    }

    private void assertNotNullString(Object value) {
        assertTrue(value != null && !String.valueOf(value).isBlank(), "期望有值，实际：" + value);
    }

    private Map<?, ?> expectRoot(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), Map.class);
    }

    private Map<?, ?> expectRoot(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return expectRoot(actions.andReturn());
    }

    private Map<?, ?> expectData(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return expectData(actions.andReturn());
    }

    private List<Map<?, ?>> expectList(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return expectList(actions.andReturn());
    }

    private Map<?, ?> expectData(MvcResult result) throws Exception {
        Map<?, ?> root = expectRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof Map, "期望对象型 data，实际：" + data);
        return (Map<?, ?>) data;
    }

    private List<Map<?, ?>> expectList(MvcResult result) throws Exception {
        Map<?, ?> root = expectRoot(result);
        assertEquals(200, ((Number) root.get("code")).intValue(), "期望业务成功，实际：" + root);
        Object data = root.get("data");
        assertTrue(data instanceof List, "期望数组型 data，实际：" + data);
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> rows = (List<Map<?, ?>>) data;
        return rows;
    }
}
