package com.systar.ops.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.systar.ops.workorder.entity.WorkOrderLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkOrderLogMapper extends BaseMapper<WorkOrderLogEntity> {
}
