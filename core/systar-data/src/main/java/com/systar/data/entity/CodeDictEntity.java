package com.systar.data.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Code dictionary entity corresponding to the t_code_dict table.
 * <p>
 * Stores code entries grouped by catalog for use as drop-down options
 * and classification references.
 */
@Data
@TableName("t_code_dict")
public class CodeDictEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** Catalog id this entry belongs to. */
    private Integer catalogId;

    /** Code name (unique within catalog). */
    private String name;

    /** Display caption. */
    private String caption;

    /** Parent code id (for hierarchical codes). */
    @TableField("parent")
    private Integer parentId;
}
