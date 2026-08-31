package com.systar.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_sys_notice")
public class SysNoticeEntity {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int TYPE_NOTICE = 1;
    public static final int TYPE_ANNOUNCEMENT = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private Integer type;

    private Integer status;

    private LocalDateTime publishTime;

    private String createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
