package com.tree.controller.admin;

import com.tree.annotation.mySystemLog;
import com.tree.constant.PaginationConstants;
import com.tree.dto.AddNoticeDto;
import com.tree.dto.NoticeQueryDto;
import com.tree.result.Result;
import com.tree.service.NoticeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 辅导员端通知接口（规范路径 /counselor/notices）
 */
@RestController
@RequestMapping("counselor/notices")
@Tag(name = "辅导员-通知")
public class CounselorNoticeController {

    @Resource
    private NoticeService noticeService;

    /** 规范路径: GET /counselor/notices?page=1&size=10&... */
    @GetMapping
    @mySystemLog(xxbusinessName = "分页查询通知")
    public Result page(NoticeQueryDto queryDTO,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size) {
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, PaginationConstants.MAX_PAGE_SIZE);
        return Result.ok(noticeService.page(queryDTO, page == null || page < 1 ? 1 : page, safeSize));
    }

    /** 规范路径: GET /counselor/notices/{id} */
    @GetMapping("/{id}")
    @mySystemLog(xxbusinessName = "查询单个通知")
    public Result getById(@PathVariable Long id) {
        return Result.ok(noticeService.getNoticeById(id));
    }

    @PostMapping
    @mySystemLog(xxbusinessName = "添加通知")
    public Result add(@Validated @ModelAttribute AddNoticeDto addNoticeDTO,
                      @RequestParam(value = "images", required = false) MultipartFile[] images) {
        noticeService.addNotice(addNoticeDTO, images);
        return Result.ok("通知创建成功");
    }

    @DeleteMapping("/{id}")
    public Result deleteById(@PathVariable Long id) {
        noticeService.deleteNoticeById(id);
        return Result.ok("通知删除成功");
    }

    @DeleteMapping
    public Result deleteByIds(@RequestParam("ids") Long id) {
        noticeService.deleteNoticeById(id);
        return Result.ok("通知删除成功");
    }
}
