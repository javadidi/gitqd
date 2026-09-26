package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.TaskContext;
import com.hospital.dto.TaskDispatchRequest;
import com.hospital.entity.Task;
import com.hospital.entity.TaskHandover;
import com.hospital.enums.TaskScope;
import com.hospital.enums.TaskStatus;
import com.hospital.enums.TaskTypeMeta;
import com.hospital.exception.BizException;
import com.hospital.mapper.TaskHandoverMapper;
import com.hospital.mapper.TaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {

    private static final String ACTION_HANDOVER = "TASK_HANDOVER";
    private static final String ACTION_TRANSFER_ALL = "TASK_TRANSFER_ALL";
    private static final String TARGET_TYPE_TASK = "task";

    private final TaskMapper taskMapper;
    private final TaskHandoverMapper taskHandoverMapper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public TaskService(TaskMapper taskMapper, TaskHandoverMapper taskHandoverMapper,
                       AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.taskHandoverMapper = taskHandoverMapper;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 派单。幂等键为 (type, relatedType, relatedId, assigneeId)，存在未完成的同键任务时直接复用。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long dispatchTask(TaskContext ctx, TaskDispatchRequest req) {
        requireContext(ctx);
        if (req == null || isBlank(req.type()) || isBlank(req.relatedType())
                || req.relatedId() == null || req.assigneeId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "派单幂等键四要素不可为空");
        }
        TaskTypeMeta meta = TaskTypeMeta.fromCode(req.type());

        Task open = taskMapper.selectOne(openKeyWrapper(req.type(), req.relatedType(),
                req.relatedId(), req.assigneeId()));
        if (open != null) {
            return open.getId();
        }

        LocalDateTime now = LocalDateTime.now();
        Task task = new Task();
        task.setType(meta.name());
        task.setRelatedType(req.relatedType());
        task.setRelatedId(req.relatedId());
        task.setAssigneeId(req.assigneeId());
        task.setStatus(TaskStatus.OPEN.name());
        task.setScope(meta.scope().name());
        task.setDueAt(meta.defaultDueAt(now));
        taskMapper.insert(task);
        return task.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeTask(TaskContext ctx, Long taskId) {
        requireContext(ctx);
        Task task = requireOpenTask(taskId);
        if (TaskScope.REVIEW.name().equals(task.getScope())) {
            throw new BizException(ErrorCode.REVIEW_MUST_OPEN_DOC);
        }
        task.setStatus(TaskStatus.COMPLETED.name());
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handoverTask(TaskContext ctx, Long taskId, Long toAssigneeId, String reason) {
        requireContext(ctx);
        if (toAssigneeId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "交接目标负责人不能为空");
        }
        Task task = requireOpenTask(taskId);
        Long fromAssigneeId = task.getAssigneeId();
        if (fromAssigneeId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "任务无负责人，无法交接");
        }
        if (fromAssigneeId.equals(toAssigneeId)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "交接目标不能是当前负责人");
        }

        writeHandover(task.getId(), fromAssigneeId, toAssigneeId, reason);
        task.setAssigneeId(toAssigneeId);
        taskMapper.updateById(task);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", task.getId());
        detail.put("fromAssigneeId", fromAssigneeId);
        detail.put("toAssigneeId", toAssigneeId);
        auditLogService.write(ctx.operatorId(), ctx.operatorType(), ACTION_HANDOVER,
                TARGET_TYPE_TASK, task.getId(), reason, toJson(detail));
    }

    /**
     * 换人时把某业务单据下该人名下的全部未完成待办整体转移。
     * 审计目标是业务单据（relatedType/relatedId），不是单条任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public int transferAllTasks(TaskContext ctx, String relatedType, Long relatedId,
                                Long fromAssigneeId, Long toAssigneeId, String reason) {
        requireContext(ctx);
        if (isBlank(relatedType) || relatedId == null || fromAssigneeId == null || toAssigneeId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "转移参数不可为空");
        }
        if (fromAssigneeId.equals(toAssigneeId)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "转出与转入负责人不能相同");
        }

        List<Task> openTasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getRelatedType, relatedType)
                .eq(Task::getRelatedId, relatedId)
                .eq(Task::getAssigneeId, fromAssigneeId)
                .eq(Task::getStatus, TaskStatus.OPEN.name()));
        if (openTasks.isEmpty()) {
            return 0;
        }

        for (Task task : openTasks) {
            writeHandover(task.getId(), fromAssigneeId, toAssigneeId, reason);
            task.setAssigneeId(toAssigneeId);
            taskMapper.updateById(task);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("relatedType", relatedType);
        detail.put("relatedId", relatedId);
        detail.put("fromAssigneeId", fromAssigneeId);
        detail.put("toAssigneeId", toAssigneeId);
        detail.put("taskCount", openTasks.size());
        auditLogService.write(ctx.operatorId(), ctx.operatorType(), ACTION_TRANSFER_ALL,
                relatedType, relatedId, reason, toJson(detail));
        return openTasks.size();
    }

    public List<Task> listOpen(Long assigneeId) {
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getAssigneeId, assigneeId)
                .eq(Task::getStatus, TaskStatus.OPEN.name())
                .orderByAsc(Task::getDueAt));
    }

    /**
     * 超期只在查询时判定，不落库、不定时刷新，避免状态与实际时间不一致。
     */
    public boolean isOverdue(Task task, LocalDateTime now) {
        return task != null
                && TaskStatus.OPEN.name().equals(task.getStatus())
                && task.getDueAt() != null
                && task.getDueAt().isBefore(now);
    }

    private void writeHandover(Long taskId, Long fromAssigneeId, Long toAssigneeId, String reason) {
        TaskHandover handover = new TaskHandover();
        handover.setTaskId(taskId);
        handover.setFromId(fromAssigneeId);
        handover.setToId(toAssigneeId);
        handover.setReason(reason);
        taskHandoverMapper.insert(handover);
    }

    private LambdaQueryWrapper<Task> openKeyWrapper(String type, String relatedType,
                                                    Long relatedId, Long assigneeId) {
        return new LambdaQueryWrapper<Task>()
                .eq(Task::getType, type)
                .eq(Task::getRelatedType, relatedType)
                .eq(Task::getRelatedId, relatedId)
                .eq(Task::getAssigneeId, assigneeId)
                .eq(Task::getStatus, TaskStatus.OPEN.name())
                .last("LIMIT 1");
    }

    private Task requireOpenTask(Long taskId) {
        if (taskId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "任务 id 不能为空");
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!TaskStatus.OPEN.name().equals(task.getStatus())) {
            throw new BizException(ErrorCode.TASK_STATUS_ERROR.getCode(), "任务已结案，不可再操作");
        }
        return task;
    }

    private void requireContext(TaskContext ctx) {
        if (ctx == null || ctx.operatorId() == null || isBlank(ctx.operatorType())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
