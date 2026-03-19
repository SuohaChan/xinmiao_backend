package com.tree.config.data;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

/**
 * Redis 配置。
 * <p>
 * 项目约定：Redis 的 value / hashValue 使用 <b>Jackson</b> 序列化（{@link GenericJackson2JsonRedisSerializer}），
 * 以便存储对象（如 Refresh Token 会话中的用户信息 Map）并能正确反序列化回原类型。
 * Key 仍使用默认的 String 序列化。
 * </p>
 * <p>
 * 执行 Lua 脚本时，传参使用 <b>纯字符串</b> 序列化，否则 Lua 内 {@code tonumber(ARGV[i])} 会得到 nil。
 * 故提供 {@code scriptRedisTemplate} 专用于脚本执行（限流等），不覆盖 value 序列化器。
 * </p>
 *
 * @see com.tree.constant.RedisConstants
 */
@Configuration
public class RedisConfig {

    /**
     * 自定义 StringRedisTemplate：value、hashValue 使用 Jackson 序列化，便于存取对象。
     * 标记为 @Primary，未显式指定 @Qualifier 的注入点（如 RedisChatMemoryRepository）使用本 Bean。
     */
    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(redisConnectionFactory);

        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }

    /**
     * 专用于执行 Lua 脚本的 RedisTemplate，key/value 均为 String 序列化。
     * 供限流等需要将 ARGV 以纯字符串传入 Lua 的场景使用，避免 Jackson 序列化导致 tonumber(ARGV[i]) 为 nil。
     */
    @Bean("scriptRedisTemplate")
    public StringRedisTemplate scriptRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }
}
