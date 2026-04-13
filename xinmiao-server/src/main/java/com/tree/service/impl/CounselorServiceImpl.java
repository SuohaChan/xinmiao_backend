package com.tree.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.constant.RedisConstants;
import com.tree.exception.BusinessException;
import com.tree.mapper.CounselorMapper;
import com.tree.service.CounselorService;
import com.tree.util.JwtUtils;
import com.tree.utils.LoginUserUtils;
import com.tree.util.TokenExtractUtils;
import com.tree.dto.CounselorDto;
import com.tree.dto.CounselorShowDto;
import com.tree.dto.LoginDto;
import com.tree.entity.Counselor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tree.result.ErrorCode;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ljx
 * @description 针对表【tb_counselor(辅导员)】的数据库操作Service实现
 * @createDate 2024-02-21 20:21:31
 */
@Slf4j
@Service
public class CounselorServiceImpl extends ServiceImpl<CounselorMapper, Counselor>
        implements CounselorService {

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    public CounselorServiceImpl(StringRedisTemplate stringRedisTemplate,
            JwtUtils jwtUtils,
            PasswordEncoder passwordEncoder) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Map<String, Object> login(LoginDto loginDto) {
        String username = loginDto.getUsername();
        String rawPassword = loginDto.getPassword();
        Counselor cs = lambdaQuery().eq(Counselor::getUsername, username).one();
        if (cs == null || !passwordEncoder.matches(rawPassword, cs.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "用户名或密码错误");
        }

        Boolean isAdmin = cs.getIsAdmin() != null && cs.getIsAdmin() == 1;
        CounselorDto dto = BeanUtil.copyProperties(cs, CounselorDto.class);
        dto.setIsAdmin(isAdmin);
        String refreshToken = UUID.fastUUID().toString(true);
        Map<String, Object> userMap = BeanUtil.beanToMap(dto);
        userMap.put("type", "Counselor");
        long accessTtlMs = RedisConstants.ACCESS_TOKEN_TTL.toMillis();
        String accessToken = jwtUtils.generateAccessToken(cs.getId(), "Counselor", accessTtlMs, isAdmin);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.REFRESH_TOKEN_KEY + refreshToken, userMap);
        stringRedisTemplate.expire(RedisConstants.REFRESH_TOKEN_KEY + refreshToken, RedisConstants.REFRESH_TOKEN_TTL);

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", RedisConstants.ACCESS_TOKEN_TTL.getSeconds());
        CounselorShowDto showDto = BeanUtil.copyProperties(cs, CounselorShowDto.class);
        showDto.setIsAdmin(isAdmin);
        result.put("userInfo", showDto);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> register(HttpServletRequest request, Counselor counselor) {
        // === 1. 参数校验与初始化 ===
        // 若用户名为空，尝试从session获取（如身份证/工号）
        String username = counselor.getUsername();
        if (StringUtils.isEmpty(username)) {
            Object idNumber = request.getSession().getAttribute("idNumber");
            counselor.setUsername((String) idNumber);
            username = counselor.getUsername();
        }
        // 校验用户名唯一性
        if (lambdaQuery().eq(Counselor::getUsername, username).exists()) {
            throw new BusinessException(ErrorCode.DUPLICATE, "用户名已存在，请更换用户名");
        }

        // === 2. 密码 BCrypt 哈希（防撞库、带盐） ===
        counselor.setPassword(passwordEncoder.encode(counselor.getPassword()));

        // === 3. 保存辅导员基本信息 ===
        save(counselor);

        Boolean isAdmin = counselor.getIsAdmin() != null && counselor.getIsAdmin() == 1;
        CounselorDto dto = BeanUtil.copyProperties(counselor, CounselorDto.class);
        dto.setIsAdmin(isAdmin);
        String refreshToken = UUID.fastUUID().toString(true);
        Map<String, Object> userMap = BeanUtil.beanToMap(dto);
        userMap.put("type", "Counselor");
        long accessTtlMs = RedisConstants.ACCESS_TOKEN_TTL.toMillis();
        String accessToken = jwtUtils.generateAccessToken(counselor.getId(), "Counselor", accessTtlMs, isAdmin);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.REFRESH_TOKEN_KEY + refreshToken, userMap);
        stringRedisTemplate.expire(RedisConstants.REFRESH_TOKEN_KEY + refreshToken, RedisConstants.REFRESH_TOKEN_TTL);

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", RedisConstants.ACCESS_TOKEN_TTL.getSeconds());
        return result;
    }

    @Override
    public Map<String, Object> refreshToken(HttpServletRequest request) {
        String rt = TokenExtractUtils.getCookieValue(request, "refreshToken");
        if (rt == null || rt.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "Cookie 中 refreshToken 缺失");
        }
        String refreshKey = RedisConstants.REFRESH_TOKEN_KEY + rt;
        Map<Object, Object> userMap;
        try {
            userMap = stringRedisTemplate.opsForHash().entries(refreshKey);
        } catch (Exception e) {
            log.warn("Redis unavailable during refreshToken, fail with 503. refreshKey={}", refreshKey, e);
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
        }
        if (userMap == null || userMap.isEmpty()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "refreshToken 无效或已过期");
        }
        if (!"Counselor".equalsIgnoreCase(String.valueOf(userMap.get("type")))) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "refreshToken 类型不匹配");
        }

        try {
            stringRedisTemplate.delete(refreshKey);
        } catch (Exception e) {
            log.warn("Redis unavailable during refreshToken cleanup, fail with 503. refreshKey={}", refreshKey, e);
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
        }

        String idStr = String.valueOf(userMap.get("id"));
        Long userId = Long.parseLong(idStr.replace("\"", "").trim());
        Object isAdminObj = userMap.get("isAdmin");
        Boolean isAdmin = isAdminObj != null
                && (Boolean.TRUE.equals(isAdminObj) || "true".equalsIgnoreCase(String.valueOf(isAdminObj)));
        long accessTtlMs = RedisConstants.ACCESS_TOKEN_TTL.toMillis();
        String newAccessToken = jwtUtils.generateAccessToken(userId, "Counselor", accessTtlMs, isAdmin);
        String newRefreshToken = UUID.fastUUID().toString(true);
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<Object, Object> e : userMap.entrySet()) {
            map.put(String.valueOf(e.getKey()), e.getValue());
        }
        try {
            stringRedisTemplate.opsForHash().putAll(RedisConstants.REFRESH_TOKEN_KEY + newRefreshToken, map);
            stringRedisTemplate.expire(RedisConstants.REFRESH_TOKEN_KEY + newRefreshToken,
                    RedisConstants.REFRESH_TOKEN_TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable during refreshToken rotate, fail with 503. userId={}", userId, e);
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("token", newAccessToken);
        result.put("refreshToken", newRefreshToken);
        result.put("expiresIn", RedisConstants.ACCESS_TOKEN_TTL.getSeconds());
        return result;
    }

    @Override
    public void logout(String refreshTokenFromCookie) {
        String accessToken = LoginUserUtils.getCurrentToken();
        if (accessToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未找到登录信息");
        }
        jwtUtils.blacklist(accessToken);
        if (refreshTokenFromCookie != null && !refreshTokenFromCookie.isBlank()) {
            try {
                stringRedisTemplate.delete(RedisConstants.REFRESH_TOKEN_KEY + refreshTokenFromCookie.trim());
            } catch (Exception e) {
                log.warn("Redis unavailable during logout refreshToken delete, fail with 503.", e);
                throw new BusinessException(ErrorCode.SERVICE_BUSY, "服务繁忙，请稍后重试");
            }
        }
        log.info("辅导员登出成功，JWT 已加入黑名单");
    }

}
