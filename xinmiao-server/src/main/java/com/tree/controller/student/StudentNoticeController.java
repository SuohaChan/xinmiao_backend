package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.context.StudentHolder;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.result.Result;
import com.tree.service.NoticeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生端通知接口（规范路径 /student/notices）
 */
@RestController
@RequestMapping("student/notices")
@Tag(name = "学生-通知")
public class StudentNoticeController {

    @Resource
    private NoticeService noticeService;

    /** 优化: GET /student/notices/my 使用当前登录学生上下文 */
    @GetMapping("/my")
    @mySystemLog(xxbusinessName = "当前用户通知列表")
    public Result getMyNotices() {
        Long userId = requireStudentId();
        List<?> list = noticeService.getNoticeByUserId(userId);
        return Result.ok(list);
    }

    /** 规范路径: GET /student/notices/{id} */
    @GetMapping("/{id}")
    @mySystemLog(xxbusinessName = "查询单个通知")
    public Result getById(@PathVariable Long id) {
        return Result.ok(noticeService.getNoticeById(id));
    }

    private Long requireStudentId() {
        if (StudentHolder.getStudent() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return StudentHolder.getStudent().getId();
    }
}
