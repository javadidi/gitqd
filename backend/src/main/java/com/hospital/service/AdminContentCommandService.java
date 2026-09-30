package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminArticleResponse;
import com.hospital.dto.GuideArticleSaveRequest;
import com.hospital.dto.HealthArticleSaveRequest;
import com.hospital.dto.HospitalProfileResponse;
import com.hospital.dto.NoticeResponse;
import com.hospital.dto.NoticeSaveRequest;
import com.hospital.dto.ProfileSaveRequest;
import com.hospital.entity.AppointmentNotice;
import com.hospital.entity.DeliveryNotice;
import com.hospital.entity.GuideArticle;
import com.hospital.entity.HealthArticle;
import com.hospital.entity.HospitalProfile;
import com.hospital.exception.BizException;
import com.hospital.mapper.AppointmentNoticeMapper;
import com.hospital.mapper.DeliveryNoticeMapper;
import com.hospital.mapper.GuideArticleMapper;
import com.hospital.mapper.HealthArticleMapper;
import com.hospital.mapper.HospitalProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端内容类写入（T27 卡片 741/742/744/745/746 行 / PRD §4.5.6–§4.5.11 的 419–438 行）：
 * 健康百科、就诊指南、医院简介、预约须知、病案配送须知。
 *
 * <h2>三类形状，三套语义</h2>
 * <ul>
 *   <li><b>文章/指南</b>＝列表 + 详情 + 增删改（卡片写 CRUD，PRD 420/424 行有列表页），软删；</li>
 *   <li><b>简介/两条须知</b>＝<b>单行 upsert</b>：卡片用的是「编辑」而不是 CRUD，
 *       与 T24 读取侧"按 id 升序取第一行"完全对齐。没有第二条的语义，所以也没有列表端点。</li>
 * </ul>
 *
 * <h2>编辑类写入是 upsert 而不是"必须先有人建第一行"</h2>
 * V7 的迁移文件只建表不插行（除了两条须知从硬编码搬来的 seed 行），
 * 所以 {@code hospital_profile} 首版是空表。如果 PUT 在"还没有行"时报 5001，
 * 后台那个编辑页就打不开、也没法救——空表 + 只能改不能建的接口，是一个自锁死的设计。
 * 因此 PUT 走"没有就建、有就改"，两条须知同理。
 *
 * <h2>为什么不新开 GET 详情</h2>
 * 编辑页要回填当前内容，所以简介与两条须知各有一把 GET（读那唯一一行）。
 * 文章与指南的详情走 {@code GET .../{id}}，因为它们的 id 是列表点进来的。
 */
@Service
public class AdminContentCommandService {

    private final HealthArticleMapper articleMapper;
    private final GuideArticleMapper guideMapper;
    private final HospitalProfileMapper profileMapper;
    private final AppointmentNoticeMapper appointmentNoticeMapper;
    private final DeliveryNoticeMapper deliveryNoticeMapper;
    private final HospitalContentService hospitalContentService;

    public AdminContentCommandService(HealthArticleMapper articleMapper,
                                      GuideArticleMapper guideMapper,
                                      HospitalProfileMapper profileMapper,
                                      AppointmentNoticeMapper appointmentNoticeMapper,
                                      DeliveryNoticeMapper deliveryNoticeMapper,
                                      HospitalContentService hospitalContentService) {
        this.articleMapper = articleMapper;
        this.guideMapper = guideMapper;
        this.profileMapper = profileMapper;
        this.appointmentNoticeMapper = appointmentNoticeMapper;
        this.deliveryNoticeMapper = deliveryNoticeMapper;
        this.hospitalContentService = hospitalContentService;
    }

    // ============================================================
    // 健康百科（卡片 741 行）
    // ============================================================

    public List<AdminArticleResponse> listArticles() {
        List<AdminArticleResponse> result = new ArrayList<>();
        for (HealthArticle row : articleMapper.selectList(new LambdaQueryWrapper<HealthArticle>()
                .orderByDesc(HealthArticle::getPublishTime).orderByDesc(HealthArticle::getId))) {
            result.add(toArticleResponse(row));
        }
        return result;
    }

    public AdminArticleResponse articleDetail(Long id) {
        return toArticleResponse(requireArticle(id));
    }

