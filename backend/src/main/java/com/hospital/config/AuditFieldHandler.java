package com.hospital.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充 created_at / updated_at。
 * 不填的话 MyBatis-Plus 的 updateById 会把实体里读到的旧 updated_at 原样写回，
 * MySQL 判定"值未变化"从而不触发 ON UPDATE CURRENT_TIMESTAMP，该列永久冻结在插入时刻。
 */
@Component
public class AuditFieldHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject.hasSetter("updatedAt")) {
            // 覆盖式写入：strictUpdateFill 遇到非 null 旧值会跳过，正是本类要修的 bug
            metaObject.setValue("updatedAt", LocalDateTime.now());
        }
    }
}
