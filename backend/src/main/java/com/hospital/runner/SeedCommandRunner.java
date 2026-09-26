package com.hospital.runner;

import com.hospital.service.SeedCheckService;
import com.hospital.service.SeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 命令行入口：{@code --seed} 应用种子，{@code --seed-check} 跑自检。
 * 两者都是"执行完就退出"的一次性动作，所以带上任一参数都会关闭容器并按结果退出，
 * 不影响正常启动。Flyway 迁移在容器刷新阶段已完成，这里一定跑在迁移之后。
 */
@Component
public class SeedCommandRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedCommandRunner.class);

    private static final String OPTION_SEED = "seed";
    private static final String OPTION_SEED_CHECK = "seed-check";

    private final SeedService seedService;
    private final SeedCheckService seedCheckService;
    private final ConfigurableApplicationContext context;

    public SeedCommandRunner(SeedService seedService,
                             SeedCheckService seedCheckService,
                             ConfigurableApplicationContext context) {
        this.seedService = seedService;
        this.seedCheckService = seedCheckService;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean applySeed = args.containsOption(OPTION_SEED);
        boolean runCheck = args.containsOption(OPTION_SEED_CHECK);
        if (!applySeed && !runCheck) {
            return;
        }

        int exitCode = 0;
        if (applySeed) {
            seedService.apply();
        }
        if (runCheck) {
            exitCode = report(seedCheckService.check());
        }
        context.close();
        System.exit(exitCode);
    }

    private int report(List<String> violations) {
        if (violations.isEmpty()) {
            log.info("种子自检通过：号源=预约数、就诊卡号唯一、住院号唯一 三项均无违规");
            return 0;
        }
        log.error("种子自检失败，共 {} 条违规：", violations.size());
        violations.forEach(v -> log.error("  - {}", v));
        return 1;
    }
}