    @AuditLog(action = "CREATE_HEALTH_ARTICLE", targetType = "health_article")
    @Transactional
    public AdminArticleResponse createArticle(HealthArticleSaveRequest request) {
        HealthArticle entity = new HealthArticle();
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        entity.setCategory(blankToNull(request.getCategory()));
        entity.setPublishTime(LocalDateTime.now());
        articleMapper.insert(entity);
        return toArticleResponse(articleMapper.selectById(entity.getId()));
    }

    @AuditLog(action = "UPDATE_HEALTH_ARTICLE", targetType = "health_article")
    @Transactional
    public AdminArticleResponse updateArticle(@AuditTarget Long id, HealthArticleSaveRequest request) {
        HealthArticle existing = requireArticle(id);
        // 用 LambdaUpdateWrapper 显式 SET（含 null）：updateById 会跳过 null 字段，
        // "把分类抹掉"这类编辑就会点下去没反应。
        // 反面，publishTime 要把原值显式写回去——修个错字不该把文章挪到"刚刚发布"，
        // 患者侧那个按发布时间倒序的列表会因此重排，那是改写排序事实。
        articleMapper.update(null, new LambdaUpdateWrapper<HealthArticle>()
                .eq(HealthArticle::getId, id)
                .set(HealthArticle::getTitle, request.getTitle().trim())
                .set(HealthArticle::getContent, request.getContent())
                .set(HealthArticle::getCategory, blankToNull(request.getCategory()))
                .set(HealthArticle::getPublishTime, existing.getPublishTime()));
        return toArticleResponse(articleMapper.selectById(id));
    }

    @AuditLog(action = "DELETE_HEALTH_ARTICLE", targetType = "health_article")
    @Transactional
    public void deleteArticle(@AuditTarget Long id) {
        requireArticle(id);
        articleMapper.deleteById(id);
    }

    // ============================================================
    // 就诊指南（卡片 742 行）
    // ============================================================

    public List<AdminArticleResponse> listGuides() {
        List<AdminArticleResponse> result = new ArrayList<>();
        for (GuideArticle row : guideMapper.selectList(
                new LambdaQueryWrapper<GuideArticle>().orderByAsc(GuideArticle::getId))) {
            AdminArticleResponse response = new AdminArticleResponse();
            response.setId(row.getId());
            response.setTitle(row.getTitle());
            response.setContent(row.getContent());
            response.setCreatedAt(row.getCreatedAt());
            response.setUpdatedAt(row.getUpdatedAt());
            result.add(response);
        }
        return result;
    }

