package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.dto.LoginDto;
import com.tree.dto.RegisterDto;
import com.tree.result.Result;
import com.tree.service.StudentService;
import com.tree.util.RefreshTokenCookieHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * 学生接口
 */
@RestController
@RequestMapping("student")
@Tag(name = "学生接口")
public class StudentController {
    @Resource
    private StudentService studentService;
    @Resource
    private RefreshTokenCookieHelper refreshTokenCookieHelper;

    @PostMapping("register")
    @mySystemLog(xxbusinessName = "注册用户")
    public Result register(HttpServletRequest request, HttpServletResponse response, @Valid @RequestBody RegisterDto registerDto) {
        Map<String, Object> result = studentService.register(request, registerDto);
        refreshTokenCookieHelper.write(response, (String) result.remove("refreshToken"));
        return Result.ok(result);
    }

    @PostMapping("login")
    @mySystemLog(xxbusinessName = "登录用户")
    public Result login(HttpServletResponse response, @Valid @RequestBody LoginDto loginDto) {
        Map<String, Object> result = studentService.login(loginDto);
        refreshTokenCookieHelper.write(response, (String) result.remove("refreshToken"));
        return Result.ok(result);
    }

    @PostMapping("refresh")
    @mySystemLog(xxbusinessName = "刷新Token")
    public Result refresh(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> result = studentService.refreshToken(request);
        refreshTokenCookieHelper.write(response, (String) result.remove("refreshToken"));
        return Result.ok(result);
    }

    @PostMapping("logout")
    @mySystemLog(xxbusinessName = "登出用户")
    public Result logout(HttpServletRequest request, HttpServletResponse response) {
        studentService.logout(refreshTokenCookieHelper.read(request));
        refreshTokenCookieHelper.clear(response);
        return Result.ok("登出成功");
    }
}
