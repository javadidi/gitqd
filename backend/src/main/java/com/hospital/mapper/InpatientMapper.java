package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.Inpatient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InpatientMapper extends BaseMapper<Inpatient> {
}
