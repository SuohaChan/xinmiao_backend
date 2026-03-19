package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.context.StudentHolder;
import com.tree.entity.StudentInfo;
import com.tree.exception.BusinessException;
import com.tree.result.ErrorCode;
import com.tree.result.Result;
import com.tree.service.StudentInfoService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.tree.validation.ValidateGroup;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 学生信息接口
 */
@RestController
@RequestMapping("student")
@Slf4j
public class StudentInfoController {
    @Resource
    private StudentInfoService studentInfoService;

    /**
     * 进行注册前身份验证
     *
     * @param studentInfo 包含字段idNumber和admissionNumber
     * @return Result对象
     */
    @PostMapping("/validate")
    @mySystemLog(xxbusinessName = "身份验证")
    public Result validate(HttpServletRequest request, @Validated(ValidateGroup.class) @RequestBody StudentInfo studentInfo) {
        studentInfoService.validate(request, studentInfo);
        return Result.ok();
    }
    //身份验证的一环旧接口未实现
    @PostMapping("/face")
    @mySystemLog(xxbusinessName = "人脸识别")
    public Result checkFace(HttpServletRequest request, MultipartFile faceFile) throws IOException {
        return Result.ok(studentInfoService.checkFace(request, faceFile));
    }

    /**
     * 更新当前登录学生的信息（仅允许改本人，请求体中的 id 会被忽略）。
     */
    @PutMapping("/updateInfo")
    @mySystemLog(xxbusinessName = "更新学生信息")
    public Result updateStudentInfo(@Validated @RequestBody StudentInfo studentInfo) {
        Long studentId = requireStudentId();
        studentInfoService.updateCurrentStudentInfo(studentId, studentInfo);
        return Result.ok();
    }

    /** 规范路径: GET /student/info（通过 token 获取当前学生信息） */
    @GetMapping("/info")
    @mySystemLog(xxbusinessName = "获取学生信息")
    public Result getStudentInfo() {
        Long studentId = requireStudentId();
        return Result.ok(studentInfoService.getCurrentStudentInfo(studentId));
    }

    @GetMapping("/getByToken")
    @mySystemLog(xxbusinessName = "通过token获取学生信息（兼容）")
    public Result getStudentInfoByToken() {
        return getStudentInfo();
    }

    @PutMapping("/updateByToken")
    @mySystemLog(xxbusinessName = "通过token修改学生信息")
    public Result updateStudentInfoByToken(@Validated @RequestBody StudentInfo studentInfo) {
        Long studentId = requireStudentId();
        studentInfoService.updateCurrentStudentInfo(studentId, studentInfo);
        return Result.ok();
    }

    private Long requireStudentId() {
        if (StudentHolder.getStudent() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return StudentHolder.getStudent().getId();
    }
}


