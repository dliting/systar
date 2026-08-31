package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.AlarmEscalationPolicyEntity;
import com.systar.data.mapper.AlarmEscalationPolicyMapper;
import com.systar.data.service.AlarmEscalationPolicyService;
import org.springframework.stereotype.Service;

@Service
public class AlarmEscalationPolicyServiceImpl
        extends ServiceImpl<AlarmEscalationPolicyMapper, AlarmEscalationPolicyEntity>
        implements AlarmEscalationPolicyService {
}
