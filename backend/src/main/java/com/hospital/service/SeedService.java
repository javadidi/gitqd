package com.hospital.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * 应用 classpath:db/seed.sql。事务内执行，所以测试里可以在回滚后不留痕迹。
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private static final String SEED_SCRIPT = "db/seed.sql";

    private final DataSource dataSource;

    public SeedService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Transactional(rollbackFor = Exception.class)
    public void apply() {
        EncodedResource script =
                new EncodedResource(new ClassPathResource(SEED_SCRIPT), StandardCharsets.UTF_8);

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            // 分隔符沿用默认值：行注释 --、语句分隔 ;、块注释 /* */，与 seed.sql 的写法一致。
            ScriptUtils.executeSqlScript(connection, script);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
        log.info("种子数据已应用：{}", SEED_SCRIPT);
    }
}
