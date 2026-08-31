package com.systar.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.systar.data.entity.ScheduledTaskEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTaskEntity> {
}
