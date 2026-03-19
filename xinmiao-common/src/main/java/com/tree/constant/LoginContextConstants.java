package com.tree.constant;

/**
 * 运行时常量
 * 登录上下文在 request 中的属性名，用于拦截器间传递已校验用户信息（避免重复查 Redis）。
 */
public final class LoginContextConstants {
    public static final String LOGIN_USER_DTO = "loginUserDto";
    public static final String LOGIN_USER_TYPE = "loginUserType";

    private LoginContextConstants() {}
}
