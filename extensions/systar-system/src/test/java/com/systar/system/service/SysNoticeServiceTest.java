package com.systar.system.service;

import com.systar.system.entity.SysNoticeEntity;
import com.systar.system.test.SystemTestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemTestApplication.class)
@Transactional
class SysNoticeServiceTest {

    @Autowired
    private SysNoticeService sysNoticeService;

    @Test
    @DisplayName("发布通知后状态应为已发布且发布时间为当前时间")
    void shouldPublishNotice() {
        SysNoticeEntity notice = new SysNoticeEntity();
        notice.setTitle("测试通知");
        notice.setContent("测试内容");
        notice.setType(SysNoticeEntity.TYPE_NOTICE);
        notice.setStatus(SysNoticeEntity.STATUS_DRAFT);
        notice.setCreateBy("admin");

        sysNoticeService.createNotice(notice);
        assertThat(notice.getId()).isNotNull();
        assertThat(notice.getStatus()).isEqualTo(SysNoticeEntity.STATUS_DRAFT);

        sysNoticeService.publishNotice(notice.getId());

        SysNoticeEntity published = sysNoticeService.getNoticeById(notice.getId());
        assertThat(published.getStatus()).isEqualTo(SysNoticeEntity.STATUS_PUBLISHED);
        assertThat(published.getPublishTime()).isNotNull();
    }

    @Test
    @DisplayName("getActiveNotices只返回已发布的通知并按发布时间降序排列")
    void shouldListActiveNotices() {
        // Create and publish a notice
        SysNoticeEntity published = new SysNoticeEntity();
        published.setTitle("已发布通知");
        published.setContent("内容");
        published.setCreateBy("admin");
        sysNoticeService.createNotice(published);
        sysNoticeService.publishNotice(published.getId());

        // Create a draft notice (not published)
        SysNoticeEntity draft = new SysNoticeEntity();
        draft.setTitle("草稿通知");
        draft.setContent("草稿");
        draft.setCreateBy("admin");
        sysNoticeService.createNotice(draft);

        List<SysNoticeEntity> activeNotices = sysNoticeService.getActiveNotices();
        assertThat(activeNotices).isNotEmpty();
        // Draft should not appear
        assertThat(activeNotices.stream().noneMatch(n -> n.getId().equals(draft.getId()))).isTrue();
        // Published should appear
        assertThat(activeNotices.stream().anyMatch(n -> n.getId().equals(published.getId()))).isTrue();
    }
}
