package com.tree.controller.admin;

import com.tree.annotation.mySystemLog;
import com.tree.constant.PaginationConstants;
import com.tree.context.CounselorHolder;
import com.tree.dto.AppealQueryDto;
import com.tree.dto.AppealUpdateDto;
import com.tree.result.Result;
import com.tree.service.AppealService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 辅导员端申诉接口（规范路径 /counselor/appeals）
 */
@RestController
@RequestMapping("counselor/appeals")
public class CounselorAppealController {

    @Resource
    private AppealService appealService;

    /** 规范路径: GET /counselor/appeals?page=1&size=10&... */
    @GetMapping
    @mySystemLog(xxbusinessName = "分页查询申诉")
    public Result page(AppealQueryDto queryDTO,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size) {
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, PaginationConstants.MAX_PAGE_SIZE);
        return Result.ok(appealService.page(queryDTO, page == null || page < 1 ? 1 : page, safeSize));
    }

    /** 规范路径: GET /counselor/appeals/{id} */
    @GetMapping("/{id}")
    @mySystemLog(xxbusinessName = "申诉详情")
    public Result getById(@PathVariable Long id) {
        return Result.ok(appealService.getByIdOrThrow(id));
    }

    /** 规范路径: PUT /counselor/appeals/{id}/handle */
    @PutMapping("/{id}/handle")
    @mySystemLog(xxbusinessName = "处理申诉")
    public Result<Boolean> handle(@PathVariable Long id, @Validated @RequestBody AppealUpdateDto dto) {
        dto.setId(id);
        if (CounselorHolder.getCounselor() != null) {
            dto.setHandlerId(CounselorHolder.getCounselor().getId());
        }
        return Result.ok(appealService.handleAppeal(dto));
    }
}
