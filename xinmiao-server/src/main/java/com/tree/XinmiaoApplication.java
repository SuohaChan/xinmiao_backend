package com.tree;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.tree.mapper")
@EnableScheduling
public class XinmiaoApplication {
    public static void main(String[] args) {
        SpringApplication.run(XinmiaoApplication.class, args);
    }
}

