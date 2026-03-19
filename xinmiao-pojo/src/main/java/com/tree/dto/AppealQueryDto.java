package com.tree.dto;

import lombok.Data;

/**
 * @author SuohaChan
 * @data 2025/9/20
 */
@Data
public class AppealQueryDto {
    private  String id;//申诉事件id

    private Long appealerId;//申诉人id

    private String startTime;

    private String status;  // accept/rejected
}
