package com.hospital;

import com.hospital.service.SeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 卡片第 4 项里「故意 2 条重复就诊卡号验唯一索引」「2 名医生同一时段验冲突」的落地方式。
 *
 * <p>这两条都不能写成 seed.sql 里的正式行：V1:34 的 uk_card_no 会让种子文件直接插失败，
 * 而「清库+迁移+种子+自检一路绿」是 db:reset 的前提。所以重复数据做成这里的负例夹具——
 * 现场插一条重复的，断言数据库拒绝，事务回滚不留痕。
 */
@SpringBootTest
@Transactional
class SeedConstraintTest {

    @Autowired
    private SeedService seedService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void applySeed() {
        seedService.apply();
    }

    @Test
    void duplicateCardNoIsRejectedByUniqueIndex() {
        applySeed();

        DuplicateKeyException ex = assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO `patient` (user_id, name, id_card, phone, relation, card_no)
                VALUES (2, '冒用他人卡号', 'SEED_ENC:id_card:9001', 'SEED_ENC:patient_phone:9001',
                        'OTHER', '1000000001')
                """));
        assertEquals(1, count("SELECT COUNT(*) FROM `patient` WHERE card_no = '1000000001'"),
                "被拒的这条没有落库，原行仍在：" + ex.getMessage());
    }

    @Test
    void duplicateInpatientNoIsRejectedByUniqueIndex() {
        applySeed();

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO `inpatient` (user_id, name, inpatient_no)
                VALUES (2, '冒用他人住院号', 'ZY20260001')
                """));
        assertEquals(1, count("SELECT COUNT(*) FROM `inpatient` WHERE inpatient_no = 'ZY20260001'"));
    }

    /** uk_doctor_date_slot 带 doctor_id，所以「冲突」只存在于同一医生。 */
    @Test
    void sameDoctorCannotBeScheduledTwiceInOneSlot() {
        applySeed();

        assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update("""
                INSERT INTO `schedule` (doctor_id, `date`, time_slot, total_slots, remaining_slots)
                VALUES (1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'MORNING', 10, 10)
                """));
    }

    /** 反过来：不同医生同一天同一时段是合法排班，索引不拦，也不该拦。 */
    @Test
    void differentDoctorsCanShareTheSameDateAndSlot() {
        applySeed();

        int inserted = jdbcTemplate.update("""
                INSERT INTO `schedule` (doctor_id, `date`, time_slot, total_slots, remaining_slots)
                VALUES (1, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'EVENING', 10, 10),
                       (2, DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'EVENING', 10, 10)
                """);
        assertEquals(2, inserted);
        assertEquals(2, count("""
                SELECT COUNT(*) FROM `schedule`
                WHERE doctor_id IN (1, 2) AND `date` = DATE_ADD(CURDATE(), INTERVAL 7 DAY)
                  AND time_slot = 'EVENING'
                """));
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
