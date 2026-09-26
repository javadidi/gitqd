package com.hospital;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.dto.TaskContext;
import com.hospital.dto.TaskDispatchRequest;
import com.hospital.entity.AuditLog;
import com.hospital.entity.Task;
import com.hospital.entity.TaskHandover;
import com.hospital.enums.TaskTypeMeta;
import com.hospital.exception.BizException;
import com.hospital.mapper.AuditLogMapper;
import com.hospital.mapper.TaskHandoverMapper;
import com.hospital.mapper.TaskMapper;
import com.hospital.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * J9-J12：任务内核（派单幂等 / REVIEW 拒绝完成 / 交接留痕 / 批量转移）
 */
@SpringBootTest
class TaskKernelTest {

    private static final TaskContext CTX = new TaskContext(2L, "ADMIN");
    private static final String RELATED_TYPE = "TEST_T05";
    private static final String TARGET_TYPE_TASK = "task";

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskHandoverMapper taskHandoverMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @AfterEach
    void cleanUpTestData() {
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().eq(Task::getRelatedType, RELATED_TYPE));
        for (Task task : tasks) {
            taskHandoverMapper.delete(new LambdaQueryWrapper<TaskHandover>()
                    .eq(TaskHandover::getTaskId, task.getId()));
            auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                    .eq(AuditLog::getTargetType, TARGET_TYPE_TASK)
                    .eq(AuditLog::getTargetId, task.getId()));
        }
        auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getTargetType, RELATED_TYPE));
        taskMapper.delete(new LambdaQueryWrapper<Task>().eq(Task::getRelatedType, RELATED_TYPE));
    }

    private Long dispatch(TaskTypeMeta meta, Long relatedId, Long assigneeId) {
        return taskService.dispatchTask(CTX,
                new TaskDispatchRequest(meta.name(), RELATED_TYPE, relatedId, assigneeId));
    }

    private long countOpenByKey(TaskTypeMeta meta, Long relatedId, Long assigneeId) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<Task>()
                .eq(Task::getType, meta.name())
                .eq(Task::getRelatedType, RELATED_TYPE)
                .eq(Task::getRelatedId, relatedId)
                .eq(Task::getAssigneeId, assigneeId)
                .eq(Task::getStatus, "OPEN"));
        return count == null ? 0L : count;
    }

    private long countAuditByAction(String action) {
        Long count = auditLogMapper.selectCount(
                new LambdaQueryWrapper<AuditLog>().eq(AuditLog::getAction, action));
        return count == null ? 0L : count;
    }

    private AuditLog latestAudit(String action) {
        return auditLogMapper.selectOne(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getAction, action)
                .orderByDesc(AuditLog::getId)
                .last("LIMIT 1"));
    }

    @Test
    void j9_dispatchIsIdempotentWhileTaskIsOpen() {
        Long taskId = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, 900101L, 11L);
        Long again = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, 900101L, 11L);

        assertEquals(taskId, again, "同幂等键重复派单必须返回同一任务 id");
        assertEquals(1, countOpenByKey(TaskTypeMeta.APPOINTMENT_CONFIRM, 900101L, 11L),
                "同键未完成不得新增第二条");

        Task task = taskMapper.selectById(taskId);
        assertEquals("OPEN", task.getStatus());
        assertEquals("ACTION", task.getScope(), "scope 应来自 TaskTypeMeta，不由调用方传");
        assertNotNull(task.getDueAt(), "due_at 应按 TaskTypeMeta 默认时限自动算出");
        LocalDateTime now = LocalDateTime.now();
        assertTrue(task.getDueAt().isAfter(now.plusHours(1)) && task.getDueAt().isBefore(now.plusHours(3)),
                "预约确认默认时限应为 2 小时，实际为 " + task.getDueAt());

        Long otherAssignee = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, 900101L, 12L);
        assertNotEquals(taskId, otherAssignee, "换负责人即换任务，幂等键含 assigneeId");

        taskService.completeTask(CTX, taskId);
        Long afterCompleted = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, 900101L, 11L);
        assertNotEquals(taskId, afterCompleted, "幂等只约束未完成，已结案后应能重新派单");
        assertEquals(1, countOpenByKey(TaskTypeMeta.APPOINTMENT_CONFIRM, 900101L, 11L));
    }

    @Test
    void j9_dispatchRejectsIncompleteIdempotencyKey() {
        BizException blankRelated = assertThrows(BizException.class, () -> taskService.dispatchTask(CTX,
                new TaskDispatchRequest(TaskTypeMeta.APPOINTMENT_CONFIRM.name(), null, 900102L, 11L)));
        assertEquals(400, blankRelated.getCode(), "relatedType 为空会让幂等查询永久失配，必须拒绝");

        BizException blankAssignee = assertThrows(BizException.class, () -> taskService.dispatchTask(CTX,
                new TaskDispatchRequest(TaskTypeMeta.APPOINTMENT_CONFIRM.name(), RELATED_TYPE, 900102L, null)));
        assertEquals(400, blankAssignee.getCode(), "assigneeId 为空同样破坏幂等键");

        BizException unknownType = assertThrows(BizException.class, () -> taskService.dispatchTask(CTX,
                new TaskDispatchRequest("NOT_A_REAL_TYPE", RELATED_TYPE, 900102L, 11L)));
        assertEquals(6004, unknownType.getCode(), "未知任务类型必须显式报错，不能静默派单");

        BizException noCtx = assertThrows(BizException.class, () -> taskService.dispatchTask(null,
                new TaskDispatchRequest(TaskTypeMeta.APPOINTMENT_CONFIRM.name(), RELATED_TYPE, 900102L, 11L)));
        assertEquals(401, noCtx.getCode(), "无操作者上下文不得派单");

        assertEquals(0L, countOpenByKey(TaskTypeMeta.APPOINTMENT_CONFIRM, 900102L, 11L),
                "上述四次失败调用都不应在任务表留下任何记录");
    }

    @Test
    void j10_reviewTaskCannotBeCompletedByKernel() {
        Long taskId = dispatch(TaskTypeMeta.REFUND_REVIEW, 900103L, 21L);
        assertEquals("REVIEW", taskMapper.selectById(taskId).getScope());

        BizException ex = assertThrows(BizException.class, () -> taskService.completeTask(CTX, taskId));
        assertEquals(6003, ex.getCode(), "scope=REVIEW 必须抛 REVIEW_MUST_OPEN_DOC");

        Task stillOpen = taskMapper.selectById(taskId);
        assertEquals("OPEN", stillOpen.getStatus(), "拒绝必须彻底，不能留下半完成状态");
        assertNull(stillOpen.getCompletedAt());

        // 交接不受 REVIEW 限制：换人不是"完成"
        taskService.handoverTask(CTX, taskId, 22L, "退款争议转主管");
        assertEquals(22L, taskMapper.selectById(taskId).getAssigneeId().longValue());
    }

    @Test
    void redLine_kernelExposesNoBatchCompletePath() {
        for (Method method : TaskService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            boolean batchComplete = name.contains("complete") && (name.contains("all") || name.contains("batch"));
            assertFalse(batchComplete,
                    "红线：REVIEW 不给任何批量完成路径 —— TaskService 不应存在方法 " + method.getName());
        }
    }

    @Test
    void j11_handoverWritesHandoverRecordAndAudit() {
        Long taskId = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, 900104L, 31L);
        long auditBefore = countAuditByAction("TASK_HANDOVER");

        taskService.handoverTask(CTX, taskId, 32L, "原负责人休假");

        Task task = taskMapper.selectById(taskId);
        assertEquals(32L, task.getAssigneeId().longValue(), "负责人应已变更");
        assertEquals("OPEN", task.getStatus(), "交接不是完成");

        TaskHandover handover = taskHandoverMapper.selectOne(new LambdaQueryWrapper<TaskHandover>()
                .eq(TaskHandover::getTaskId, taskId)
                .orderByDesc(TaskHandover::getId)
                .last("LIMIT 1"));
        assertNotNull(handover, "必须留下交接记录");
        assertEquals(31L, handover.getFromId().longValue());
        assertEquals(32L, handover.getToId().longValue());
        assertEquals("原负责人休假", handover.getReason());

        AuditLog log = latestAudit("TASK_HANDOVER");
        assertNotNull(log, "交接必须留痕");
        assertEquals(auditBefore + 1, countAuditByAction("TASK_HANDOVER"));
        assertEquals(2L, log.getOperatorId().longValue(), "审计记录的是 ctx 里的操作者");
        assertEquals("ADMIN", log.getOperatorType());
        assertEquals(TARGET_TYPE_TASK, log.getTargetType());
        assertEquals(taskId, log.getTargetId());
        assertEquals("原负责人休假", log.getReason());
        assertNotNull(log.getDetail());
        assertTrue(log.getDetail().contains("fromAssigneeId"), "detail 应含转出方，实际：" + log.getDetail());

        BizException selfHandover = assertThrows(BizException.class,
                () -> taskService.handoverTask(CTX, taskId, 32L, "交接给自己"));
        assertEquals(400, selfHandover.getCode(), "交接目标等于当前负责人应被拒绝");
    }

    @Test
    void j12_transferAllMovesOnlyOpenTasksOfThatAssigneeUnderThatDoc() {
        long relatedId = 900105L;
        Long first = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, relatedId, 41L);
        Long second = dispatch(TaskTypeMeta.NUCLEIC_CONFIRM, relatedId, 41L);
        Long third = dispatch(TaskTypeMeta.PHYSICAL_CONFIRM, relatedId, 41L);
        Long otherAssignee = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, relatedId, 42L);
        Long otherDoc = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, 999999L, 41L);
        taskService.completeTask(CTX, third);

        int moved = taskService.transferAllTasks(CTX, RELATED_TYPE, relatedId, 41L, 43L, "医生调班");

        assertEquals(2, moved, "只转移该单据下该负责人名下的未完成任务");
        assertEquals(43L, taskMapper.selectById(first).getAssigneeId().longValue());
        assertEquals(43L, taskMapper.selectById(second).getAssigneeId().longValue());
        assertEquals(41L, taskMapper.selectById(third).getAssigneeId().longValue(), "已完成任务不参与转移");
        assertEquals(42L, taskMapper.selectById(otherAssignee).getAssigneeId().longValue(), "他人任务不受影响");
        assertEquals(41L, taskMapper.selectById(otherDoc).getAssigneeId().longValue(), "他单任务不受影响");

        for (Long taskId : List.of(first, second)) {
            TaskHandover handover = taskHandoverMapper.selectOne(new LambdaQueryWrapper<TaskHandover>()
                    .eq(TaskHandover::getTaskId, taskId));
            assertNotNull(handover, "每条被转任务都要有交接记录：" + taskId);
            assertEquals(41L, handover.getFromId().longValue());
            assertEquals(43L, handover.getToId().longValue());
        }
        assertEquals(0L, taskHandoverMapper.selectCount(new LambdaQueryWrapper<TaskHandover>()
                .eq(TaskHandover::getTaskId, third)).longValue(), "未转移的任务不应有交接记录");

        AuditLog log = latestAudit("TASK_TRANSFER_ALL");
        assertNotNull(log);
        assertEquals(RELATED_TYPE, log.getTargetType(), "批量转移的审计对象是业务单据，不是某条任务");
        assertEquals(relatedId, log.getTargetId().longValue());
        assertEquals("医生调班", log.getReason());
        assertTrue(log.getDetail().contains("taskCount"), "detail 应记录转移条数");

        BizException selfTransfer = assertThrows(BizException.class,
                () -> taskService.transferAllTasks(CTX, RELATED_TYPE, relatedId, 43L, 43L, "原地转移"));
        assertEquals(400, selfTransfer.getCode());
    }

    @Test
    void overdueIsJudgedAtQueryTimeWithoutStoredState() {
        LocalDateTime now = LocalDateTime.now();

        Task overdue = new Task();
        overdue.setStatus("OPEN");
        overdue.setDueAt(now.minusMinutes(1));
        assertTrue(taskService.isOverdue(overdue, now));

        Task dueLater = new Task();
        dueLater.setStatus("OPEN");
        dueLater.setDueAt(now.plusMinutes(1));
        assertFalse(taskService.isOverdue(dueLater, now), "未到截止点不算超期");

        Task completedLate = new Task();
        completedLate.setStatus("COMPLETED");
        completedLate.setDueAt(now.minusDays(1));
        assertFalse(taskService.isOverdue(completedLate, now), "已结案任务不再参与超期判定");

        Task noDeadline = new Task();
        noDeadline.setStatus("OPEN");
        assertFalse(taskService.isOverdue(noDeadline, now), "无截止时间不能被判超期");

        Long held = dispatch(TaskTypeMeta.APPOINTMENT_CONFIRM, 900106L, 51L);
        Long kept = dispatch(TaskTypeMeta.NUCLEIC_CONFIRM, 900106L, 51L);
        List<Long> openIds = openIdsOf(51L);
        assertTrue(openIds.contains(held) && openIds.contains(kept), "派单后应出现在待办列表");

        taskService.completeTask(CTX, held);
        openIds = openIdsOf(51L);
        assertFalse(openIds.contains(held), "已完成任务不得再出现在待办列表");
        assertTrue(openIds.contains(kept), "同人其他待办不应被牵连");
    }

    /** 只断言"我们的任务在/不在"，不数总数，避免历史脏数据干扰 */
    private List<Long> openIdsOf(Long assigneeId) {
        return taskService.listOpen(assigneeId).stream().map(Task::getId).toList();
    }
}
