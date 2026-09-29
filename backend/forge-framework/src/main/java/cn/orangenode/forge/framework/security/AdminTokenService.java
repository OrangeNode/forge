package cn.orangenode.forge.framework.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 管理员令牌会话。
 *
 * <p>令牌为 32 字节安全随机值，只在签发响应中返回；Redis 中以令牌的 SHA-256 摘要作为键名保存会话，
 * 因此即使 Redis 内容泄漏也不能直接还原出可用令牌，日志也不会记录令牌明文。</p>
 *
 * <p>撤销采用账号会话版本：退出只删除当前令牌；停用、改密等需要让该账号全部会话失效的场景，
 * 通过递增版本键实现，签发时记录版本、校验时比对版本。这样批量撤销是常数次写操作，
 * 不需要遍历或通配扫描令牌键，也不会在并发登录时留下仍然可用的旧会话——
 * 撤销前签发的令牌版本落后，一律失效。</p>
 *
 * <p>Redis 不可用时异常向上抛出，由调用方拒绝受保护请求（HTTP 200 + body.code=503），
 * 不得降级为放行。</p>
 */
@Slf4j
@Component
public class AdminTokenService {

    /**
     * 令牌随机字节数，256 位熵。属于安全不变式，不做成配置项。
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * 令牌会话键的逻辑前缀，完整键名由 {@link ForgeRedisTemplate} 拼接项目与环境前缀。
     */
    private static final String SESSION_KEY_PREFIX = "auth:session:";

    /**
     * 账号会话版本键的逻辑前缀。
     */
    private static final String VERSION_KEY_PREFIX = "auth:session-version:";

    /**
     * Redis 操作入口，负责键前缀与存活时间。
     */
    private final ForgeRedisTemplate redis;

    /**
     * 认证与权限配置，提供令牌有效期。
     */
    private final ForgeSecurityProperties properties;

    /**
     * JSON 映射器，用于序列化会话内容。
     */
    private final JsonMapper jsonMapper;

    /**
     * 安全随机数发生器。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造管理员令牌会话组件。
     *
     * @param redis      Redis 操作入口
     * @param properties 认证与权限配置
     * @param jsonMapper JSON 映射器
     */
    public AdminTokenService(ForgeRedisTemplate redis, ForgeSecurityProperties properties, JsonMapper jsonMapper) {
        this.redis = redis;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 为指定管理员签发令牌并写入会话。
     *
     * @param adminId 管理员 ID
     * @return 令牌与有效期
     */
    public AdminToken issue(Long adminId) {
        String token = newToken();
        AdminTokenSession session = new AdminTokenSession(adminId, currentVersion(adminId),
                System.currentTimeMillis());
        Duration ttl = Duration.ofSeconds(properties.getTokenTtlSeconds());
        redis.set(sessionKey(token), jsonMapper.writeValueAsString(session), ttl);
        log.info("管理员 [{}] 登录成功并签发令牌，有效期 {} 秒", adminId, properties.getTokenTtlSeconds());
        return new AdminToken(token, properties.getTokenTtlSeconds());
    }

    /**
     * 校验令牌并解析出管理员 ID。
     *
     * <p>会话不存在、内容损坏或版本落后于当前账号会话版本时都视为无效；
     * 内容损坏与版本落后的会话会被删除，避免继续占用 Redis。</p>
     *
     * @param token 客户端提交的令牌
     * @return 有效时返回管理员 ID，否则返回空
     */
    public Optional<Long> resolveAdminId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String key = sessionKey(token);
        String payload = redis.get(key);
        if (payload == null) {
            return Optional.empty();
        }
        AdminTokenSession session = readSession(key, payload);
        if (session == null) {
            return Optional.empty();
        }
        if (session.sessionVersion() != currentVersion(session.adminId())) {
            log.info("令牌会话版本已失效，按已撤销处理，adminId={}", session.adminId());
            redis.delete(key);
            return Optional.empty();
        }
        return Optional.of(session.adminId());
    }

    /**
     * 撤销单个令牌，用于退出登录。
     *
     * @param token 客户端提交的令牌
     */
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        redis.delete(sessionKey(token));
    }

    /**
     * 撤销指定管理员的全部会话，用于停用账号、修改密码等场景。
     *
     * <p>递增会话版本后，该账号此前签发的所有令牌在下一次请求即失效，
     * 不需要等待令牌自然过期。</p>
     *
     * @param adminId 管理员 ID
     */
    public void revokeAllForAdmin(Long adminId) {
        long version = redis.increment(versionKey(adminId), Duration.ofSeconds(properties.getTokenTtlSeconds()));
        log.info("管理员 [{}] 的全部令牌会话已撤销，当前会话版本为 {}", adminId, version);
    }

    /**
     * 读取账号当前的会话版本，不存在时视为初始版本 0。
     *
     * @param adminId 管理员 ID
     * @return 会话版本
     */
    private long currentVersion(Long adminId) {
        String raw = redis.get(versionKey(adminId));
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException failure) {
            log.warn("账号会话版本内容非法，按初始版本处理，adminId={}", adminId);
            return 0L;
        }
    }

    /**
     * 解析 Redis 中的会话内容，内容损坏时删除该会话。
     *
     * @param key     会话键名
     * @param payload 会话内容
     * @return 会话对象，内容不可用时返回 {@code null}
     */
    private AdminTokenSession readSession(String key, String payload) {
        try {
            AdminTokenSession session = jsonMapper.readValue(payload, AdminTokenSession.class);
            if (session == null || session.adminId() == null) {
                redis.delete(key);
                return null;
            }
            return session;
        } catch (JacksonException failure) {
            log.warn("令牌会话内容无法解析，按无效令牌处理并删除");
            redis.delete(key);
            return null;
        }
    }

    /**
     * 生成不可预测的令牌字符串。
     *
     * @return URL 安全的 Base64 令牌
     */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 计算令牌摘要，作为 Redis 键名的一部分。
     *
     * @param token 令牌明文
     * @return 十六进制摘要
     */
    private String digest(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256 摘要算法", failure);
        }
    }

    /**
     * 拼接令牌会话的逻辑键名。
     *
     * @param token 令牌明文
     * @return 逻辑键名
     */
    private String sessionKey(String token) {
        return SESSION_KEY_PREFIX + digest(token);
    }

    /**
     * 拼接账号会话版本的逻辑键名。
     *
     * @param adminId 管理员 ID
     * @return 逻辑键名
     */
    private String versionKey(Long adminId) {
        return VERSION_KEY_PREFIX + adminId;
    }
}
