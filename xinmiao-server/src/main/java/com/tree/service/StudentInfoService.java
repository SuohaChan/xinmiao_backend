package com.tree.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.dto.StudentInfoShowDto;
import com.tree.entity.StudentInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface StudentInfoService extends IService<StudentInfo> {

    void validate(HttpServletRequest request, StudentInfo studentInfo);

    Boolean checkFace(HttpServletRequest request, MultipartFile faceFile) throws IOException;

    /** 按 id 查询，不存在则抛 NOT_FOUND */
    StudentInfo getByIdOrThrow(Long id);

    /** 获取当前学生信息（含学院/班级 ID），不存在则抛 NOT_FOUND */
    StudentInfoShowDto getCurrentStudentInfo(Long studentId);

    /** 按 body.id 更新学生信息，不存在则抛 NOT_FOUND */
    void updateStudentInfo(StudentInfo studentInfo);

    /** 以当前学生 id 更新本人信息，不存在则抛 NOT_FOUND */
    void updateCurrentStudentInfo(Long studentId, StudentInfo studentInfo);
}
