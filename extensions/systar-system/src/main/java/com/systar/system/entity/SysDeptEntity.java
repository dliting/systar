package com.systar.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_sys_dept")
public class SysDeptEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deptName;

    private Long parentId;

    private String ancestors;

    private Integer orderNum;

    private String leader;

    private String phone;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<SysDeptEntity> children;
}
