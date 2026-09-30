package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminFeedbackResponse;
import com.hospital.dto.FeedbackReplyRequest;
import com.hospital.entity.Feedback;
import com.hospital.entity.User;
import com.hospital.exception.BizException;
import com.hospital.mapper.FeedbackMapper;
import com.hospital.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端用户反馈（T27 卡片 747 行「用户反馈管理：列表/处理」/ PRD 4.5.12 的 440–442 行，
 * 判据 J60「反馈处理 → 状态更新」）。
 *
 * <h2>回复同时推进状态，而且这是唯一一条边</h2>
 * V1:362 的取值域是 {@code PENDING/REPLIED/CLOSED} 三个。本卡只开
 * {@code PENDING → REPLIED} 一条：PRD 442 行的原话是「进行<b>处理回复</b>」，
 * J60 要的是"状态更新"，两句话合起来就是"回复这个动作把单子推到已回复"。
 * {@code CLOSED} <b>没有任何写入路径</b>——规格里没有一句"关闭反馈"，
 * 所以后台也不给那个按钮（与 T16 的 {@code queue_status}、T22 的三张体检表同一类"没主的空"）。
 *
 * <p>第二条边刻意不开：REPLIED 之后再回复 → {@code 5002 DATA_ALREADY_EXISTS}
 * （那句"数据已存在"正好说的是"回复已经有一条了"）。
 * 规格没有"追加回复"也没有"改回复"，而 {@code feedback.reply} 是单列 TEXT，
 * 结构上就只能存一份——允许覆盖等于让管理员悄悄改掉已经发给患者看过的话。
 *
 * <h2>本卡最大的一个事实：这张表没有生产者</h2>
 * {@code feedback} 表 V1 就建好了，但<b>提交反馈的那一页在任务卡里不存在</b>：
 * 全文件只有 747 行（本卡的后台列表/处理）与 753 行（J60）两处提到"反馈"，
 * 而 PRD 侧一共写了四次——318–320 行「问题反馈 — 提交反馈 / 查看反馈处理状态」、
 * 527 行页面清单里的「问题反馈」、622 行接口概览里的「反馈提交」。
 * {@code miniprogram/pages/mine/mine.js} 那一条「问题反馈」的 {@code url} 至今是空串。
 * 也就是说：<b>后台这一页首版永远只能是空列表</b>，J60 只能拿探针行取证。
 * 这一条不是本卡能补的（补它要在小程序侧新建一页并改 T08 的入口清单），
 * 已原样记进 WORK_LOG 的跨卡 TODO。
 */
@Service
public class AdminFeedbackService {

    static final String PENDING = "PENDING";
    static final String REPLIED = "REPLIED";

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AdminFeedbackService(FeedbackMapper feedbackMapper, UserMapper userMapper,
                                ObjectMapper objectMapper) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 反馈列表（PRD 441 行）。<b>不接筛选参数</b>：与 T26 五个费用列表同一条判断——
     * PRD 这一节只有「展示用户提交的反馈」一句，没有"按状态筛"。
     */
    public List<AdminFeedbackResponse> list() {
        List<Feedback> rows = feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>().orderByDesc(Feedback::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        for (Feedback row : rows) {
            if (row.getUserId() != null) {
                userIds.add(row.getUserId());
            }
        }
        Map<Long, String> nicknames = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(userIds)) {
                nicknames.put(user.getId(), user.getNickname());
            }
        }
        List<AdminFeedbackResponse> result = new ArrayList<>();
        for (Feedback row : rows) {
            result.add(toResponse(row, nicknames.get(row.getUserId())));
        }
        return result;
    }

    public AdminFeedbackResponse detail(Long id) {
        Feedback row = require(id);
        User user = row.getUserId() == null ? null : userMapper.selectById(row.getUserId());
        return toResponse(row, user == null ? null : user.getNickname());
    }

    /** 处理回复（J58 之后的 J60）：写 reply、把状态从 PENDING 推到 REPLIED，同一事务一条审计。 */
    @AuditLog(action = "REPLY_FEEDBACK", targetType = "feedback")
    @Transactional
    public AdminFeedbackResponse reply(@AuditTarget Long id, FeedbackReplyRequest request) {
        Feedback row = require(id);
        if (!PENDING.equals(row.getStatus())) {
            throw new BizException(ErrorCode.DATA_ALREADY_EXISTS);
        }
        Feedback update = new Feedback();
        update.setId(id);
        update.setReply(request.getReply().trim());
        update.setStatus(REPLIED);
        feedbackMapper.updateById(update);
        return detail(id);
    }

    private Feedback require(Long id) {
        Feedback row = id == null ? null : feedbackMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        return row;
    }

    /**
     * images 是 JSON 列（V1:361），本卡<b>只读不写</b>（没有上传通道）。
     * 解析失败不报错——一条脏历史行不该让整个反馈列表 500（与 T15 的 parseItems 同一条容错）。
     */
    private List<String> readImages(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<String> urls = new ArrayList<>();
            for (Object element : raw) {
                urls.add(String.valueOf(element));
            }
            return urls;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private AdminFeedbackResponse toResponse(Feedback row, String nickname) {
        AdminFeedbackResponse response = new AdminFeedbackResponse();
        response.setId(row.getId());
        response.setUserId(row.getUserId());
        response.setNickname(nickname);
        response.setContent(row.getContent());
        response.setImages(readImages(row.getImages()));
        response.setStatus(row.getStatus());
        response.setReply(row.getReply());
        response.setCreatedAt(row.getCreatedAt());
        response.setUpdatedAt(row.getUpdatedAt());
        return response;
    }
}
