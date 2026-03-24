package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.context.StudentHolder;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.result.Result;
import com.tree.service.CheckInService;
import org.springframework.web.bind.annotation.*;


/**
 * @author SuohaChan
 * @data 2025/9/1
 */
@RestController
@RequestMapping("student/check-in")
public class CheckInController {
    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    /**
     * 学生签到：使用当前登录学生ID（事务在 Service 层）
     */
    @PostMapping("/today")
    @mySystemLog(xxbusinessName = "签到")
    public Result checkInToday() {
        Long studentId = requireStudentId();
        return Result.ok(checkInService.checkInToday(studentId));
    }

    /**
     * 本周签到状态：使用当前登录学生ID
     */
    @GetMapping("/week")
    @mySystemLog(xxbusinessName = "获取本周签到状态")
    public Result getWeekCheckStatus() {
        Long studentId = requireStudentId();
        return Result.ok(checkInService.getWeekCheckStatus(studentId));
    }
    

    private Long requireStudentId() {
        if (StudentHolder.getStudent() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return StudentHolder.getStudent().getId();
    }
}
