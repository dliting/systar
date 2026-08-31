package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.AlarmRuleEntity;
import com.systar.data.mapper.AlarmRuleMapper;
import com.systar.data.service.AlarmRuleService;
import org.springframework.stereotype.Service;

/**
 * Alarm rule service implementation.
 */
@Service
public class AlarmRuleServiceImpl extends ServiceImpl<AlarmRuleMapper, AlarmRuleEntity>
        implements AlarmRuleService {
}
