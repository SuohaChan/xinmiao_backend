package com.tree.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.dto.LoginDto;
import com.tree.dto.RefreshTokenDto;
import com.tree.entity.Counselor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 辅导员业务：认证。Service 返回业务数据或抛 BusinessException，由 Controller 封装 Result。
 */
public interface CounselorService extends IService<Counselor> {

    Map<String, Object> register(HttpServletRequest request, Counselor counselor);

    Map<String, Object> login(LoginDto loginDto);

    Map<String, Object> refreshToken(RefreshTokenDto dto);

    void logout(String refreshTokenFromBody);
}
