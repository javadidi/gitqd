package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.GuideArticleResponse;
import com.hospital.dto.HealthArticleResponse;
import com.hospital.dto.HospitalProfileResponse;
import com.hospital.dto.NoticeResponse;
import com.hospital.dto.StopNoticeResponse;
import com.hospital.enums.AnnouncementType;
import com.hospital.entity.Announcement;
import com.hospital.entity.AppointmentNotice;
import com.hospital.entity.DeliveryNotice;
import com.hospital.entity.GuideArticle;
import com.hospital.entity.HealthArticle;
import com.hospital.entity.HospitalProfile;
import com.hospital.exception.BizException;
import com.hospital.mapper.AnnouncementMapper;
import com.hospital.mapper.AppointmentNoticeMapper;
import com.hospital.mapper.DeliveryNoticeMapper;
import com.hospital.mapper.GuideArticleMapper;
import com.hospital.mapper.HealthArticleMapper;
import com.hospital.mapper.HospitalProfileMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 医院服务只读查询（T24 卡片 678/680/681/682 行 + PRD 251–269 行）。
 *
 * <h2>四块内容、两种"空"</h2>
 * 医院简介 / 就诊指南 / 健康百科三块读 V6 新建的三张表，停诊通知读 V1 就有的 {@code announcement}。
 * 四块<b>首版全部零行</b>，但性质不同：前三块的生产者点名是 T27
 * （卡片 741/742/744 行「健康百科管理：CRUD」「就诊指南管理：CRUD」「医院简介管理：编辑」），
 * 属"有主的空"（与 T22 的三张体检表同类）；停诊通知在 T25–T28 的四张后台卡里
 * 找不到任何"发布停诊通知"的条目，属"没主的空"（与 T16 的 queue_status 同类）。
 * 两种都不往 seed 塞内容：医院介绍是要对外负责的事实陈述，
 * 健康文章是医疗建议，编一条比留白坏得多。
 *
 * <h2>停诊通知为什么落在 announcement、而不是从排班取消派生</h2>
 * 另一条路是读"被取消的排班"（T11 的取消是软删 {@code schedule.deleted=1}）。放弃它的理由具体：
 * {@code schedule} 没有原因列，"停诊"与"调班"在数据上不可区分（卡片 682 行两个词都要展示），
 * 也没有任何一列能当通知的标题与正文——真要走这条路就得由后端拼一句话当公告正文，
 * 那是替医院发布它没写过的声明。{@code announcement} 的 {@code type} 列（V1:348）
 * 存在的理由就是让不同种类的公告共用一张表，PRD 593 行字典也把它列为「类型」。
 * 本卡读 {@code type='STOP_CLINIC'} 这一族行，零行时回空列表。
 *
 * <h2>纯读，不套事务</h2>
 * 与 {@code CatalogService} 同一条理由：没有写操作，套事务只是白占连接。
 */
@Service
public class HospitalContentService {

    private final HospitalProfileMapper profileMapper;
    private final GuideArticleMapper guideMapper;
    private final HealthArticleMapper articleMapper;
    private final AnnouncementMapper announcementMapper;
    private final AppointmentNoticeMapper appointmentNoticeMapper;
    private final DeliveryNoticeMapper deliveryNoticeMapper;

    public HospitalContentService(HospitalProfileMapper profileMapper,
                                  GuideArticleMapper guideMapper,
                                  HealthArticleMapper articleMapper,
                                  AnnouncementMapper announcementMapper,
                                  AppointmentNoticeMapper appointmentNoticeMapper,
                                  DeliveryNoticeMapper deliveryNoticeMapper) {
        this.profileMapper = profileMapper;
        this.guideMapper = guideMapper;
        this.articleMapper = articleMapper;
        this.announcementMapper = announcementMapper;
        this.appointmentNoticeMapper = appointmentNoticeMapper;
        this.deliveryNoticeMapper = deliveryNoticeMapper;
    }

    /**
     * 预约须知（T27 卡片 745 行 / PRD 78 行那页的数据源改造）。
     * 表里没有行时回 null，患者页显示空态而不是报错——与医院简介同一处理。
     *
     * <p><b>读法只有这一份</b>：后台的编辑页也走它（{@code AdminContentCommandService} 委托过来）。
     * 两处各写一遍，就会出现"管理员看到的"与"患者看到的"不是同一行那种查不出来的 bug。
     */
    public NoticeResponse appointmentNotice() {
        List<AppointmentNotice> rows = appointmentNoticeMapper.selectList(
                new LambdaQueryWrapper<AppointmentNotice>().orderByAsc(AppointmentNotice::getId));
        if (rows.isEmpty()) {
            return null;
        }
        AppointmentNotice row = rows.get(0);
        return toNoticeResponse(row.getId(), row.getTitle(), row.getContent(), row.getUpdatedAt());
    }

