package com.tree.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tree.entity.Clazz;
import com.tree.exception.IllegalRequestParamException;
import com.tree.result.Result;
import com.tree.service.ClassService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 辅导员（仅 isAdmin=true）：班级 CRUD。
 */
@RestController
@RequestMapping("counselor/class")
public class CounselorClassController {

    @Resource
    private ClassService classService;

    @GetMapping
    public Result<List> list(@RequestParam(required = false) Long collegeId) {
        LambdaQueryWrapper<Clazz> wrapper = new LambdaQueryWrapper<Clazz>().orderByAsc(Clazz::getCollegeId).orderByAsc(Clazz::getId);
        if (collegeId != null) {
            wrapper.eq(Clazz::getCollegeId, collegeId);
        }
        List<Clazz> list = classService.list(wrapper);
        return Result.ok(list);
    }

    @PostMapping
    public Result add(@Validated @RequestBody Clazz clazz) {
        classService.save(clazz);
        return Result.ok("添加成功");
    }

    @PutMapping
    public Result update(@Validated @RequestBody Clazz clazz) {
        classService.updateClass(clazz);
        return Result.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        if (id == null) {
            throw new IllegalRequestParamException("id不能为空");
        }
        classService.removeById(id);
        return Result.ok("删除成功");
    }
}
