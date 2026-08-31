package com.systar.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_sys_menu")
public class SysMenuEntity {

    public static final String TYPE_DIRECTORY = "M";
    public static final String TYPE_MENU = "C";
    public static final String TYPE_BUTTON = "F";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String menuName;

    private Long parentId;

    private String path;

    private String component;

    private String icon;

    private String perms;

    private String menuType;

    private Integer orderNum;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<SysMenuEntity> children;
}
