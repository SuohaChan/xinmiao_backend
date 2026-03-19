package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.context.StudentHolder;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.result.Result;
import com.tree.service.StudentCourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 学生课程接口
 */
@RestController
@RequestMapping("student")
public class StudentCourseController {

    @Autowired
    StudentCourseService studentCourseService;

    @GetMapping("course-schedule")
    @mySystemLog(xxbusinessName = "课表")
    public Result getCourseSchedule(@RequestParam(required = false) Long studentId) {
        Long targetId = studentId;
        if (targetId == null) {
            if (StudentHolder.getStudent() == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
            }
            targetId = StudentHolder.getStudent().getId();
        }
        List<Map<String, Object>> schedule = studentCourseService.getCourseScheduleByStudentId(targetId);
        return Result.ok(schedule);
    }
}
