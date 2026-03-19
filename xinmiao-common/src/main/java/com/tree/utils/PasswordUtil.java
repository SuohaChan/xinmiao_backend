package com.tree.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Scanner;

/**
 * 密码哈希工具：生成 BCrypt 哈希，与后端登录/注册一致。
 * 用于手动往数据库插入加密后的密码（如初始化管理员）。
 */
public class PasswordUtil {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 使用与项目中相同的 BCrypt 方式加密密码
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== 密码 BCrypt 哈希工具（与后端一致） ===");
        System.out.print("请输入要加密的密码: ");
        String rawPassword = scanner.nextLine();
        String encoded = encode(rawPassword);
        System.out.println("\n原始密码: " + rawPassword);
        System.out.println("BCrypt 哈希: " + encoded);
        scanner.close();
    }
}
