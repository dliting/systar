package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Code catalog entity corresponding to the t_code_catalog table.
 * <p>
 * Groups code dictionary entries into categories.
 */
@Data
@TableName("t_code_catalog")
public class CodeCatalogEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Catalog name. */
    private String name;
}
