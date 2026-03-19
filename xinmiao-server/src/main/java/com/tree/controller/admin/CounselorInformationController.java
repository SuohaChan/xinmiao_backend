package com.tree.controller.admin;

import com.tree.annotation.mySystemLog;
import com.tree.constant.PaginationConstants;
import com.tree.dto.AddInformationDto;
import com.tree.dto.InformationQueryDto;
import com.tree.entity.Information;
import com.tree.result.Result;
import com.tree.service.InformationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 辅导员端资讯接口（规范路径 /counselor/information）
 */
@RestController
@RequestMapping("counselor/information")
@Tag(name = "辅导员-资讯")
public class CounselorInformationController {

    @Resource
    private InformationService informationService;

    /** 规范路径: GET /counselor/information?page=1&size=10&... */
    @GetMapping
    @mySystemLog(xxbusinessName = "分页查询资讯")
    public Result page(InformationQueryDto queryDTO,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size) {
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, PaginationConstants.MAX_PAGE_SIZE);
        return Result.ok(informationService.page(queryDTO, page == null || page < 1 ? 1 : page, safeSize));
    }

    /** 规范路径: GET /counselor/information/{id} */
    @GetMapping("/{id}")
    @mySystemLog(xxbusinessName = "查询单个资讯")
    public Result getById(@PathVariable Long id) {
        return Result.ok(informationService.getInformationById(id));
    }

    @PostMapping
    @mySystemLog(xxbusinessName = "添加资讯")
    public Result add(@Validated @ModelAttribute AddInformationDto dto,
                      @RequestParam(value = "images", required = false) MultipartFile[] images) {
        informationService.addInformation(dto, images);
        return Result.ok("资讯创建成功");
    }

    @PutMapping
    public Result update(@Validated @RequestBody Information information) {
        informationService.updateInformation(information);
        return Result.ok("资讯更新成功");
    }

    @DeleteMapping("/{id}")
    public Result deleteById(@PathVariable Long id) {
        informationService.deleteInformationById(id);
        return Result.ok("资讯删除成功");
    }

    @DeleteMapping
    public Result deleteByIds(@RequestParam("ids") Long id) {
        informationService.deleteInformationById(id);
        return Result.ok("资讯删除成功");
    }
}
