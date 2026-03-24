package com.tree.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.dto.LoginDto;
import com.tree.dto.RegisterDto;
import com.tree.entity.Student;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 学生业务：认证与个人信息。Service 返回业务数据或抛 BusinessException，由 Controller 封装 Result。
 */
public interface StudentService extends IService<Student> {

    /** 返回 token、refreshToken、expiresIn */
    Map<String, Object> register(HttpServletRequest request, RegisterDto student);

    /** 返回 token、refreshToken、expiresIn、userInfo */
    Map<String, Object> login(LoginDto loginDto);

    /** 返回 token、refreshToken、expiresIn */
    Map<String, Object> refreshToken(HttpServletRequest request);

    void updateStudent(Student student);

    /** @param refreshTokenFromCookie 可选，传则从 Redis 删除该 refresh 使立即失效 */
    void logout(String refreshTokenFromCookie);
}
