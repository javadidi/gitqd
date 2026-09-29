package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.ErrorCode;
import com.hospital.dto.ReportDetailResponse;
import com.hospital.dto.ReportListItemResponse;
import com.hospital.entity.Patient;
import com.hospital.entity.Report;
import com.hospital.enums.ReportType;
import com.hospital.exception.BizException;
import com.hospital.mapper.PatientMapper;
import com.hospital.mapper.ReportMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报告查询（T17 卡片 545–558 行：选择报告类型 / 报告列表 / 报告详情）。
 *
 * <p>纯读，与 T10 {@code CatalogService}、T13 {@code AppointmentQueryService}、T16
 * {@code QueueService} 同一个分工：<b>故意不加 {@code @Transactional}</b>，只读查询套事务只是白占连接。
 *
 * <h2>本卡一张报告都不生产</h2>
 * {@code report} 表和 T16 的 {@code queue_status} 同构：<b>首版没有生产者</b>——
 * {@code seed.sql} 里零行（已逐字 grep 过 {@code insert into report}，无匹配），
 * 任务卡 28 张里没有任何一张写它（卡片 642/648 行的「体检报告」是 <b>T22</b> 的 J50，
 * 卡片 622/628 行的「核酸检测报告」是 <b>T21</b> 的 J48，两边都是各自模块的表与页面），
 * PRD §4 后台章节也没有报告录入页。所以这里只有读。
 * <b>至于真实部署里谁来写这张表，规格从没说过</b>（全仓 grep 过 PRD：没有 LIS/PACS/HIS
 * 任何一个词，只有 486 行提过一次「对接微信支付安全接口」）——"由院内检验/影像系统推入"
 * 是我按行业常识做的<strong>推断</strong>，只能当设计背景读，不能当出处引用。
 * 所以验收要取证只能裸插探针行，并在日志里标成「人工取证探针」。
 *
 * <p>反面约束也记一句：PRD 499 行「报告数据需长期保存（≥ 5年）」——本卡不删不改，
 * 唯一碰这张表的地方是测试自己插的探针行，收尾物理删掉。
 *
 * <h2>类型白名单：列表与详情过同一道闸（T22 放开了 PHYSICAL）</h2>
 * 列表 {@link #list} 只收 {@link ReportType#isQueryable} 允许的类型；
 * 详情 {@link #detail} <b>也</b>要过同一道白名单——只筛列表等于留一扇门：
 * 拿到别人报告 id 的人照样能绕过列表直读详情。
 * 越界读按 {@code 5001} 而不是 403：与"这条报告不存在"同码，不确认存在性
 * （T08/T09/T14/T15 一路沿用下来的同一口径）。
 *
 * <p>T17 建卡时白名单只有 LAB/IMAGING，体检报告（PHYSICAL）两处都进不来；
 * <b>T22（卡片 642 行 / J50）把 PHYSICAL 放开了</b>，钩子就是 {@code ReportType} 里预留的那一行。
 * 放开之后这道闸的<b>安全含义一点没变</b>：它挡的仍然是"类型不认识"的行，
 * 而"这条报告不是你的"由归属那一跳挡，两件事各管各的。
 * 变化只有一处：T17 的「选择报告类型」页仍只列检验/检查两项（PRD 161 行原话），
 * 所以体检报告只能从 T22 的入口带 {@code ?type=PHYSICAL} 进来。
 *
 * <h2>归属：一跳</h2>
 * {@code report} 没有 {@code user_id}（V1:202–215），所以"这条报告是不是你的"必须经
 * {@code report.patient_id → patient.user_id} 跳一次。列表用
 * {@code patient_id IN (我的就诊人)} 一次收口；详情双条件定位，越权与不存在同为 5001。
 */
@Service
public class ReportService {

    private final ReportMapper reportMapper;
    private final PatientMapper patientMapper;
    private final ObjectMapper objectMapper;

    public ReportService(ReportMapper reportMapper,
                         PatientMapper patientMapper,
                         ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.patientMapper = patientMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 某一类报告的列表，按报告时间倒序（最新在前——患者进这页就是要看刚出的那份）。
     *
     * <p>{@code type} 是<strong>必填</strong>：卡片 548 行与 PRD 161 行都把"选择报告类型"
     * 定为流程第一步，§6.1 第 518 行还把它单列成一个页面名。所以本端点没有"返回全部类型"
     * 这种形态，混排两类报告的诉求规格里不存在。
     */
    public List<ReportListItemResponse> list(Long userId, String type) {
        if (!ReportType.isQueryable(type)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "报告类型只支持 LAB（检验报告）或 IMAGING（检查报告）");
        }
        List<Long> myPatientIds = myPatientIds(userId);
        if (myPatientIds.isEmpty()) {
            // 一个就诊人也没有 → 不可能有报告。直接回空列表，也免得把 IN () 空集合交给 MyBatis。
            return List.of();
        }
        List<Report> rows = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .in(Report::getPatientId, myPatientIds)
                .eq(Report::getType, type)
                .orderByDesc(Report::getReportTime)
                .orderByDesc(Report::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> patientNames = patientNamesOf(rows);
        return rows.stream().map(row -> {
            ReportListItemResponse item = new ReportListItemResponse();
            item.setReportId(row.getId());
            item.setReportNo(row.getReportNo());
            item.setType(row.getType());
            item.setPatientName(patientNames.get(row.getPatientId()));
            item.setReportTime(row.getReportTime());
            return item;
        }).toList();
    }

    /** 报告详情（卡片 550 行）。越权、不存在、软删、类型不认识四种情况同为 5001。 */
    public ReportDetailResponse detail(Long userId, Long reportId) {
        Report report = reportMapper.selectById(reportId);
        // 软删行到这里已经是 null：Report extends BaseEntity，deleted 上有 @TableLogic，
        // MP 会给 selectById 自动追加 deleted=0（T08-G 起这条一直是这个仓库的默认行为）。
        if (report == null || !myPatientIds(userId).contains(report.getPatientId())
                || !ReportType.isQueryable(report.getType())) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
        ReportDetailResponse detail = new ReportDetailResponse();
        detail.setReportId(report.getId());
        detail.setReportNo(report.getReportNo());
        detail.setType(report.getType());
        Patient owner = patientMapper.selectById(report.getPatientId());
        detail.setPatientName(owner == null ? null : owner.getName());
        detail.setReportTime(report.getReportTime());
        detail.setResult(report.getResult());
        detail.setItems(parseItems(report.getItems()));
        return detail;
    }

    /**
     * {@code report.items} 是 JSON 列（V1:207），形状<strong>没有任何规格出处</strong>
     * （详见 {@link ReportDetailResponse} 的类注释）。所以这里只做"合法 JSON → JsonNode"
     * 这一步转换，不挑字段、不改键名，原样透传。
     *
     * <p>解析失败按理不可能发生（MySQL 的 JSON 列自己会拒非法 JSON），但仍必须处理
     * 检查异常：按 {@code 5001} 报出去，与 T15 的 {@code parseItems} 同一个选择——
     * 一条读不出来的报告不该变成整页 500。
     */
    private com.fasterxml.jackson.databind.JsonNode parseItems(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
        }
    }

    private List<Long> myPatientIds(Long userId) {
        return patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                        .eq(Patient::getUserId, userId))
                .stream().map(Patient::getId).toList();
    }

    /** 一次批量捞名字，不逐行查（列表 20 条就是 20 次 SQL）。与 T13/T16 同一理由。 */
    private Map<Long, String> patientNamesOf(List<Report> rows) {
        List<Long> ids = rows.stream().map(Report::getPatientId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return patientMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getName));
    }
}
