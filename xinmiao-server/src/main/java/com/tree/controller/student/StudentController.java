package com.tree.controller.student;

import com.tree.annotation.mySystemLog;
import com.tree.dto.LoginDto;
import com.tree.dto.RefreshTokenDto;
import com.tree.dto.RegisterDto;
import com.tree.result.Result;
import com.tree.service.StudentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;


/**
 * 学生接口
 */
@RestController
@RequestMapping("student")
@Tag(name = "学生接口")
public class StudentController {
    @Resource
    private StudentService studentService;

    @PostMapping("register")
    @mySystemLog(xxbusinessName = "注册用户")
    public Result register(HttpServletRequest request, @Valid @RequestBody RegisterDto registerDto) {
        return Result.ok(studentService.register(request, registerDto));
    }

    @PostMapping("login")
    @mySystemLog(xxbusinessName = "登录用户")
    public Result login(@Valid @RequestBody LoginDto loginDto) {
        return Result.ok(studentService.login(loginDto));
    }

    @PostMapping("refresh")
    @mySystemLog(xxbusinessName = "刷新Token")
    public Result refresh(@Valid @RequestBody RefreshTokenDto dto) {
        return Result.ok(studentService.refreshToken(dto));
    }

    @PostMapping("logout")
    @mySystemLog(xxbusinessName = "登出用户")
    public Result logout(@RequestBody(required = false) RefreshTokenDto dto) {
        studentService.logout(dto != null ? dto.getRefreshToken() : null);
        return Result.ok("登出成功");
    }
}
