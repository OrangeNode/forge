package cn.orangenode.forge.framework.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 序列化配置。
 *
 * <p>提供对象缓存使用的 {@code RedisTemplate}：键固定使用字符串序列化，
 * 值使用 Jackson JSON 序列化，缓存内容可读、跨语言可解析，不使用 JDK 序列化。</p>
 *
 * <p>业务缓存优先使用 {@link ForgeRedisTemplate} 的字符串操作；
 * 需要在值中保存结构体时使用本模板，键名仍必须经过前缀拼接。</p>
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * 装配对象值序列化的 Redis 模板。
     *
     * @param connectionFactory Redis 连接工厂
     * @return 键为字符串、值为 JSON 的 Redis 模板
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        log.info("Redis 对象模板已装配：键为字符串，值为 JSON，默认类型信息用于还原缓存对象");
        return template;
    }
}
