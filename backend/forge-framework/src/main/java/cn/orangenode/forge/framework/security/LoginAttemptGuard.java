package cn.orangenode.forge.framework.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * 登录失败限流。
 *
 * <p>按规范化后的用户名摘要计数，达到配置上限后拒绝继续尝试并返回 429，
 * 登录成功时清零。计数键使用摘要而不是用户名明文，避免把账号名写进 Redis 键。</p>
 *
 * <p>只做按账号的限流，不按来源 IP 统计：同一账号的暴力尝试会被阻断，
 * 同时不会因为共享出口地址影响其他管理员。需要按来源限流时按新的验收条目补充。</p>
 */
@Slf4j
@Component
public class LoginAttemptGuard {

    /**
     * 登录失败计数键的逻辑前缀。
     */
    private static final String FAILURE_KEY_PREFIX = "auth:login-fail:";

    /**
     * Redis 操作入口。
     */
    private final ForgeRedisTemplate redis;

    /**
     * 认证与权限配置，提供失败次数上限与统计窗口。
     */
    private final ForgeSecurityProperties properties;

    /**
     * 构造登录失败限流组件。
     *
     * @param redis      Redis 操作入口
     * @param properties 认证与权限配置
     */
    public LoginAttemptGuard(ForgeRedisTemplate redis, ForgeSecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * 在尝试登录前检查该账号是否已被限流。
     *
     * @param normalizedUsername 规范化后的登录用户名
     * @throws BusinessException 失败次数达到上限时抛出 429
     */
    public void assertAllowed(String normalizedUsername) {
        String raw = redis.get(failureKey(normalizedUsername));
        long failures = parseCount(raw);
        if (failures >= properties.getLoginMaxAttempts()) {
            log.warn("登录尝试触发限流，失败次数={}，上限={}", failures, properties.getLoginMaxAttempts());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
        }
    }

    /**
     * 记录一次登录失败，并重新开始统计窗口计时。
     *
     * @param normalizedUsername 规范化后的登录用户名
     */
    public void recordFailure(String normalizedUsername) {
        redis.increment(failureKey(normalizedUsername),
                Duration.ofSeconds(properties.getLoginAttemptWindowSeconds()));
    }

    /**
     * 登录成功后清除失败计数。
     *
     * @param normalizedUsername 规范化后的登录用户名
     */
    public void clear(String normalizedUsername) {
        redis.delete(failureKey(normalizedUsername));
    }

    /**
     * 读取失败计数值。
     *
     * @param raw Redis 中的原始内容
     * @return 失败次数，缺失或内容非法时为 0
     */
    private long parseCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException failure) {
            log.warn("登录失败计数内容非法，按 0 处理");
            return 0L;
        }
    }

    /**
     * 拼接登录失败计数的逻辑键名。
     *
     * @param normalizedUsername 规范化后的登录用户名
     * @return 逻辑键名
     */
    private String failureKey(String normalizedUsername) {
        String normalized = normalizedUsername == null ? "" : normalizedUsername.toLowerCase(Locale.ROOT);
        return FAILURE_KEY_PREFIX + sha256(normalized);
    }

    /**
     * 计算字符串的 SHA-256 摘要。
     *
     * @param value 原始内容
     * @return 十六进制摘要
     */
    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256 摘要算法", failure);
        }
    }
}
