package com.tree.controller.admin;

import com.tree.annotation.mySystemLog;
import com.tree.result.Result;
import com.tree.service.CounselorService;
import com.tree.util.RefreshTokenCookieHelper;
import com.tree.dto.LoginDto;
import com.tree.entity.Counselor;
import com.tree.validation.RegisterGroup;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;


/**
 * 辅导员接口
 */
@RestController
@RequestMapping("counselor")
public class CounselorController {
    @Resource
    private CounselorService counselorService;
    @Resource
    private RefreshTokenCookieHelper refreshTokenCookieHelper;

    /**
     * 辅导员登录
     * @return Result对象
     */
    @PostMapping("login")
    @mySystemLog(xxbusinessName = "登录辅导员")
    public Result login(HttpServletResponse response, @Valid @RequestBody LoginDto loginDto) {
        Map<String, Object> result = counselorService.login(loginDto);
        refreshTokenCookieHelper.write(response, (String) result.remove("refreshToken"));
        return Result.ok(result);
    }

    @PostMapping("refresh")
    @mySystemLog(xxbusinessName = "刷新Token")
    public Result refresh(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> result = counselorService.refreshToken(request);
        refreshTokenCookieHelper.write(response, (String) result.remove("refreshToken"));
        return Result.ok(result);
    }

    @PostMapping("register")
    @mySystemLog(xxbusinessName = "注册辅导员")
    public Result register(HttpServletRequest request, @Validated(RegisterGroup.class) @RequestBody Counselor counselor) {
        return Result.ok(counselorService.register(request, counselor));
    }

    @PostMapping("logout")
    @mySystemLog(xxbusinessName = "登出辅导员")
    public Result logout(HttpServletRequest request, HttpServletResponse response) {
        counselorService.logout(refreshTokenCookieHelper.read(request));
        refreshTokenCookieHelper.clear(response);
        return Result.ok("登出成功");
    }
}
