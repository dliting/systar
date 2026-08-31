package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.AlarmSilenceWindowEntity;
import com.systar.data.mapper.AlarmSilenceWindowMapper;
import com.systar.data.service.AlarmSilenceWindowService;
import org.springframework.stereotype.Service;

@Service
public class AlarmSilenceWindowServiceImpl
        extends ServiceImpl<AlarmSilenceWindowMapper, AlarmSilenceWindowEntity>
        implements AlarmSilenceWindowService {
}
