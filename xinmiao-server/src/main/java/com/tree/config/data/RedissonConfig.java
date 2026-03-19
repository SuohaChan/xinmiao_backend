package com.tree.config.data;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置，复用 spring.data.redis 的地址，用于分布式锁（含看门狗）。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;
    @Value("${spring.data.redis.port:6379}")
    private int port;
    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        if (password == null || password.isEmpty()) {
            config.useSingleServer().setAddress(address);
        } else {
            config.useSingleServer().setAddress(address).setPassword(password);
        }
        return Redisson.create(config);
    }
}
