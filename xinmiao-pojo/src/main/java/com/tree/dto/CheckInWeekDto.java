package com.tree.dto;

import lombok.Data;
import lombok.Getter;
import java.util.List;

/**
 * @author SuohaChan
 * @data 2025/9/2
 */
@Data
@Getter
public class CheckInWeekDto {
    //今日签到状态
   private boolean todayChecked;

   //本周七天内的签到状态
    private List<Boolean> weekCheckStatus;

}