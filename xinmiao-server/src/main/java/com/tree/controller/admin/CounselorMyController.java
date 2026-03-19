package com.tree.controller.admin;

import com.tree.annotation.mySystemLog;
import com.tree.context.CounselorHolder;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.result.Result;
import com.tree.service.CounselorTaskService;
import com.tree.service.InformationService;
import com.tree.service.NoticeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 辅导员端「我的发布」接口（规范路径 /counselor/my/*，当前用户由 token 解析）
 */
@RestController
@RequestMapping("counselor/my")
@Tag(name = "辅导员-我的发布")
public class CounselorMyController {

    @Resource
    private InformationService informationService;
    @Resource
    private NoticeService noticeService;
    @Resource
    private CounselorTaskService counselorTaskService;

    /** 规范路径: GET /counselor/my/information */
    @GetMapping("information")
    @mySystemLog(xxbusinessName = "我的资讯")
    public Result myInformation() {
        Long userId = requireCounselorId();
        return Result.ok(informationService.getInformationByUserId(userId));
    }

    /** 规范路径: GET /counselor/my/notices */
    @GetMapping("notices")
    @mySystemLog(xxbusinessName = "我的通知")
    public Result myNotices() {
        Long counselorId = requireCounselorId();
        return Result.ok(noticeService.getNoticeByCounselorId(counselorId));
    }

    /** 规范路径: GET /counselor/my/tasks */
    @GetMapping("tasks")
    @mySystemLog(xxbusinessName = "我的任务")
    public Result myTasks() {
        Long userId = requireCounselorId();
        return Result.ok(counselorTaskService.getTaskByUserId(userId));
    }

    private Long requireCounselorId() {
        if (CounselorHolder.getCounselor() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return CounselorHolder.getCounselor().getId();
    }
}
