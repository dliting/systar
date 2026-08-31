package com.systar.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.systar.data.entity.ErrorMessageLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErrorMessageLogMapper extends BaseMapper<ErrorMessageLogEntity> {
}
