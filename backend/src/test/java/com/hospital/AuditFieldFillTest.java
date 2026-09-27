package com.hospital;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.entity.Task;
import com.hospital.entity.Title;
import com.hospital.mapper.TaskMapper;
import com.hospital.mapper.TitleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T06-0：created_at / updated_at 自动填充。
 * 复现的 bug 是 updateById 把实体里读到的旧 updated_at 原样写回，
 * 使 MySQL 的 ON UPDATE CURRENT_TIMESTAMP 因"值未变化"而不触发。
 */
@SpringBootTest
class AuditFieldFillTest {

    private static final String RELATED_TYPE = "TEST_T06";
    private static final String TITLE_NAME = "T06 填充测试职称";
    private static final LocalDateTime FROZEN = LocalDateTime.of(2020, 1, 1, 0, 0, 0);

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TitleMapper titleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().eq(Task::getRelatedType, RELATED_TYPE));
        for (Task task : tasks) {
            taskMapper.deleteById(task.getId());
        }
        // title 必须走裸 SQL 物理删：title 继承 BaseEntity，@TableLogic 让
        // titleMapper.delete(...) 只是把 deleted 置 1，行还在。T06 收口时没发现，
        // 到 T10 数库才暴露——每跑一次全套测试就往开发库多留一行 sort_order=100 的死行，
        // 累计 11 行（见 docs/WORK_LOG.md 的 T10 节）。
        // task 表没有 deleted 列，所以上面那个 deleteById 本来就是物理删，不用改。
        jdbcTemplate.update("DELETE FROM title WHERE name = ?", TITLE_NAME);
    }

    private Task newTask(Long relatedId) {
        Task task = new Task();
        task.setType("APPOINTMENT_CONFIRM");
        task.setRelatedType(RELATED_TYPE);
        task.setRelatedId(relatedId);
        task.setAssigneeId(71L);
        task.setStatus("OPEN");
        task.setScope("ACTION");
        task.setDueAt(LocalDateTime.now().plusHours(2));
        return task;
    }

    @Test
    void insertFillsBothTimestampsOnTask() {
        LocalDateTime before = LocalDateTime.now();
        Task task = newTask(900601L);
        taskMapper.insert(task);

        Task loaded = taskMapper.selectById(task.getId());
        assertNotNull(loaded.getCreatedAt(), "created_at 应由 MetaObjectHandler 填充");
        assertNotNull(loaded.getUpdatedAt(), "updated_at 应由 MetaObjectHandler 填充");
        assertTrue(!loaded.getCreatedAt().isBefore(before.minusSeconds(1)),
                "created_at 应接近插入时刻，实际=" + loaded.getCreatedAt());
    }

    @Test
    void updateByIdOverwritesStaleUpdatedAtOnStandaloneEntity() {
        Task task = newTask(900602L);
        taskMapper.insert(task);
        LocalDateTime insertedAt = taskMapper.selectById(task.getId()).getUpdatedAt();

        // 模拟真实调用方：实体带着上次读到的时间戳回来更新（旧值可能是任意陈旧时刻）
        Task toUpdate = taskMapper.selectById(task.getId());
        toUpdate.setUpdatedAt(FROZEN);
        toUpdate.setAssigneeId(72L);
        taskMapper.updateById(toUpdate);

        Task after = taskMapper.selectById(task.getId());
        assertTrue(after.getUpdatedAt().isAfter(insertedAt),
                "updated_at 必须推进：插入时=" + insertedAt + "，更新后=" + after.getUpdatedAt());
        assertTrue(after.getUpdatedAt().isAfter(FROZEN),
                "陈旧的 updated_at 必须被覆盖，不能被原样写回");
        assertTrue(!after.getCreatedAt().isAfter(after.getUpdatedAt()),
                "created_at 不得被 updateFill 改动");
    }

    @Test
    void updateByIdOverwritesStaleUpdatedAtOnBaseEntitySubclass() {
        Title title = new Title();
        title.setName(TITLE_NAME);
        title.setSortOrder(99);
        titleMapper.insert(title);
        LocalDateTime insertedAt = titleMapper.selectById(title.getId()).getUpdatedAt();

        Title toUpdate = titleMapper.selectById(title.getId());
        toUpdate.setSortOrder(100);
        toUpdate.setUpdatedAt(FROZEN);
        titleMapper.updateById(toUpdate);

        Title after = titleMapper.selectById(title.getId());
        assertNotNull(after.getCreatedAt(), "BaseEntity 子类的 created_at 也应自动填充");
        assertTrue(after.getUpdatedAt().isAfter(insertedAt),
                "BaseEntity 子类的 updated_at 也必须推进：插入时=" + insertedAt
                        + "，更新后=" + after.getUpdatedAt());
        assertTrue(after.getUpdatedAt().isAfter(FROZEN), "旧值应被覆盖");
    }
}
