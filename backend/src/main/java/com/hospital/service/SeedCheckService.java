package com.hospital.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 种子数据自检（任务卡 T06 第 5 项），三条检查严格对应卡片点名的三项：
 * 排班号源=预约数、就诊卡号唯一、住院号唯一。不额外加检查项。
 *
 * <p>号源口径：{@code remaining_slots = total_slots - 未取消的预约数}。
 * 「取消即释放号源」是暂定规则，PRD 未规定，T12 实现退号时复核。
 * 剩余号源须落在 [0, total_slots] 内，属于同一条号源自洽检查，不是第四项。
 *
 * <p>两条唯一性检查读全表（不过滤 deleted），与 uk_card_no / uk_inpatient_no 的语义一致：
 * 软删行照样占着唯一索引。所以只要索引在，这两条不可能报错，真正拦住重复的是索引本身
 * （由 SeedConstraintTest 的负例夹具证明）；保留它们是为了兜住手工造数、绕过 DDL 的环境。
 */
@Service
public class SeedCheckService {

    /** 单次检查最多回显的违规明细条数，超出只报总数。 */
    private static final int MAX_REPORTED_ROWS = 20;

    private static final String SLOT_MISMATCH_SQL = """
            SELECT s.id, s.doctor_id, s.`date`, s.time_slot,
                   s.total_slots, s.remaining_slots, COALESCE(h.held, 0) AS held
            FROM `schedule` s
            LEFT JOIN (
                SELECT a.schedule_id, COUNT(*) AS held
                FROM `appointment` a
                WHERE a.status <> 'CANCELLED' AND a.deleted = 0
                GROUP BY a.schedule_id
            ) h ON h.schedule_id = s.id
            WHERE s.deleted = 0
              AND (s.remaining_slots <> s.total_slots - COALESCE(h.held, 0)
                   OR s.remaining_slots < 0
                   OR s.remaining_slots > s.total_slots)
            ORDER BY s.`date`, s.doctor_id, s.time_slot
            """;

    private static final String DUPLICATE_CARD_NO_SQL = """
            SELECT card_no, COUNT(*) AS c
            FROM `patient`
            GROUP BY card_no
            HAVING COUNT(*) > 1
            ORDER BY card_no
            """;

    private static final String DUPLICATE_INPATIENT_NO_SQL = """
            SELECT inpatient_no, COUNT(*) AS c
            FROM `inpatient`
            GROUP BY inpatient_no
            HAVING COUNT(*) > 1
            ORDER BY inpatient_no
            """;

    private final JdbcTemplate jdbcTemplate;

    public SeedCheckService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @return 违规描述列表，空列表即自检通过。 */
    public List<String> check() {
        List<String> violations = new ArrayList<>();
        collectSlotViolations(violations);
        collectDuplicateViolations(violations, DUPLICATE_CARD_NO_SQL, "card_no", "就诊卡号");
        collectDuplicateViolations(violations, DUPLICATE_INPATIENT_NO_SQL, "inpatient_no", "住院号");
        return violations;
    }

    private void collectSlotViolations(List<String> violations) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SLOT_MISMATCH_SQL);
        if (rows.isEmpty()) {
            return;
        }
        rows.stream().limit(MAX_REPORTED_ROWS).forEach(row -> {
            int total = ((Number) row.get("total_slots")).intValue();
            int held = ((Number) row.get("held")).intValue();
            violations.add(String.format(
                    "号源自洽：排班 id=%s（doctor=%s，%s %s）总号源 %d，非取消预约 %d 笔，"
                            + "剩余应为 %d，实际 %s",
                    row.get("id"), row.get("doctor_id"), row.get("date"), row.get("time_slot"),
                    total, held, total - held, row.get("remaining_slots")));
        });
        appendOverflow(violations, rows.size(), "号源自洽");
    }

    private void collectDuplicateViolations(List<String> violations, String sql, String column, String label) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        if (rows.isEmpty()) {
            return;
        }
        rows.stream().limit(MAX_REPORTED_ROWS).forEach(row -> violations.add(String.format(
                "%s唯一性：%s 出现 %s 次", label, row.get(column), row.get("c"))));
        appendOverflow(violations, rows.size(), label + "唯一性");
    }

    private void appendOverflow(List<String> violations, int total, String title) {
        if (total > MAX_REPORTED_ROWS) {
            violations.add(title + "：另有 " + (total - MAX_REPORTED_ROWS) + " 条同类违规未展开");
        }
    }
}
