package com.hospital;

import com.hospital.service.SeedCheckService;
import com.hospital.service.SeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * J13 —— 种子自检。
 *
 * <p>整个类跑在事务里并回滚：seed.sql 是纯 DML，在测试事务内执行完，自检能读到未提交的行，
 * 测试结束后库回到原样，所以 `mvn test` 不会清掉开发者本地数据。
 */
@SpringBootTest
@Transactional
class SeedCheckTest {

    @Autowired
    private SeedService seedService;

    @Autowired
    private SeedCheckService seedCheckService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** 卡片第 4 项逐条对上：这是「种子够不够」的验收，不属于自检项。 */
    @Test
    void seedCoversEveryItemTheCardEnumerates() {
        seedService.apply();

        assertEquals(3, count("SELECT COUNT(*) FROM `department` WHERE deleted = 0"), "3 科室");
        assertEquals(3, count("SELECT COUNT(*) FROM `title` WHERE deleted = 0"), "3 职称");
        assertEquals(5, count("SELECT COUNT(*) FROM `doctor` WHERE deleted = 0"), "5 医生");
        assertEquals(2, count("""
                SELECT COUNT(*) FROM `doctor` d
                JOIN `title` t ON t.id = d.title_id
                WHERE d.deleted = 0 AND t.name = '主任医师'
                """), "其中 2 名主任医师");

        assertEquals(10, count("SELECT COUNT(*) FROM `patient` WHERE deleted = 0"), "10 就诊人");
        assertEquals(5, count("SELECT COUNT(DISTINCT relation) FROM `patient` WHERE deleted = 0"),
                "就诊人覆盖 5 种关系");
        assertEquals(5, count("SELECT COUNT(*) FROM `inpatient` WHERE deleted = 0"), "5 住院人");
        assertEquals(3, count("""
                SELECT COUNT(*) FROM `inpatient`
                WHERE deleted = 0 AND department IS NOT NULL AND bed_no IS NOT NULL
                """), "其中 3 个有住院记录（科室+床位已填）");

        LocalDate today = LocalDate.now();
        assertEquals(15, count("""
                SELECT COUNT(DISTINCT `date`) FROM `schedule`
                WHERE deleted = 0 AND `date` BETWEEN ? AND ?
                """, Date.valueOf(today.minusDays(7)), Date.valueOf(today.plusDays(7))),
                "排班覆盖近 2 周（CURDATE 起 -7..+7 共 15 天）");
        assertEquals(150, count("SELECT COUNT(*) FROM `schedule` WHERE deleted = 0"),
                "15 天 × 5 医生 × 2 时段");
        assertTrue(count("""
                SELECT COUNT(*) FROM (
                    SELECT `date`, time_slot FROM `schedule`
                    WHERE deleted = 0 GROUP BY `date`, time_slot HAVING COUNT(*) > 1
                ) multi
                """) > 0, "存在同一日期同一时段排了多名医生的场景");

        assertEquals(4, count("SELECT COUNT(DISTINCT status) FROM `appointment` WHERE deleted = 0"),
                "预约覆盖待支付/已确认/已完成/已取消");
        assertEquals(0, count("""
                SELECT COUNT(*) FROM `appointment`
                WHERE deleted = 0 AND appointment_time < ?
                  AND status IN ('PENDING_PAYMENT', 'CONFIRMED')
                """, Timestamp.valueOf(today.atStartOfDay())),
                "过去的时段上不得留待支付/已确认，否则是假数据");

        assertEquals(3, count("SELECT COUNT(DISTINCT status) FROM `recharge_record`"),
                "充值覆盖 PENDING/SUCCESS/REFUNDED");
        assertEquals(1, count("SELECT COUNT(*) FROM `recharge_record` WHERE inpatient_id IS NOT NULL"),
                "含 1 笔住院充值（走 inpatient_id 而非 patient_id）");
        assertEquals(4, count("SELECT COUNT(*) FROM `payment_record`"), "4 笔缴费");
        assertEquals(1, count("SELECT COUNT(*) FROM `payment_record` WHERE status = 'PENDING'"),
                "缴费含待缴费");
        assertEquals(1, count("""
                SELECT COUNT(*) FROM `refund_record` rf
                JOIN `payment_record` py ON py.id = rf.related_id
                WHERE rf.related_type = 'PAYMENT' AND rf.amount_fen < py.amount_fen
                """), "部分退款：退款额 < 原单额（payment_record 没有部分退款状态位）");
        assertEquals(1, count("""
                SELECT COUNT(*) FROM `refund_record` rf
                JOIN `recharge_record` rc ON rc.id = rf.related_id
                WHERE rf.related_type = 'RECHARGE' AND rf.amount_fen = rc.amount_fen
                """), "整单退款：对应充值记录已置 REFUNDED");
    }

    @Test
    void seedPassesSelfCheck() {
        seedService.apply();

        List<String> violations = seedCheckService.check();
        assertTrue(violations.isEmpty(), "正确种子必须全绿，实际违规：" + violations);
    }

    /** 人为改坏一个数字，自检必须报错——否则上面的「全绿」是不可证伪的。 */
    @Test
    void breakingOneNumberMakesSelfCheckFail() {
        seedService.apply();

        int updated = jdbcTemplate.update("""
                UPDATE `schedule`
                SET remaining_slots = remaining_slots - 1
                WHERE doctor_id = 1 AND `date` = DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                  AND time_slot = 'MORNING'
                """);
        assertEquals(1, updated, "必须先确认真改到了一行，否则这个测试什么都没测");

        List<String> violations = seedCheckService.check();
        assertEquals(1, violations.size(), "改坏一个号源只该报一条，实际：" + violations);
        assertTrue(violations.get(0).contains("号源自洽"), violations.get(0));
    }

    /** 越界值（剩余号源 > 总号源）同样要报错，改回原值后必须恢复全绿。 */
    @Test
    void outOfRangeRemainingSlotsAreCaught() {
        seedService.apply();

        String where = "WHERE doctor_id = 1 AND `date` = DATE_ADD(CURDATE(), INTERVAL 2 DAY) AND time_slot = 'AFTERNOON'";
        assertEquals(1, jdbcTemplate.update("UPDATE `schedule` SET remaining_slots = total_slots + 1 " + where));
        assertFalse(seedCheckService.check().isEmpty(), "剩余号源超过总号源必须报错");

        assertEquals(1, jdbcTemplate.update("UPDATE `schedule` SET remaining_slots = total_slots " + where));
        assertTrue(seedCheckService.check().isEmpty(), "改回原值后自检应恢复全绿");
    }
}