    /** 病案配送须知（T27 卡片 746 行 / PRD 240 行）。 */
    public NoticeResponse deliveryNotice() {
        List<DeliveryNotice> rows = deliveryNoticeMapper.selectList(
                new LambdaQueryWrapper<DeliveryNotice>().orderByAsc(DeliveryNotice::getId));
        if (rows.isEmpty()) {
            return null;
        }
        DeliveryNotice row = rows.get(0);
        return toNoticeResponse(row.getId(), row.getTitle(), row.getContent(), row.getUpdatedAt());
    }

    private NoticeResponse toNoticeResponse(Long id, String title, String content,
                                            java.time.LocalDateTime updatedAt) {
        NoticeResponse response = new NoticeResponse();
        response.setId(id);
        response.setTitle(title);
        response.setContent(content);
        response.setUpdatedAt(updatedAt);
        return response;
    }

    /**
     * 医院简介（J53）。表是单行语义（卡片 744 行写的是"编辑"不是 CRUD），
     * 所以按 id 升序取第一行；没配置时回 null，Result.data 为 null，前端显示空态而不是报错。
     */
    public HospitalProfileResponse profile() {
        List<HospitalProfile> rows = profileMapper.selectList(
                new LambdaQueryWrapper<HospitalProfile>().orderByAsc(HospitalProfile::getId));
        if (rows.isEmpty()) {
            return null;
        }
        return toProfileResponse(rows.get(0));
    }

    /** 就医指南列表（卡片 680 行 / PRD 262 行）。只给标题，正文留给详情。 */
    public List<GuideArticleResponse> guides() {
        return guideMapper.selectList(new LambdaQueryWrapper<GuideArticle>()
                        .orderByAsc(GuideArticle::getId))
                .stream().map(row -> {
                    GuideArticleResponse response = new GuideArticleResponse();
                    response.setId(row.getId());
                    response.setTitle(row.getTitle());
                    response.setUpdatedAt(row.getUpdatedAt());
                    return response;
                }).toList();
    }

    /** 指南详情。id 不存在与已被下架同回 5001（内容类资源没有"存在但不属于你"这种可区分状态）。 */
    public GuideArticleResponse guide(Long id) {
        GuideArticle row = guideMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        GuideArticleResponse response = new GuideArticleResponse();
        response.setId(row.getId());
        response.setTitle(row.getTitle());
        response.setContent(row.getContent());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }

    /** 健康百科文章列表（J54）。按发布时间倒序；publish_time 为空的行排在最后（MySQL 的 NULL 排序）。 */
    public List<HealthArticleResponse> articles() {
        return articleMapper.selectList(new LambdaQueryWrapper<HealthArticle>()
                        .orderByDesc(HealthArticle::getPublishTime)
                        .orderByDesc(HealthArticle::getId))
                .stream().map(row -> {
                    HealthArticleResponse response = new HealthArticleResponse();
                    response.setId(row.getId());
                    response.setTitle(row.getTitle());
                    response.setPublishTime(row.getPublishTime());
                    return response;
                }).toList();
    }

    /** 文章详情（PRD 266 行「文章详情 — 查看文章详细内容」）。 */
    public HealthArticleResponse article(Long id) {
        HealthArticle row = articleMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        HealthArticleResponse response = new HealthArticleResponse();
        response.setId(row.getId());
        response.setTitle(row.getTitle());
        response.setContent(row.getContent());
        response.setPublishTime(row.getPublishTime());
        return response;
    }

    /** 停诊通知列表（卡片 682 行）。只读 announcement 里 STOP_CLINIC 那一族，别的种类不外泄。 */
    public List<StopNoticeResponse> stopNotices() {
        return announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getType, AnnouncementType.STOP_CLINIC.name())
                        .orderByDesc(Announcement::getPublishTime)
                        .orderByDesc(Announcement::getId))
                .stream().map(row -> {
                    StopNoticeResponse response = new StopNoticeResponse();
                    response.setId(row.getId());
                    response.setTitle(row.getTitle());
                    response.setContent(row.getContent());
                    response.setPublishTime(row.getPublishTime());
                    return response;
                }).toList();
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
}
