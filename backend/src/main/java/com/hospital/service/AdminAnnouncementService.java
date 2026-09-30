package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AnnouncementResponse;
import com.hospital.dto.AnnouncementSaveRequest;
import com.hospital.entity.Announcement;
import com.hospital.enums.AnnouncementType;
import com.hospital.exception.BizException;
import com.hospital.mapper.AnnouncementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息公告的读写（T28 卡片 765 行「消息公告管理：CRUD」/ PRD 4.6.4 的 461–462 行）。
 *
 * <h2>本卡是 announcement 表的第一个写侧</h2>
 * 这张表 V1:344 就建好了，但 seed 一行没给（第 12 段起才出现内容表，公告不在里面），
 * 于是 T24 做的停诊通知页 {@code GET /user/stop-notices} 从上线起读到的永远是空列表——
 * 它当时的类注释写得很清楚："本卡读 type='STOP_CLINIC' 这一族行，零行时回空列表"。
 * 本卡把这把写端点补上以后，那条链路第一次通：后台发一条 STOP_CLINIC 公告，
 * 小程序的停诊通知页立刻就看见（集成测试里有一条跨卡断言专门钉这件事）。
 *
 * <p><b>但本卡不 seed 任何公告内容</b>：公告写的是"某天某科停诊"这种声明，
 * 编一条出来就是替医院发布它没发生过的声明（T24 当初因此不从排班软删派生停诊通知，
 * T21 也拒绝在核酸里写医学结论）。所以库里那张表仍然零行，只是从此由人来发。
 *
 * <h2>publish_time 只在创建时落一次</h2>
 * 与 T27 的 {@code AdminContentCommandService.updateArticle} 同一条取舍：
 * 编辑正文不应该把公告"顶回"最新一条——停诊通知改了个错别字，
 * 时间却跳到今天，管理员和患者都会被这个时间骗。所以 SET 白名单里没有 publishTime。
 */
@Service
public class AdminAnnouncementService {

    private final AnnouncementMapper announcementMapper;

    public AdminAnnouncementService(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    /** 公告列表（PRD 461 行）。全类型都列，不按 type 过滤——管理员要看的正是"发出去了什么"。 */
    public List<AnnouncementResponse> list() {
        List<Announcement> rows = announcementMapper.selectList(
                new LambdaQueryWrapper<Announcement>().orderByDesc(Announcement::getId));
        List<AnnouncementResponse> result = new ArrayList<>();
        for (Announcement row : rows) {
            result.add(toResponse(row));
        }
        return result;
    }

    @AuditLog(action = "CREATE_ANNOUNCEMENT", targetType = "announcement")
    @Transactional
    public AnnouncementResponse create(AnnouncementSaveRequest request) {
        Announcement entity = new Announcement();
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent());
        entity.setType(requireType(request.getType()).name());
        entity.setPublishTime(LocalDateTime.now());
        announcementMapper.insert(entity);
        return toResponse(announcementMapper.selectById(entity.getId()));
    }

    @AuditLog(action = "UPDATE_ANNOUNCEMENT", targetType = "announcement")
    @Transactional
    public AnnouncementResponse update(@AuditTarget Long id, AnnouncementSaveRequest request) {
        requireAnnouncement(id);
        // 显式 SET：三列全给，publishTime 不在名单里（见类注释）。
        announcementMapper.update(null, new LambdaUpdateWrapper<Announcement>()
                .eq(Announcement::getId, id)
                .set(Announcement::getTitle, request.getTitle().trim())
                .set(Announcement::getContent, request.getContent())
                .set(Announcement::getType, requireType(request.getType()).name()));
        return toResponse(announcementMapper.selectById(id));
    }

    /**
     * 删除公告。不加守卫，因为没有任何表引用它（全库搜 {@code announcement_id} 零命中），
     * 删一条公告的语义就是"撤回这条通知"——这恰好是本卡开删除的唯一理由：
     * 发错了的停诊通知必须能收回来，否则小程序会一直挂着一条假停诊。
     */
    @AuditLog(action = "DELETE_ANNOUNCEMENT", targetType = "announcement")
    @Transactional
    public void delete(@AuditTarget Long id) {
        requireAnnouncement(id);
        announcementMapper.deleteById(id);
    }

    private Announcement requireAnnouncement(Long id) {
        Announcement row = id == null ? null : announcementMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND.getCode(), "公告不存在");
        }
        return row;
    }

    /** 未知类型直接 400，不兜底成 NOTICE——那等于替管理员选了一个他没选的类型。 */
    private AnnouncementType requireType(String raw) {
        AnnouncementType type = AnnouncementType.of(raw);
        if (type == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "公告类型只能是 NOTICE/ACTIVITY/STOP_CLINIC，收到：" + raw);
        }
        return type;
    }

    private AnnouncementResponse toResponse(Announcement row) {
        AnnouncementResponse response = new AnnouncementResponse();
        response.setId(row.getId());
        response.setTitle(row.getTitle());
        response.setContent(row.getContent());
        response.setType(row.getType());
        AnnouncementType type = AnnouncementType.of(row.getType());
        response.setTypeLabel(type == null ? row.getType() : type.getLabel());
        response.setPublishTime(row.getPublishTime());
        return response;
    }
}
