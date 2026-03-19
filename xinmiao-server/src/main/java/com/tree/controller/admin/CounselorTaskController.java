package com.tree.controller.admin;

import com.tree.annotation.mySystemLog;
import com.tree.constant.PaginationConstants;
import com.tree.result.Result;
import com.tree.service.CounselorTaskService;
import com.tree.service.TaskService;
import com.tree.dto.TaskQueryDto;
import com.tree.entity.Task;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 辅导员端任务接口（规范路径 /counselor/tasks）
 * 合并了原 CounselorTaskController 与 CounselorTaskController：
 * - /counselor/tasks...：任务 CRUD 与分页
 * - /counselor/tasks/class-completion、/counselor/tasks/task-status：任务统计
 * 同时兼容旧前缀 /counselorTask。
 */
@RestController
@RequestMapping("counselor/tasks")
public class CounselorTaskController {
    @Resource
    private TaskService taskService;

    @Resource
    private CounselorTaskService counselorTaskService;

    /**
     * 添加任务
     *
     * @param task 要添加的任务
     * @return Result对象
     */
    @PostMapping
    public Result addTask(@Validated @RequestBody Task task) {
        taskService.addTask(task);
        return Result.ok("任务创建成功");
    }

    /** 规范路径: GET /counselor/tasks?page=1&size=10&... */
    @mySystemLog(xxbusinessName = "条件分页查询任务")
    @GetMapping
    public Result page(TaskQueryDto queryDTO,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size) {
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, PaginationConstants.MAX_PAGE_SIZE);
        return Result.ok(taskService.page(queryDTO, page == null || page < 1 ? 1 : page, safeSize));
    }

    @PutMapping
    @mySystemLog(xxbusinessName = "修改任务")
    public Result<?> updateTask(@Validated @RequestBody Task task) {
        taskService.updateTask(task);
        return Result.ok();
    }

    /** 规范路径: DELETE /counselor/tasks/{id} 或 ?ids= */
    @DeleteMapping("/{id}")
    @mySystemLog(xxbusinessName = "通过id删除任务")
    public Result<?> deleteTaskById(@PathVariable Long id) {
        taskService.deleteTaskById(id);
        return Result.ok("删除成功");
    }

    @DeleteMapping
    @mySystemLog(xxbusinessName = "通过ids删除任务")
    public Result deleteTaskByIds(@RequestParam("ids") Long ids) {
        taskService.deleteTaskById(ids);
        return Result.ok("删除成功");
    }


    @GetMapping("/{id}")
    @mySystemLog(xxbusinessName = "查询任务详情")
    public Result getTaskDetail(@PathVariable Long id) {
        return Result.ok(taskService.getTaskById(id));
    }

    /**
     * 老师查询自己发布的任务、各班级的完成情况（每个任务下列出各班级完成人数与完成率）
     * 兼容路径：
     * - 新：GET /counselor/tasks/class-completion
     * - 旧：GET /counselorTask/class-completion
     */
    @GetMapping("/class-completion")
    @mySystemLog(xxbusinessName = "查看辅导员发布任务各班级完成情况")
    public Result getClassTaskCompletion() {
        return Result.ok(counselorTaskService.getClassTask());
    }

    /**
     * 查看辅导员发布的任务状态统计
     * 兼容路径：
     * - 新：POST /counselor/tasks/task-status
     * - 旧：POST /counselorTask/task-status
     */
    @PostMapping("/task-status")
    @mySystemLog(xxbusinessName = "查看辅导员发布的任务状态统计")
    public Result getTaskStatus() {
        return Result.ok(counselorTaskService.getTaskStatus());
    }
}
