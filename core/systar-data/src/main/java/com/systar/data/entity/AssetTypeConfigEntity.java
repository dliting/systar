package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_asset_type_config")
public class AssetTypeConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String kind;

    private String typeName;

    private String caption;

    private String driverClass;

    private String properties;

    private Integer version;

    private String content;
}
