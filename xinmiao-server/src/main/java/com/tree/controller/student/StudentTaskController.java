package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.dto.CompleteTaskDto;
import com.tree.result.Result;
import com.tree.service.RankService;
import com.tree.service.StudentTaskService;
import com.tree.service.TaskService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("student/tasks")
public class StudentTaskController {

    @Resource
    private StudentTaskService studentTaskService;

    @Resource
    private TaskService taskService;

    @Resource
    private RankService rankService;

    /**
     * 通过任务类型查询任务
     * 通过token获取学生信息，根据学生信息获取任务
     * @param type 任务类型
     * @return Result对象
     */
    @GetMapping
    @mySystemLog(xxbusinessName = "通过任务类型查询任务")
    public Result getTasksByType(@RequestParam String type) {
        return Result.ok(studentTaskService.getTasksByType(type));
    }

    @PostMapping("/complete")
    @mySystemLog(xxbusinessName = "完成任务")
    public Result completeTask(@Validated @RequestBody CompleteTaskDto dto) {
        return Result.ok(studentTaskService.completeTask(dto));
    }

    @GetMapping("/rankings/today")
    @mySystemLog(xxbusinessName = "获取今日排行榜")
    public Result getTodayRank() {
        return Result.ok(rankService.getTodayRank());
    }

    @GetMapping("/rankings/week")
    @mySystemLog(xxbusinessName = "获取周排行榜")
    public Result getWeekRank() {
        return Result.ok(rankService.getWeekRank());
    }

    @GetMapping("/rankings/college")
    @mySystemLog(xxbusinessName = "获取学院排行榜")
    public Result getCollegeRank() {
        return Result.ok(rankService.getCollegeRank());
    }
}