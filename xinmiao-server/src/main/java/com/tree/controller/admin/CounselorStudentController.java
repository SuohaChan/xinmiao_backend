package com.tree.controller.admin;

import com.tree.annotation.mySystemLog;
import com.tree.entity.Student;
import com.tree.result.Result;
import com.tree.service.StudentService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 辅导员（仅 isAdmin=true）：学生基础信息管理。
 * 路径前缀：/counselor/students
 */
@RestController
@RequestMapping("counselor/students")
public class CounselorStudentController {

    @Resource
    private StudentService studentService;

    /**
     * 列出所有学生（后续如需可加分页条件）。
     */
    @GetMapping
    @mySystemLog(xxbusinessName = "管理员-学生列表")
    public Result listStudents() {
        return Result.ok(studentService.list());
    }

    /**
     * 更新学生信息。
     */
    @PutMapping
    @mySystemLog(xxbusinessName = "管理员-修改学生信息")
    public Result updateStudent(@Validated @RequestBody Student student) {
        studentService.updateStudent(student);
        return Result.ok();
    }
}

