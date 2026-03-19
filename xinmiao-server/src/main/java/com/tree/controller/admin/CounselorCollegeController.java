package com.tree.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tree.entity.College;
import com.tree.exception.IllegalRequestParamException;
import com.tree.result.Result;
import com.tree.service.CollegeService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 辅导员（仅 isAdmin=true）：学院 CRUD。
 */
@RestController
@RequestMapping("counselor/college")
public class CounselorCollegeController {

    @Resource
    private CollegeService collegeService;

    @GetMapping
    public Result list() {
        List<College> list = collegeService.list(new LambdaQueryWrapper<College>().orderByAsc(College::getId));
        return Result.ok(list);
    }

    @PostMapping
    public Result add(@Validated @RequestBody College college) {
        collegeService.save(college);
        return Result.ok("添加成功");
    }

    @PutMapping
    public Result update(@Validated @RequestBody College college) {
        collegeService.updateCollege(college);
        return Result.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        if (id == null) {
            throw new IllegalRequestParamException("id不能为空");
        }
        collegeService.removeById(id);
        return Result.ok("删除成功");
    }
}
