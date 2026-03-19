package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.context.StudentHolder;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.result.Result;
import com.tree.service.InformationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生端资讯接口（规范路径 /student/informations）
 */
@RestController
@RequestMapping("student/informations")
@Tag(name = "学生-资讯")
public class StudentInformationController {

    @Resource
    private InformationService informationService;

    @GetMapping("/my")
    @mySystemLog(xxbusinessName = "当前用户资讯列表")
    public Result getMyInformations() {
        Long userId = requireStudentId();
        List<?> list = informationService.getInformationByUserId(userId);
        return Result.ok(list);
    }

    @GetMapping("/{id}")
    @mySystemLog(xxbusinessName = "查询单个资讯")
    public Result getById(@PathVariable Long id) {
        return Result.ok(informationService.getInformationById(id));
    }

    private Long requireStudentId() {
        if (StudentHolder.getStudent() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return StudentHolder.getStudent().getId();
    }
}
