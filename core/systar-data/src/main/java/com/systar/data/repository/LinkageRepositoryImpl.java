package com.systar.data.repository;

import com.systar.data.entity.LinkageLogEntity;
import com.systar.data.mapper.LinkageLogMapper;
import com.systar.monitor.linkage.LinkageRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class LinkageRepositoryImpl implements LinkageRepository {

    private final LinkageLogMapper linkageLogMapper;

    public LinkageRepositoryImpl(LinkageLogMapper linkageLogMapper) {
        this.linkageLogMapper = linkageLogMapper;
    }

    @Override
    public void saveLinkageLog(int ruleId, int causeMonitorId,
                               int effectMonitorId, String effectCommand,
                               boolean success) {
        LinkageLogEntity entity = new LinkageLogEntity();
        entity.setRuleId(ruleId);
        entity.setCauseMonitorId(causeMonitorId);
        entity.setEffectMonitorId(effectMonitorId);
        entity.setTriggerTime(LocalDateTime.now());
        entity.setEffectCommand(effectCommand);
        entity.setSuccess(success);
        linkageLogMapper.insert(entity);
    }
}
