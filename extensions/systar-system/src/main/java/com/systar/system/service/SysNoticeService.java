package com.systar.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.system.entity.SysNoticeEntity;

import java.util.List;

/**
 * Notice management service.
 */
public interface SysNoticeService {

    Page<SysNoticeEntity> listNotices(int page, int size, String title, Integer status);

    SysNoticeEntity getNoticeById(Long id);

    void createNotice(SysNoticeEntity notice);

    void updateNotice(SysNoticeEntity notice);

    void deleteNotice(Long id);

    void publishNotice(Long id);

    List<SysNoticeEntity> getActiveNotices();
}
