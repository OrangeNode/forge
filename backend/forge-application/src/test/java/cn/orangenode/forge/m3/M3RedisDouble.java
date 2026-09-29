package cn.orangenode.forge.m3;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 内存版 Redis 替身。
 *
 * <p>把 {@link StringRedisTemplate} 的字符串读写、计数与删除替换为进程内映射，
 * 用于验证令牌会话、会话版本撤销与登录失败限流的真实交互过程，
 * 不需要启动外部 Redis。TTL 只记录不生效，测试通过读取记录值断言写入时带上了存活时间。</p>
 *
 * <p>需要验证依赖故障时调用 {@link #failOnRead()}：之后的读取按 Redis 连接失败抛出，
 * 用于确认受保护请求返回 503 而不是被放行。</p>
 */
final class M3RedisDouble {

    /**
     * 字符串键值存储。
     */
    private final Map<String, String> values = new ConcurrentHashMap<>();

    /**
     * 各键最近一次写入时使用的存活时间。
     */
    private final Map<String, Duration> ttlByKey = new ConcurrentHashMap<>();

    /**
     * 被替身的字符串模板。
     */
    private final StringRedisTemplate template;

    /**
     * 是否模拟 Redis 连接失败。
     */
    private boolean readFailure;

    /**
     * 安装替身行为。
     *
     * @param template 上下文中的字符串模板替身
     */
    M3RedisDouble(StringRedisTemplate template) {
        this.template = template;
        install();
    }

    /**
     * 清空全部键、存活时间与故障开关，供每个用例开始前重置。
     */
    void clear() {
        values.clear();
        ttlByKey.clear();
        readFailure = false;
    }

    /**
     * 让后续读取按连接失败抛出，用于验证依赖故障语义。
     */
    void failOnRead() {
        readFailure = true;
    }

    /**
     * 读取键当前的值。
     *
     * @param fullKey 含前缀的完整键名
     * @return 键值，不存在时为 {@code null}
     */
    String value(String fullKey) {
        return values.get(fullKey);
    }

    /**
     * 读取键最近一次写入时使用的存活时间。
     *
     * @param fullKey 含前缀的完整键名
     * @return 存活时间，未写入过时为 {@code null}
     */
    Duration ttl(String fullKey) {
        return ttlByKey.get(fullKey);
    }

    /**
     * 返回当前保存的全部键名，供断言键前缀与摘要存储使用。
     *
     * @return 键名集合
     */
    java.util.Set<String> keys() {
        return java.util.Set.copyOf(values.keySet());
    }

    /**
     * 安装字符串读写、计数与删除行为。
     */
    @SuppressWarnings("unchecked")
    private void install() {
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(operations);
        when(operations.get(anyString())).thenAnswer(invocation -> {
            if (readFailure) {
                throw new RedisConnectionFailureException("redis down");
            }
            return values.get(invocation.getArgument(0, String.class));
        });
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            ttlByKey.put(invocation.getArgument(0, String.class), invocation.getArgument(2, Duration.class));
            return null;
        }).when(operations).set(anyString(), anyString(), any(Duration.class));
        when(operations.increment(anyString())).thenAnswer(invocation -> {
            if (readFailure) {
                throw new RedisConnectionFailureException("redis down");
            }
            String key = invocation.getArgument(0, String.class);
            long next = Long.parseLong(values.getOrDefault(key, "0")) + 1;
            values.put(key, Long.toString(next));
            return next;
        });
        when(template.expire(anyString(), any(Duration.class))).thenAnswer(invocation -> {
            ttlByKey.put(invocation.getArgument(0, String.class), invocation.getArgument(1, Duration.class));
            return true;
        });
        when(template.delete(anyString())).thenAnswer(invocation ->
                values.remove(invocation.getArgument(0, String.class)) != null);
    }
}
