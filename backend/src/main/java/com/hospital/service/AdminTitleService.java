package com.hospital.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hospital.annotation.AuditLog;
import com.hospital.annotation.AuditTarget;
import com.hospital.common.ErrorCode;
import com.hospital.dto.AdminTitleResponse;
import com.hospital.dto.TitleSaveRequest;
import com.hospital.entity.Doctor;
import com.hospital.entity.Title;
import com.hospital.exception.BizException;
import com.hospital.mapper.DoctorMapper;
import com.hospital.mapper.TitleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 职称的读写（T28 卡片 764 行「职称管理：CRUD」/ PRD 4.6.3 的 457–458 行）。
 *
 * <h2>本卡只有三把端点：列表、新增、编辑，没有删除</h2>
 * PRD 那一节只给了两句（「职称列表」「新增职称」），跟卡片 764 行的「CRUD」差一个动词。
 * 这不是第一次遇到，也不该用另一种方式处理：T27 的套餐类型（卡片 740 行 CRUD vs PRD 416–417 两句）
 * 是同一形状，当时的结论是<b>不开删除这把端点</b>，理由写在那张卡的类注释和 WORK_LOG 的有意未做里。
 * 本卡沿用同一条结论，两条独立的理由：
 * <ul>
 *   <li>{@code doctor.title_id}（V1:88）是 DEFAULT NULL——真删了，那些医生的职称列不会报错，
 *       而是<b>静默变成"未分类"</b>。一个还有 3 个人在用的「主任医师」被删掉以后，
 *       医生列表上那 3 行看起来像数据缺失，而没有任何一处写着"这里少了一个职称"；</li>
 *   <li>seed 第 2 段那三行名字是 PRD 457 行括号里点名的（主任医师、副主任医师、主治医师），
 *       删掉规格钦定的数据这件事，规格里没有任何一句授权。</li>
 * </ul>
 * 写错名字的真实需求由"编辑"覆盖（改名即可），不需要删除。
 * 列表因此带 {@code doctorCount}：让管理员在页面上看见每个职称在用还是没在用。
 *
 * <h2>重名不做查重，与 T27 的套餐类型一致</h2>
 * {@code title.name} 没有唯一索引（V1:72-79 只有主键），也没有任何东西按名字取职称
 * ——{@code AdminCatalogCommandService.options()} 给医生表单的是 id + name 的清单。
 * 两行同名职称在库里就是"同一个名字可以选两次"，丑但不坏；
 * 反过来，加一层查重校验就等于在本卡里发明一条规格里没有的规则
 * （而且它没有索引兜底，并发下照样会漏，T11/T08 那批"第二层"都是有唯一索引才做的）。
 */
@Service
public class AdminTitleService {

    private final TitleMapper titleMapper;
    private final DoctorMapper doctorMapper;

    public AdminTitleService(TitleMapper titleMapper, DoctorMapper doctorMapper) {
        this.titleMapper = titleMapper;
        this.doctorMapper = doctorMapper;
    }

    /** 职称列表（PRD 457 行），按 sort_order 升序、同值按 id 升序，与 {@code options()} 同一顺序。 */
    public List<AdminTitleResponse> list() {
        List<Title> rows = titleMapper.selectList(new LambdaQueryWrapper<Title>()
                .orderByAsc(Title::getSortOrder).orderByAsc(Title::getId));
        Map<Long, Long> doctorCounts = doctorCountByTitle();
        List<AdminTitleResponse> result = new ArrayList<>();
        for (Title row : rows) {
            result.add(toResponse(row, doctorCounts.getOrDefault(row.getId(), 0L)));
        }
        return result;
    }

    @AuditLog(action = "CREATE_TITLE", targetType = "title")
    @Transactional
    public AdminTitleResponse create(TitleSaveRequest request) {
        Title entity = new Title();
        entity.setName(request.getName().trim());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        titleMapper.insert(entity);
        return toResponse(titleMapper.selectById(entity.getId()), 0L);
    }

    @AuditLog(action = "UPDATE_TITLE", targetType = "title")
    @Transactional
    public AdminTitleResponse update(@AuditTarget Long id, TitleSaveRequest request) {
        requireTitle(id);
        // 显式 SET（含 null）：sort_order 被清空要真的能清成 0，updateById 会跳过 null。
        titleMapper.update(null, new LambdaUpdateWrapper<Title>()
                .eq(Title::getId, id)
                .set(Title::getName, request.getName().trim())
                .set(Title::getSortOrder, request.getSortOrder() == null ? 0 : request.getSortOrder()));
        Title row = titleMapper.selectById(id);
        return toResponse(row, doctorCountByTitle().getOrDefault(id, 0L));
    }

    private Title requireTitle(Long id) {
        Title title = id == null ? null : titleMapper.selectById(id);
        if (title == null) {
            throw new BizException(ErrorCode.DATA_NOT_FOUND.getCode(), "职称不存在");
        }
        return title;
    }

    /**
     * 每个职称在用几位医生。只算<b>活着</b>的医生（默认读法带 {@code deleted = 0}）：
     * 这里的数字是给"这个职称还有没有人在用"看的展示值，
     * 与 T27 删除守卫里那种"软删医生的历史单据还要认名字"的读法不是同一件事，
     * 所以不复用 {@code DoctorMapper} 的 IncludingDeleted 查询。
     */
    private Map<Long, Long> doctorCountByTitle() {
        List<Doctor> doctors = doctorMapper.selectList(
                new LambdaQueryWrapper<Doctor>().select(Doctor::getTitleId));
        Map<Long, Long> counts = new HashMap<>();
        for (Doctor doctor : doctors) {
            if (doctor.getTitleId() != null) {
                counts.merge(doctor.getTitleId(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private AdminTitleResponse toResponse(Title row, Long doctorCount) {
        AdminTitleResponse response = new AdminTitleResponse();
        response.setId(row.getId());
        response.setName(row.getName());
        response.setSortOrder(row.getSortOrder());
        response.setDoctorCount(doctorCount);
        return response;
    }
}
