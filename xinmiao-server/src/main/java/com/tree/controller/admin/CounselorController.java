package com.tree.controller.admin;

import com.tree.annotation.mySystemLog;
import com.tree.result.Result;
import com.tree.service.CounselorService;
import com.tree.dto.LoginDto;
import com.tree.dto.RefreshTokenDto;
import com.tree.entity.Counselor;
import com.tree.validation.RegisterGroup;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 辅导员接口
 */
@RestController
@RequestMapping("counselor")
public class CounselorController {
    @Resource
    private CounselorService counselorService;

    /**
     * 辅导员登录
     * @return Result对象
     */
    @PostMapping("login")
    @mySystemLog(xxbusinessName = "登录辅导员")
    public Result login(@Valid @RequestBody LoginDto loginDto) {
        return Result.ok(counselorService.login(loginDto));
    }

    @PostMapping("refresh")
    @mySystemLog(xxbusinessName = "刷新Token")
    public Result refresh(@Valid @RequestBody RefreshTokenDto dto) {
        return Result.ok(counselorService.refreshToken(dto));
    }

    @PostMapping("register")
    @mySystemLog(xxbusinessName = "注册辅导员")
    public Result register(HttpServletRequest request, @Validated(RegisterGroup.class) @RequestBody Counselor counselor) {
        return Result.ok(counselorService.register(request, counselor));
    }

    @PostMapping("logout")
    @mySystemLog(xxbusinessName = "登出辅导员")
    public Result logout(@RequestBody(required = false) RefreshTokenDto dto) {
        counselorService.logout(dto != null ? dto.getRefreshToken() : null);
        return Result.ok("登出成功");
    }
}
