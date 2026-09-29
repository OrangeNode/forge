package cn.orangenode.forge.framework.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.config.ForgeRedisProperties;

/**
 * 项目级 Redis 操作入口。
 *
 * <p>统一负责键前缀与存活时间：调用方只提交逻辑键名，不再各自拼接前缀，
 * 避免出现缺少项目或环境前缀的键在共享实例中互相覆盖。</p>
 *
 * <p>不在此处做异常降级：Redis 不可用时异常向上抛出，由全局异常处理器统一返回
 * HTTP 200 + body.code=503，调用方不得改为“跳过校验”继续执行。</p>
 */
@Component
public class ForgeRedisTemplate {

    /**
     * 统一前缀的字符串模板，序列化方式为 UTF-8 字符串。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Redis 键与缓存配置。
     */
    private final ForgeRedisProperties properties;

    /**
     * 构造项目级 Redis 操作入口。
     *
     * @param stringRedisTemplate 字符串 Redis 模板
     * @param properties          Redis 键与缓存配置
     */
    public ForgeRedisTemplate(StringRedisTemplate stringRedisTemplate, ForgeRedisProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    /**
     * 拼接带项目与环境前缀的完整键名。
     *
     * @param key 逻辑键名，例如 {@code auth:token:{摘要}} 中的模块局部片段
     * @return 完整 Redis 键名
     */
    public String key(String key) {
        return properties.getKeyPrefix() + ":" + key;
    }

    /**
     * 以默认存活时间写入字符串缓存。
     *
     * @param key   逻辑键名
     * @param value 缓存内容
     */
    public void set(String key, String value) {
        set(key, value, Duration.ofSeconds(properties.getDefaultTtlSeconds()));
    }

    /**
     * 以指定存活时间写入字符串缓存。
     *
     * @param key   逻辑键名
     * @param value 缓存内容
     * @param ttl   存活时间，必须为正数
     */
    public void set(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("缓存存活时间必须为正数");
        }
        stringRedisTemplate.opsForValue().set(key(key), value, ttl);
    }

    /**
     * 读取字符串缓存。
     *
     * @param key 逻辑键名
     * @return 缓存内容，不存在时为 {@code null}
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key(key));
    }

    /**
     * 判断键是否存在。
     *
     * @param key 逻辑键名
     * @return 存在返回 {@code true}
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(key)));
    }

    /**
     * 读取键的剩余存活秒数。
     *
     * @param key 逻辑键名
     * @return 剩余秒数；键不存在或未设置过期时间时返回 {@code null}
     */
    public Long getExpireSeconds(String key) {
        return stringRedisTemplate.getExpire(key(key));
    }

    /**
     * 删除单个键。
     *
     * @param key 逻辑键名
     * @return 实际删除的键数量
     */
    public long delete(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key(key))) ? 1L : 0L;
    }

    /**
     * 按逻辑键名批量删除。
     *
     * <p>用于按业务标识撤销一批会话或缓存，调用方必须给出明确的键集合，
     * 不使用 {@code keys} 通配扫描，避免阻塞 Redis。</p>
     *
     * @param keys 逻辑键名集合
     * @return 实际删除的键数量
     */
    public long delete(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        List<String> fullKeys = keys.stream().map(this::key).toList();
        Long deleted = stringRedisTemplate.delete(fullKeys);
        return deleted == null ? 0L : deleted;
    }

    /**
     * 取得当前配置的键前缀，供诊断与测试断言使用。
     *
     * @return 键前缀
     */
    public String getKeyPrefix() {
        return properties.getKeyPrefix();
    }
}
