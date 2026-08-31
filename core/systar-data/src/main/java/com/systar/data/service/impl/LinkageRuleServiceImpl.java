package com.systar.data.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.data.entity.LinkageRuleEntity;
import com.systar.data.mapper.LinkageRuleMapper;
import com.systar.data.service.LinkageRuleService;
import org.springframework.stereotype.Service;

@Service
public class LinkageRuleServiceImpl extends ServiceImpl<LinkageRuleMapper, LinkageRuleEntity>
        implements LinkageRuleService {
}
