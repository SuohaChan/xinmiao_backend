package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.context.StudentHolder;
import com.tree.dto.AppealSubmitDto;
import com.tree.entity.Appeal;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.result.Result;
import com.tree.service.AppealService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生端申诉接口（规范路径 /student/appeals）
 */
@RestController
@RequestMapping("student/appeals")
@Tag(name = "学生-申诉")
public class StudentAppealController {

    @Resource
    private AppealService appealService;

    @PostMapping
    @mySystemLog(xxbusinessName = "提交申诉")
    public Result submit(@Validated @RequestBody AppealSubmitDto dto) {
        return Result.ok(appealService.submitAppeal(dto));
    }

    @GetMapping("/{appealId}")
    @mySystemLog(xxbusinessName = "申诉详情")
    public Result getById(@PathVariable Long appealId) {
        Long studentId = requireStudentId();
        return Result.ok(appealService.getByIdForStudent(appealId, studentId));
    }

    @GetMapping("/my")
    @mySystemLog(xxbusinessName = "当前用户申诉列表")
    public Result getMyAppeals() {
        Long userId = requireStudentId();
        List<Appeal> list = appealService.getUserAppeals(userId);
        return Result.ok(list);
    }

    private Long requireStudentId() {
        if (StudentHolder.getStudent() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return StudentHolder.getStudent().getId();
    }
}
