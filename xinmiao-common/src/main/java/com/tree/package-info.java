/**
 * 项目公共模块。
 * <p>
 * <b>工具与序列化约定：</b>
 * <ul>
 *   <li><b>对象拷贝</b>：使用 Hutool {@code cn.hutool.core.bean.BeanUtil}（如 copyProperties、beanToMap），
 *       用于 DTO/Entity 之间的属性复制，不用于 JSON 或 Redis 序列化。</li>
 *   <li><b>序列化（对象 ↔ 字符串/字节）</b>：统一使用 Jackson。HTTP 请求/响应 JSON 使用
 *       {@link com.tree.jackson.JacksonObjectMapper}；Redis 的 value/hashValue 使用
 *       {@link org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer}。</li>
 * </ul>
 * </p>
 */
package com.tree;
