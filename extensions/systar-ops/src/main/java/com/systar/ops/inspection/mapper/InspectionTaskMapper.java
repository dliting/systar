package com.systar.ops.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.systar.ops.inspection.entity.InspectionTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InspectionTaskMapper extends BaseMapper<InspectionTaskEntity> {
}
