package com.systar.ops.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrderEntity> {
}
