package com.systar.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.system.entity.SysNoticeEntity;
import com.systar.system.mapper.SysNoticeMapper;
import com.systar.system.service.SysNoticeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNoticeEntity> implements SysNoticeService {

    private static final Logger log = LoggerFactory.getLogger(SysNoticeServiceImpl.class);

    @Override
    public Page<SysNoticeEntity> listNotices(int page, int size, String title, Integer status) {
        LambdaQueryWrapper<SysNoticeEntity> wrapper = new LambdaQueryWrapper<>();
        if (title != null && !title.isEmpty()) {
            wrapper.like(SysNoticeEntity::getTitle, title);
        }
        if (status != null) {
            wrapper.eq(SysNoticeEntity::getStatus, status);
        }
        wrapper.orderByDesc(SysNoticeEntity::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public SysNoticeEntity getNoticeById(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    public void createNotice(SysNoticeEntity notice) {
        notice.setCreateTime(LocalDateTime.now());
        notice.setUpdateTime(LocalDateTime.now());
        save(notice);
        log.info("Created notice: {}", notice.getTitle());
    }

    @Override
    @Transactional
    public void updateNotice(SysNoticeEntity notice) {
        notice.setUpdateTime(LocalDateTime.now());
        updateById(notice);
        log.info("Updated notice id={}", notice.getId());
    }

    @Override
    @Transactional
    public void deleteNotice(Long id) {
        removeById(id);
        log.info("Deleted notice id={}", id);
    }

    @Override
    @Transactional
    public void publishNotice(Long id) {
        LambdaUpdateWrapper<SysNoticeEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysNoticeEntity::getId, id)
                .set(SysNoticeEntity::getStatus, SysNoticeEntity.STATUS_PUBLISHED)
                .set(SysNoticeEntity::getPublishTime, LocalDateTime.now())
                .set(SysNoticeEntity::getUpdateTime, LocalDateTime.now());
        update(wrapper);
        log.info("Published notice id={}", id);
    }

    @Override
    public List<SysNoticeEntity> getActiveNotices() {
        LambdaQueryWrapper<SysNoticeEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysNoticeEntity::getStatus, SysNoticeEntity.STATUS_PUBLISHED)
                .orderByDesc(SysNoticeEntity::getPublishTime);
        return list(wrapper);
    }
}