    public AdminArticleResponse guideDetail(Long id) {
        GuideArticle row = requireGuide(id);
        AdminArticleResponse response = new AdminArticleResponse();
        response.setId(row.getId());
        response.setTitle(row.getTitle());
        response.setContent(row.getContent());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    @AuditLog(action = "CREATE_GUIDE_ARTICLE", targetType = "guide_article")
    @Transactional
    public AdminArticleResponse createGuide(GuideArticleSaveRequest request) {
        GuideArticle entity = new GuideArticle();
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        guideMapper.insert(entity);
        return guideDetail(entity.getId());
    }

    @AuditLog(action = "UPDATE_GUIDE_ARTICLE", targetType = "guide_article")
    @Transactional
    public AdminArticleResponse updateGuide(@AuditTarget Long id, GuideArticleSaveRequest request) {
        requireGuide(id);
        GuideArticle entity = new GuideArticle();
        entity.setId(id);
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        guideMapper.updateById(entity);
        return guideDetail(id);
    }

    @AuditLog(action = "DELETE_GUIDE_ARTICLE", targetType = "guide_article")
    @Transactional
    public void deleteGuide(@AuditTarget Long id) {
        requireGuide(id);
        guideMapper.deleteById(id);
    }

    // ============================================================
    // 医院简介（卡片 744 行「编辑」，单行 upsert）
    // ============================================================

    /** 后台编辑页的回填值；没配置时回 null，页面显示"还没有内容"而不是报错。 */
    public HospitalProfileResponse profile() {
        HospitalProfile row = firstProfileRow();
        if (row == null) {
            return null;
        }
        return toProfileResponse(row);
    }

    @AuditLog(action = "UPDATE_HOSPITAL_PROFILE", targetType = "hospital_profile")
    @Transactional
    public HospitalProfileResponse saveProfile(ProfileSaveRequest request) {
        HospitalProfile existing = firstProfileRow();
        HospitalProfile entity = new HospitalProfile();
        entity.setTitle(request.getTitle().trim());
        entity.setIntro(request.getIntro());
        entity.setHonors(blankToNull(request.getHonors()));
        if (existing == null) {
            profileMapper.insert(entity);
        } else {
            // 显式 SET：荣誉资质要能被清空（updateById 会跳过那个 null）
            profileMapper.update(null, new LambdaUpdateWrapper<HospitalProfile>()
                    .eq(HospitalProfile::getId, existing.getId())
                    .set(HospitalProfile::getTitle, entity.getTitle())
                    .set(HospitalProfile::getIntro, entity.getIntro())
                    .set(HospitalProfile::getHonors, entity.getHonors()));
        }
        // 审计的 target_id 在这里是 NULL：切面只认方法入参上的 @AuditTarget，
        // 而单行表的行 id 是库里定的、不在参数上。这与 T11 的 CREATE_SCHEDULE 是同一条已知行为，
        // 差别是这里连 UPDATE 也拿不到 id——单行语义下"改的是哪一行"本就由表决定，读审计不受影响。
        return profile();
    }

    // ============================================================
    // 两条须知（卡片 745/746 行「编辑」，单行 upsert）
    // ============================================================

    /**
     * 预约须知。<b>读委托给 {@link HospitalContentService}</b>——患者侧
     * {@code GET /user/notices/appointment} 用的就是那一份，
     * 后台编辑页回填的也必须是同一份；两处各写一遍就会出现"改了却没生效"这类查不出来的 bug。
     */
    public NoticeResponse appointmentNotice() {
        return hospitalContentService.appointmentNotice();
    }

    @AuditLog(action = "UPDATE_APPOINTMENT_NOTICE", targetType = "appointment_notice")
    @Transactional
    public NoticeResponse saveAppointmentNotice(NoticeSaveRequest request) {
        List<AppointmentNotice> rows = appointmentNoticeMapper.selectList(
                new LambdaQueryWrapper<AppointmentNotice>().orderByAsc(AppointmentNotice::getId));
        AppointmentNotice entity = new AppointmentNotice();
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        if (rows.isEmpty()) {
            appointmentNoticeMapper.insert(entity);
        } else {
            entity.setId(rows.get(0).getId());
            appointmentNoticeMapper.updateById(entity);
        }
        return appointmentNotice();
    }

    public NoticeResponse deliveryNotice() {
        return hospitalContentService.deliveryNotice();
    }

    @AuditLog(action = "UPDATE_DELIVERY_NOTICE", targetType = "delivery_notice")
    @Transactional
    public NoticeResponse saveDeliveryNotice(NoticeSaveRequest request) {
        List<DeliveryNotice> rows = deliveryNoticeMapper.selectList(
                new LambdaQueryWrapper<DeliveryNotice>().orderByAsc(DeliveryNotice::getId));
        DeliveryNotice entity = new DeliveryNotice();
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        if (rows.isEmpty()) {
            deliveryNoticeMapper.insert(entity);
        } else {
            entity.setId(rows.get(0).getId());
            deliveryNoticeMapper.updateById(entity);
        }
        return deliveryNotice();
    }

    // ============================================================
    // 内部
    // ============================================================

    private HealthArticle requireArticle(Long id) {
        HealthArticle row = id == null ? null : articleMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return row;
    }

    private GuideArticle requireGuide(Long id) {
        GuideArticle row = id == null ? null : guideMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return row;
    }

    private HospitalProfile firstProfileRow() {
        List<HospitalProfile> rows = profileMapper.selectList(
                new LambdaQueryWrapper<HospitalProfile>().orderByAsc(HospitalProfile::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AdminArticleResponse toArticleResponse(HealthArticle row) {
        AdminArticleResponse response = new AdminArticleResponse();
        response.setId(row.getId());
        response.setTitle(row.getTitle());
        response.setContent(row.getContent());
        response.setCategory(row.getCategory());
        response.setPublishTime(row.getPublishTime());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    private HospitalProfileResponse toProfileResponse(HospitalProfile row) {
        HospitalProfileResponse response = new HospitalProfileResponse();
        response.setId(row.getId());
        response.setTitle(row.getTitle());
        response.setIntro(row.getIntro());
        response.setHonors(row.getHonors());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
