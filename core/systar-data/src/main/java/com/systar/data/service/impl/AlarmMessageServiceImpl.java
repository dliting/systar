package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.AlarmMessageEntity;
import com.systar.data.mapper.AlarmMessageMapper;
import com.systar.data.service.AlarmMessageService;
import org.springframework.stereotype.Service;

/**
 * Alarm message service implementation.
 */
@Service
public class AlarmMessageServiceImpl extends ServiceImpl<AlarmMessageMapper, AlarmMessageEntity>
        implements AlarmMessageService {
}
