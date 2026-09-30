package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    /**
     * 按 id 批量读科室，**不过滤软删行**。与 {@link DoctorMapper#selectByIdsIncludingDeleted}
     * 同一条理由：科室名不在预约表上，是经 {@code doctor.department_id}（V1:90）两跳解析出来的，
     * 医生或科室被后台删掉之后，历史预约的「科室」那一栏必须有名字可显示。
     */
    @Select("<script>SELECT * FROM department WHERE id IN "
            + "<foreach collection=\"ids\" item=\"id\" open=\"(\" separator=\",\" close=\")\">#{id}</foreach>"
            + "</script>")
    List<Department> selectByIdsIncludingDeleted(@Param("ids") Collection<Long> ids);
}
