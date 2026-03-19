package com.tree.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author SuohaChan
 * @data 2025/9/13
*/


@Data
public class InformationSimpleShowDto {
    /**
     * 主键ID
     */
    private Long id;

    /**
     * 是否必看（0-非必看，1-必看）
     */
    private Integer isRequired;

    /**
     * 资讯标题
     */
    private String title;

    private String scope;

    private String college;

    /**
     * 班级
     */
    private String clazz;
    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 图片URL
     */
    private String imageUrls;
}
