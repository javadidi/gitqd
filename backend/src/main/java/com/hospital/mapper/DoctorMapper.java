package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Doctor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DoctorMapper extends BaseMapper<Doctor> {

    /**
     * 按 id 批量读医生，**不过滤软删行**。
     *
     * <p>T27 开始医生可以被后台删（卡片 736 行「编辑/删除医生信息」），而
     * {@code appointment.doctor_id}（V1:122）与 {@code schedule.doctor_id}（V1:105）都是
     * {@code NOT NULL} 且没有外键——删掉一个医生，历史预约和停诊记录还在那儿。
     * 默认读法（{@code @TableLogic} 追加 {@code deleted = 0}）会让那些行的医生名/科室名集体变空，
     * 管理员看到的是"一批不知道谁看的预约"。这与 T25 的排班、T26 的就诊人是同一个缺陷，
     * 三次都是浏览器/读侧才暴露，所以这次在建删除入口的当卡就把读口备好。
     *
     * <p>手写 SQL 绕开 {@code @TableLogic} 的理由见 {@code ScheduleMapper.selectByIdsIncludingDeleted}。
     */
    @Select("<script>SELECT * FROM doctor WHERE id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
            + "</script>")
    List<Doctor> selectByIdsIncludingDeleted(@Param("ids") Collection<Long> ids);

    /**
     * 按科室取医生 id 集合，**含软删医生**。给 T25 的「按科室筛预约」用：
     * 科室还是那个科室，但底下被删掉的医生也看过病人——
     * 用默认读法筛会得到一份"看起来完整、其实少了已删医生"的名单，
     * 而少掉的那几条正是管理员最可能在查的历史记录。
     */
    @Select("SELECT id FROM doctor WHERE department_id = #{departmentId}")
    List<Long> selectIdsByDepartmentIncludingDeleted(@Param("departmentId") Long departmentId);
}
