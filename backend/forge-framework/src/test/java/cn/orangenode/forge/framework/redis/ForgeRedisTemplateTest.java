package cn.orangenode.forge.framework.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import cn.orangenode.forge.framework.config.ForgeRedisProperties;

/**
 * Redis 封装验证。
 *
 * <p>验证逻辑键名统一拼接项目与环境前缀、写入必须带存活时间、批量删除按前缀展开，
 * 以及连接失败时异常向上抛出而不是被降级为成功。</p>
 *
 * <p>使用替身验证键名与 TTL 参数，真实 Redis 读写由带环境变量开关的探针用例验证。</p>
 */
@ExtendWith(MockitoExtension.class)
class ForgeRedisTemplateTest {

    /**
     * Redis 字符串模板替身。
     */
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 字符串值操作替身。
     */
    @Mock
    private ValueOperations<String, String> valueOperations;

    /**
     * 被测的 Redis 封装。
     */
    private ForgeRedisTemplate forgeRedisTemplate;

    /**
     * 每个用例前装配被测对象，使用与配置文件不同的前缀以证明取值来自配置。
     */
    @BeforeEach
    void setUp() {
        ForgeRedisProperties properties = new ForgeRedisProperties();
        properties.setKeyPrefix("orange-forge:test");
        properties.setDefaultTtlSeconds(120);
        forgeRedisTemplate = new ForgeRedisTemplate(stringRedisTemplate, properties);
    }

    /**
     * 验证逻辑键名被拼接为带项目与环境前缀的完整键名。
     */
    @Test
    @DisplayName("逻辑键名统一拼接前缀")
    void shouldPrefixKey() {
        assertThat(forgeRedisTemplate.key("auth:token:abc")).isEqualTo("orange-forge:test:auth:token:abc");
        assertThat(forgeRedisTemplate.getKeyPrefix()).isEqualTo("orange-forge:test");
    }

    /**
     * 验证未显式指定存活时间时使用配置的默认 TTL。
     */
    @Test
    @DisplayName("未指定 TTL 时使用配置的默认存活时间")
    void shouldUseConfiguredDefaultTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        forgeRedisTemplate.set("cache:probe", "value");

        verify(valueOperations).set("orange-forge:test:cache:probe", "value", Duration.ofSeconds(120));
    }

    /**
     * 验证显式指定的存活时间生效，且键名带前缀。
     */
    @Test
    @DisplayName("显式 TTL 生效且键名带前缀")
    void shouldUseExplicitTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        forgeRedisTemplate.set("cache:probe", "value", Duration.ofSeconds(30));

        verify(valueOperations).set("orange-forge:test:cache:probe", "value", Duration.ofSeconds(30));
    }

    /**
     * 验证非正的存活时间被拒绝，避免写入永不过期的业务缓存。
     */
    @Test
    @DisplayName("非正存活时间被拒绝")
    void shouldRejectNonPositiveTtl() {
        assertThatThrownBy(() -> forgeRedisTemplate.set("cache:probe", "value", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> forgeRedisTemplate.set("cache:probe", "value", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证读取与存在性判断都使用带前缀的键名。
     */
    @Test
    @DisplayName("读取与存在性判断使用带前缀键名")
    void shouldReadWithPrefixedKey() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("orange-forge:test:cache:probe")).thenReturn("cached");
        when(stringRedisTemplate.hasKey("orange-forge:test:cache:probe")).thenReturn(true);
        when(stringRedisTemplate.getExpire("orange-forge:test:cache:probe")).thenReturn(90L);

        assertThat(forgeRedisTemplate.get("cache:probe")).isEqualTo("cached");
        assertThat(forgeRedisTemplate.hasKey("cache:probe")).isTrue();
        assertThat(forgeRedisTemplate.getExpireSeconds("cache:probe")).isEqualTo(90L);
    }

    /**
     * 验证批量删除按前缀展开，空集合不触发 Redis 调用。
     */
    @Test
    @DisplayName("批量删除按前缀展开且空集合不调用 Redis")
    void shouldDeleteWithPrefix() {
        when(stringRedisTemplate.delete(List.of("orange-forge:test:a", "orange-forge:test:b"))).thenReturn(2L);

        assertThat(forgeRedisTemplate.delete(List.of("a", "b"))).isEqualTo(2L);
        assertThat(forgeRedisTemplate.delete(List.of())).isZero();
    }

    /**
     * 验证 Redis 连接失败时异常向上抛出，不被转换为成功或静默忽略。
     */
    @Test
    @DisplayName("连接失败时异常向上抛出")
    void shouldPropagateConnectionFailure() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> forgeRedisTemplate.get("cache:probe"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    /**
     * 验证写入时的连接失败同样向上抛出，调用方不得被视为成功。
     */
    @Test
    @DisplayName("写入连接失败时异常向上抛出")
    void shouldPropagateConnectionFailureOnWrite() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> forgeRedisTemplate.set("cache:probe", "value"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
