package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.AlarmCorrelationRuleEntity;
import com.systar.data.mapper.AlarmCorrelationRuleMapper;
import com.systar.data.service.AlarmCorrelationRuleService;
import org.springframework.stereotype.Service;

@Service
public class AlarmCorrelationRuleServiceImpl
        extends ServiceImpl<AlarmCorrelationRuleMapper, AlarmCorrelationRuleEntity>
        implements AlarmCorrelationRuleService {
}
