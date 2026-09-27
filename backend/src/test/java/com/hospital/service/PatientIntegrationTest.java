package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.HospitalApplication;
import com.hospital.entity.Patient;
import com.hospital.mapper.PatientMapper;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T08 就诊人管理的必做场景：J17（加密存储）/ J18（重复就诊卡号被拒）/ J19（编辑生效），
 * 外加删除（DoD 的「CRUD 通」+ PRD §9.1，卡片没给 J 编号）与附录 B 第 806 条要求的横向越权回归。
 *
 * <p>需要本机 MySQL 与 Redis 都在跑（与 UserAuthIntegrationTest 同一前提）。
 * 用户用 mock 微信登录现建，不碰 seed 的 4 个 user 和 10 行 patient。
 *
 * <p>本类建的行在 {@link #cleanup()} 里**物理删除**：MyBatis-Plus 的 deleteById 是逻辑删除，
 * 只把 deleted 置 1，行还在，uk_card_no 还占着——那会让下次跑测试撞唯一索引（T07 的 uk_openid 同源教训）。
 * 本类自己就会造出软删行（既用裸 SQL 插，也用 DELETE 端点删），所以清理必须走裸 SQL。
 */
@SpringBootTest(classes = HospitalApplication.class)
@AutoConfigureMockMvc
class PatientIntegrationTest {

    private static final Random RANDOM = new Random();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PatientMapper patientMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CryptoService cryptoService;
    @Autowired private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<String> createdCardNos = new ArrayList<>();

    /** 按 card_no 清而不是按 id：软删占位行是裸 SQL 插的，没有回填 id 到本类 */
    @AfterEach
    void cleanup() {
        for (String cardNo : createdCardNos) {
            jdbcTemplate.update("DELETE FROM patient WHERE card_no = ?", cardNo);
        }
        createdCardNos.clear();
        for (Long id : createdUserIds) {
            jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", id);
        }
        createdUserIds.clear();
    }

    // ============================================================
    // J17 添加就诊人 → 数据加密存储
    // ============================================================

    @Test
    void j17_createPatient_storesIdCardAndPhoneEncrypted() throws Exception {
        String token = newUserToken("j17");
        String cardNo = randomCardNo();
        String idCard = randomIdCard();
        String phone = randomPhone();
        // 本用例要断言响应体，所以内联发请求而不走 createPatient() 助手；
        // 助手里那句 createdCardNos.add 也就一并被绕过了，必须在这里手动登记，
        // 否则 cleanup 删不到这行，每跑一次就往开发库里留一条占着 uk_card_no 的垃圾。
        createdCardNos.add(cardNo);

        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("张三丰", cardNo, idCard, phone, "SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("张三丰"))
                .andExpect(jsonPath("$.data.cardNo").value(cardNo))
                .andExpect(jsonPath("$.data.relation").value("SELF"))
                // 出库必须打码：卡号是明文（卡片 372 行要求列表展示），身份证/手机号不是
                .andExpect(jsonPath("$.data.idCard").value(maskIdCard(idCard)))
                .andExpect(jsonPath("$.data.phone").value(maskPhone(phone)));

        Patient row = selectByCardNo(cardNo);
        assertNotNull(row, "就诊人应已落库");
        assertNotEquals(idCard, row.getIdCard(), "身份证不能明文落库（卡片 379 行 ⚠️）");
        assertNotEquals(phone, row.getPhone(), "手机号不能明文落库");
        assertFalse(row.getIdCard().contains(idCard.substring(0, 6)), "密文里也不该出现明文片段");
        assertEquals(idCard, cryptoService.decrypt(row.getIdCard()), "解密后必须还原成原身份证");
        assertEquals(phone, cryptoService.decrypt(row.getPhone()), "解密后必须还原成原手机号");
    }

    /** 反过来验：列表里必须能看到刚建的人，且只看到自己的 */
    @Test
    void j17_listReturnsOwnPatientsOnly() throws Exception {
        String tokenA = newUserToken("j17l-a");
        String tokenB = newUserToken("j17l-b");
        String cardNoA = randomCardNo();
        String cardNoB = randomCardNo();
        createPatient(tokenA, cardNoA);
        createPatient(tokenB, cardNoB);

        mockMvc.perform(get("/user/patients").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].cardNo").value(cardNoA));
    }

    @Test
    void j17_invalidIdCardOrRelation_rejectedWith400() throws Exception {
        String token = newUserToken("j17v");

        // 17 位身份证：格式校验必须在入库前拦下，否则脏数据会一路流到 T12 的实名校验
        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("李四", randomCardNo(), "1101011990030771", randomPhone(), "SELF"))))
                .andExpect(status().isBadRequest());

        // 关系取值只能来自 V1__init.sql:31 的 5 个，编一个 FRIEND 必须被拒
        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("李四", randomCardNo(), randomIdCard(), randomPhone(), "FRIEND"))))
                .andExpect(status().isBadRequest());
    }

    // ============================================================
    // J18 重复就诊卡号 → 被拒（R1：前置查 + 唯一索引兜底，两层都要证明存在）
    // ============================================================

    /** 第一层：前置查命中，给友好提示 1004 */
    @Test
    void j18_duplicateCardNo_rejectedByPreCheck() throws Exception {
        String token = newUserToken("j18a");
        String cardNo = randomCardNo();
        createPatient(token, cardNo);

        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("王五", cardNo, randomIdCard(), randomPhone(), "CHILD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("就诊卡号已存在"));

        assertEquals(1, countByCardNo(cardNo), "被拒的那次不该留下第二行");
    }

    /** 跨用户也算重复：卡号是全局唯一，不是"每人唯一" */
    @Test
    void j18_duplicateCardNoAcrossUsers_alsoRejected() throws Exception {
        String tokenA = newUserToken("j18b-a");
        String tokenB = newUserToken("j18b-b");
        String cardNo = randomCardNo();
        createPatient(tokenA, cardNo);

        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("赵六", cardNo, randomIdCard(), randomPhone(), "SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }

    /**
     * 软删行占了卡号，但那是**本人**删掉的 → 复活原行，不是 1004。
     *
     * <p>用裸 SQL 插一行 {@code deleted = 1} 来**确定性地**造出这个前提：
     * MyBatis-Plus 的 {@code @TableLogic} 会自动给查询补 {@code deleted = 0}，
     * 所以 service 的前置查看不见这行；而 {@code uk_card_no} 不认 deleted 列，索引仍被占着。
     * 这正是"删错了想加回来"的真实处境，也是 PatientMapper 里那条手写 UPDATE 存在的理由。
     */
    @Test
    void j18_cardNoHeldByOwnSoftDeletedRow_isRevivedNotRejected() throws Exception {
        String token = newUserToken("j18c");
        Long userId = userIdOf(token);
        String cardNo = randomCardNo();

        jdbcTemplate.update(
                "INSERT INTO patient (user_id, name, id_card, phone, relation, card_no, deleted) "
                        + "VALUES (?, '软删占位', 'x', 'x', 'SELF', ?, 1)",
                userId, cardNo);
        createdCardNos.add(cardNo);
        Long placeholderId = jdbcTemplate.queryForObject(
                "SELECT id FROM patient WHERE card_no = ?", Long.class, cardNo);

        assertEquals(0, countByCardNo(cardNo), "前置查看不见软删行，这正是本用例要的前提");

        String idCard = randomIdCard();
        String phone = randomPhone();
        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("孙七", cardNo, idCard, phone, "CHILD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("孙七"))
                .andExpect(jsonPath("$.data.relation").value("CHILD"))
                // 复活的必须是原来那一行：id 变了就等于把历史单据的 patient_id 甩成了孤儿
                .andExpect(jsonPath("$.data.id").value(placeholderId));

        assertEquals(1, countByCardNo(cardNo), "复活后该行应重新可见");
        assertEquals(0, deletedFlagOf(cardNo), "deleted 必须被置回 0");
        Patient revived = selectByCardNo(cardNo);
        assertEquals(idCard, cryptoService.decrypt(revived.getIdCard()), "复活时身份证应按新填的覆盖并加密");
        assertEquals(phone, cryptoService.decrypt(revived.getPhone()), "复活时手机号应按新填的覆盖并加密");
    }

    /**
     * 软删行占了卡号，且属于**别人** → 仍然 1004。
     *
     * <p>这一条同时证明了两件事：卡号不会因为原主人删掉就外放（就诊卡号是实体卡号，
     * 放给别人用会让历史预约/缴费/报告的卡号归属错乱），以及 create 的 catch 分支
     * 真的会把 DuplicateKeyException 翻成 1004 而不是漏成 500——因为前置查看不见软删行，
     * 只能靠唯一索引兜住。
     */
    @Test
    void j18_cardNoHeldByOthersSoftDeletedRow_fallsBackToUniqueIndex() throws Exception {
        String tokenA = newUserToken("j18d-a");
        String tokenB = newUserToken("j18d-b");
        String cardNo = randomCardNo();

        // 占位行归 B，且是 B 已删除的
        jdbcTemplate.update(
                "INSERT INTO patient (user_id, name, id_card, phone, relation, card_no, deleted) "
                        + "VALUES (?, '别人的软删行', 'x', 'x', 'SELF', ?, 1)",
                userIdOf(tokenB), cardNo);
        createdCardNos.add(cardNo);

        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("孙七", cardNo, randomIdCard(), randomPhone(), "SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("就诊卡号已存在"));

        assertEquals(1, deletedFlagOf(cardNo), "被拒的那次不该把别人的软删行复活");
    }

    /** 兜底层的物理事实：同一卡号硬插两次，MySQL 必须拒绝 */
    @Test
    void j18_uniqueIndexReallyExists() {
        String cardNo = randomCardNo();
        createdCardNos.add(cardNo);
        jdbcTemplate.update(
                "INSERT INTO patient (user_id, name, id_card, phone, relation, card_no) "
                        + "VALUES (1, '索引探针', 'x', 'x', 'SELF', ?)", cardNo);

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(
                "INSERT INTO patient (user_id, name, id_card, phone, relation, card_no) "
                        + "VALUES (1, '索引探针2', 'x', 'x', 'SELF', ?)", cardNo));
    }

    // ============================================================
    // J19 编辑就诊人 → 信息更新
    // ============================================================

    @Test
    void j19_updatePatient_changesFieldsAndKeepsBlankOnesUntouched() throws Exception {
        String token = newUserToken("j19a");
        String cardNo = randomCardNo();
        String idCard = randomIdCard();
        String phone = randomPhone();
        Long patientId = createPatient(token, cardNo, "周八", idCard, phone, "SELF");

        Patient before = patientMapper.selectById(patientId);
        String newPhone = randomPhone();

        mockMvc.perform(put("/user/patients/" + patientId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload("周八九", cardNo, "", newPhone, "SPOUSE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("周八九"))
                .andExpect(jsonPath("$.data.relation").value("SPOUSE"))
                .andExpect(jsonPath("$.data.phone").value(maskPhone(newPhone)))
                // idCard 传空串 = 不改，回给前端的仍是原打码值
                .andExpect(jsonPath("$.data.idCard").value(maskIdCard(idCard)));

        Patient after = patientMapper.selectById(patientId);
        assertEquals("周八九", after.getName());
        assertEquals("SPOUSE", after.getRelation());
        assertEquals(newPhone, cryptoService.decrypt(after.getPhone()), "新手机号应加密落库");
        assertEquals(before.getIdCard(), after.getIdCard(),
                "留空的身份证必须原封不动——连密文都不该变（AES-GCM 每次加密都不同，"
                        + "若这里变了说明服务把空串当成新值重新加密了一遍）");
    }

    @Test
    void j19_updateCardNoToAnExistingOne_rejected() throws Exception {
        String token = newUserToken("j19b");
        String cardNoA = randomCardNo();
        String cardNoB = randomCardNo();
        Long idA = createPatient(token, cardNoA);
        createPatient(token, cardNoB);

        // 改成别人的（其实是自己另一个就诊人的）卡号 → 1004
        mockMvc.perform(put("/user/patients/" + idA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload("张三", cardNoB, "", "", "SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));

        // 改成自己的当前卡号（值没变）不该被自己拦下：excludeId 就是为这一刻存在的
        mockMvc.perform(put("/user/patients/" + idA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload("张三改", cardNoA, "", "", "SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("张三改"));
    }

    // ============================================================
    // 删除（卡片「测试场景」只列了 J17/J18/J19，删除来自 DoD 的「CRUD 通」+ PRD §9.1，
    // 没有 J 编号可挂，所以用描述性方法名，不编一个 J20 出来）
    // ============================================================

    @Test
    void deleteOwnPatient_hidesItFromListDetailAndUpdate() throws Exception {
        String token = newUserToken("del-a");
        String cardNo = randomCardNo();
        Long patientId = createPatient(token, cardNo);

        mockMvc.perform(delete("/user/patients/" + patientId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 逻辑删：行必须还在库里（历史单据的 patient_id 仍指着它），但对接口彻底不可见
        assertEquals(1, deletedFlagOf(cardNo), "应是逻辑删（deleted=1），不是物理删");
        assertEquals(1, rawCountByCardNo(cardNo), "行必须还在，物理删会甩下一堆孤儿单据");
        assertEquals(0, countByCardNo(cardNo), "但对 MyBatis-Plus 的查询已不可见");

        mockMvc.perform(get("/user/patients").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/user/patients/" + patientId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        // 删完还能改 = 删除是假的
        mockMvc.perform(put("/user/patients/" + patientId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload("改不动", "", "", "", "SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void deleteOthersPatient_returns1003_not403() throws Exception {
        String tokenA = newUserToken("del-b-a");
        String tokenB = newUserToken("del-b-b");
        String cardNo = randomCardNo();
        Long patientIdOfA = createPatient(tokenA, cardNo);

        mockMvc.perform(delete("/user/patients/" + patientIdOfA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                // 不是 403：403 会确认"这个 id 存在"，等于帮攻击者枚举别人的就诊人
                .andExpect(jsonPath("$.code").value(1003));

        assertEquals(0, deletedFlagOf(cardNo), "越权删除必须没生效");
    }

    /** 删错了能加回来：本人用同一卡号重新添加 = 复活原行（id 不变），信息按新填的覆盖 */
    @Test
    void reAddSameCardNoAfterDelete_revivesTheSameRow() throws Exception {
        String token = newUserToken("del-c");
        String cardNo = randomCardNo();
        Long originalId = createPatient(token, cardNo, "删除前", randomIdCard(), randomPhone(), "SELF");

        mockMvc.perform(delete("/user/patients/" + originalId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String newIdCard = randomIdCard();
        String newPhone = randomPhone();
        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("删除后", cardNo, newIdCard, newPhone, "PARENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(originalId))
                .andExpect(jsonPath("$.data.name").value("删除后"))
                .andExpect(jsonPath("$.data.relation").value("PARENT"))
                .andExpect(jsonPath("$.data.idCard").value(maskIdCard(newIdCard)))
                .andExpect(jsonPath("$.data.phone").value(maskPhone(newPhone)));

        assertEquals(1, rawCountByCardNo(cardNo), "复活不等于新建，库里必须仍然只有一行");
        assertEquals(0, deletedFlagOf(cardNo));
    }

    /** 卡号不外放：原主人删掉之后，别人仍然加不了这个卡号 */
    @Test
    void reAddSameCardNoByAnotherUser_afterOwnerDeleted_stillRejected() throws Exception {
        String tokenA = newUserToken("del-d-a");
        String tokenB = newUserToken("del-d-b");
        String cardNo = randomCardNo();
        Long patientIdOfA = createPatient(tokenA, cardNo);

        mockMvc.perform(delete("/user/patients/" + patientIdOfA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload("想捡卡号", cardNo, randomIdCard(), randomPhone(), "SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));

        assertEquals(1, deletedFlagOf(cardNo), "别人的添加尝试不该把 A 删掉的行复活");
    }

    /**
     * 编辑时撞上自己的软删行 → 1004，**不复活**。
     *
     * <p>复活只属于"新增"这一个动作。改一个活着的就诊人的卡号，没有理由让另一个已删除的
     * 就诊人凭空回来——那会出现"删掉的人又回来了"，而且回来的行 id 与正在编辑的行不是同一条。
     */
    @Test
    void updateCardNoToOwnSoftDeletedRow_rejectedWithoutReviving() throws Exception {
        String token = newUserToken("del-e");
        Long userId = userIdOf(token);
        String liveCardNo = randomCardNo();
        String deletedCardNo = randomCardNo();
        Long liveId = createPatient(token, liveCardNo);

        jdbcTemplate.update(
                "INSERT INTO patient (user_id, name, id_card, phone, relation, card_no, deleted) "
                        + "VALUES (?, '已删的那个', 'x', 'x', 'SELF', ?, 1)",
                userId, deletedCardNo);
        createdCardNos.add(deletedCardNo);

        mockMvc.perform(put("/user/patients/" + liveId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload("换个卡号", deletedCardNo, "", "", "SELF"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));

        assertEquals(1, deletedFlagOf(deletedCardNo), "编辑不该复活已删行");
        assertEquals(liveCardNo, patientMapper.selectById(liveId).getCardNo(), "被拒后原卡号必须没变");
    }

    // ============================================================
    // 横向越权（附录 B 第 806 条：小程序端新接口是否强制注入 userId 归属校验）
    // ============================================================

    @Test
    void otherUsersPatient_isInvisibleAndUneditable() throws Exception {
        String tokenA = newUserToken("own-a");
        String tokenB = newUserToken("own-b");
        Long patientIdOfA = createPatient(tokenA, randomCardNo());

        // 读详情：不是 403 而是 1003——403 会确认"这条记录存在"，等于泄露别人的就诊人 id 有效
        mockMvc.perform(get("/user/patients/" + patientIdOfA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        // 改：同样 1003，且库里的行必须没被动过
        mockMvc.perform(put("/user/patients/" + patientIdOfA)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updatePayload("被我改了", "", "", "", "OTHER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));

        Patient untouched = patientMapper.selectById(patientIdOfA);
        assertNotEquals("被我改了", untouched.getName(), "越权改写必须没生效");
    }

    @Test
    void anonymousAndStaffTokenCannotReachPatientEndpoints() throws Exception {
        // 未认证 401（小程序 utils/request.js 靠 401 跳登录页）
        mockMvc.perform(get("/user/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 员工 token 403：/user/** 的角色隔离覆盖了本卡新增的路径，不需要为 T08 改 SecurityConfig
        String adminToken = newStaffToken();
        mockMvc.perform(get("/user/patients").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));

        // DELETE 也要覆盖到。用一个必然不存在的 id：万一角色隔离哪天被改坏，
        // 这条用例会走到 service 的 1003 而不是真把 seed 的 1 号就诊人删掉。
        mockMvc.perform(delete("/user/patients/999999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/user/patients/999999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4001));
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

    private Long createPatient(String token, String cardNo) throws Exception {
        return createPatient(token, cardNo, "测试就诊人", randomIdCard(), randomPhone(), "SELF");
    }

    private Long createPatient(String token, String cardNo, String name,
                               String idCard, String phone, String relation) throws Exception {
        createdCardNos.add(cardNo);
        String body = mockMvc.perform(post("/user/patients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(createPayload(name, cardNo, idCard, phone, relation))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> root = objectMapper.readValue(body, Map.class);
        return ((Number) ((Map<?, ?>) root.get("data")).get("id")).longValue();
    }

    private Patient selectByCardNo(String cardNo) {
        return patientMapper.selectOne(
                new LambdaQueryWrapper<Patient>().eq(Patient::getCardNo, cardNo));
    }

    private int countByCardNo(String cardNo) {
        Long count = patientMapper.selectCount(
                new LambdaQueryWrapper<Patient>().eq(Patient::getCardNo, cardNo));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 以下两个断言辅助走裸 SQL，因为 {@code @TableLogic} 让 MyBatis-Plus 看不见软删行，
     * 而"删了之后行还在、只是 deleted=1"恰恰是必须证明的事。
     */
    private int rawCountByCardNo(String cardNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient WHERE card_no = ?", Integer.class, cardNo);
        return count == null ? 0 : count;
    }

    private int deletedFlagOf(String cardNo) {
        Integer flag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM patient WHERE card_no = ?", Integer.class, cardNo);
        return flag == null ? -1 : flag;
    }

    /** 每次跑用新卡号：唯一索引是全局的，复用固定值会让重跑变成假失败 */
    private static String randomCardNo() {
        return "T08" + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
    }

    /** 只做格式合法的假号（18 位、末位数字）。不校验 GB11643 校验位——没有任何规格要求，
     *  而且校验位算法一旦写错，测试就会变成"用错的规则验错的号" */
    private static String randomIdCard() {
        return "11010119900307" + String.format("%04d", RANDOM.nextInt(10_000));
    }

    private static String randomPhone() {
        return "139" + String.format("%08d", RANDOM.nextInt(100_000_000));
    }

    /** 期望值在测试里独立算，不调用 MaskUtil——用生产代码算期望值等于自己给自己判卷 */
    private static String maskPhone(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private static String maskIdCard(String idCard) {
        return idCard.substring(0, 4) + "**********" + idCard.substring(14);
    }

    private Map<String, ?> createPayload(String name, String cardNo, String idCard,
                                         String phone, String relation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("cardNo", cardNo);
        payload.put("idCard", idCard);
        payload.put("phone", phone);
        payload.put("relation", relation);
        return payload;
    }

    private Map<String, ?> updatePayload(String name, String cardNo, String idCard,
                                         String phone, String relation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("cardNo", cardNo);
        payload.put("idCard", idCard);
        payload.put("phone", phone);
        payload.put("relation", relation);
        return payload;
    }

    private String json(Map<String, ?> payload) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
    }
}
