package com.tree.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 开学前要准备的物品
 *
 * @TableName tb_item
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Item implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 准备日期
     */
    private Date date;

    /**
     * 描述
     */
    private String description;

    @Serial
    private static final long serialVersionUID = 1L;
}