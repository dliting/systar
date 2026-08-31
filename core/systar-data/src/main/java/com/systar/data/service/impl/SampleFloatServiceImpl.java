package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.SampleFloatEntity;
import com.systar.data.mapper.SampleFloatMapper;
import com.systar.data.service.SampleFloatService;
import org.springframework.stereotype.Service;

/**
 * Sample float service implementation.
 */
@Service
public class SampleFloatServiceImpl extends ServiceImpl<SampleFloatMapper, SampleFloatEntity>
        implements SampleFloatService {
}
