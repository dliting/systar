package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.SystemSettingEntity;
import com.systar.data.mapper.SystemSettingMapper;
import com.systar.data.service.SystemSettingService;
import org.springframework.stereotype.Service;

/**
 * System setting service implementation.
 */
@Service
public class SystemSettingServiceImpl extends ServiceImpl<SystemSettingMapper, SystemSettingEntity>
        implements SystemSettingService {
}
