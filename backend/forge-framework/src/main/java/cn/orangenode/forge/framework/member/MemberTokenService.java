package cn.orangenode.forge.framework.member;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Component;

import cn.orangenode.forge.framework.config.ForgeMemberAuthProperties;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 普通用户令牌会话。
 *
 * <p>与管理员令牌使用不同的 Redis 键前缀 {@code member:session:}，因此管理员令牌不能通过普通用户校验，
 * 反之亦然。令牌为 32 字节安全随机值，Redis 只保存其 SHA-256 摘要对应的会话。</p>
 *
 * <p>有效期为滑动有效期：距上次续期超过配置间隔的请求会把会话存活时间重置为完整有效期。
 * 账号级撤销使用数据库中的会话版本，不依赖会过期的 Redis 版本键，避免长期续期的令牌被误判失效。</p>
 */
@Slf4j
@Component
public class MemberTokenService {

    /**
     * 令牌随机字节数，256 位熵，属于安全不变式。
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * 普通用户会话键的逻辑前缀，与管理员会话键前缀不同。
     */
    private static final String SESSION_KEY_PREFIX = "member:session:";

    /**
     * Redis 操作入口。
     */
    private final ForgeRedisTemplate redis;

    /**
     * 普通用户认证配置。
     */
    private final ForgeMemberAuthProperties properties;

    /**
     * JSON 映射器。
     */
    private final JsonMapper jsonMapper;

    /**
     * 安全随机数发生器。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造普通用户令牌会话组件。
     *
     * @param redis      Redis 操作入口
     * @param properties 普通用户认证配置
     * @param jsonMapper JSON 映射器
     */
    public MemberTokenService(ForgeRedisTemplate redis, ForgeMemberAuthProperties properties, JsonMapper jsonMapper) {
        this.redis = redis;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /**
     * 生成新令牌但不写入会话，调用方先把摘要写入登录设备记录再调用 {@link #activate}。
     *
     * @return 新令牌与摘要
     */
    public MemberToken newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new MemberToken(token, digest(token), properties.getTokenTtlSeconds());
    }

    /**
     * 写入令牌会话。
     *
     * @param token          由 {@link #newToken()} 生成的令牌
     * @param memberId       普通用户 ID
     * @param deviceId       登录设备记录 ID
     * @param sessionVersion 账号当前会话版本
     */
    public void activate(MemberToken token, Long memberId, Long deviceId, long sessionVersion) {
        long now = System.currentTimeMillis();
        write(token.digest(), new MemberTokenSession(memberId, deviceId, sessionVersion, now, now));
        log.info("普通用户 [{}] 在设备记录 [{}] 登录并签发令牌", memberId, deviceId);
    }

    /**
     * 校验令牌，必要时续期。
     *
     * @param token 客户端提交的令牌
     * @return 有效时返回会话信息，否则为空
     */
    public Optional<MemberSessionTicket> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String digest = digest(token);
        String payload = redis.get(sessionKey(digest));
        if (payload == null) {
            return Optional.empty();
        }
        MemberTokenSession session = readSession(digest, payload);
        if (session == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        boolean renew = now - session.renewedAtEpochMilli() >= properties.getRenewIntervalSeconds() * 1000L;
        if (renew) {
            write(digest, new MemberTokenSession(session.memberId(), session.deviceId(), session.sessionVersion(),
                    session.issuedAtEpochMilli(), now));
        }
        return Optional.of(new MemberSessionTicket(session.memberId(), session.deviceId(),
                session.sessionVersion(), renew));
    }

    /**
     * 撤销客户端提交的令牌，用于退出登录。
     *
     * @param token 令牌明文
     */
    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            revokeDigest(digest(token));
        }
    }

    /**
     * 按摘要撤销会话，用于踢出设备与后台强制下线。
     *
     * @param tokenDigest 令牌摘要，可为空
     */
    public void revokeDigest(String tokenDigest) {
        if (tokenDigest != null && !tokenDigest.isBlank()) {
            redis.delete(sessionKey(tokenDigest));
        }
    }

    /**
     * 判断摘要对应的会话是否仍然存在。
     *
     * @param tokenDigest 令牌摘要，可为空
     * @return 会话存在时为 {@code true}
     */
    public boolean isActive(String tokenDigest) {
        return tokenDigest != null && !tokenDigest.isBlank() && redis.hasKey(sessionKey(tokenDigest));
    }

    /**
     * 计算令牌摘要。
     *
     * @param token 令牌明文
     * @return 十六进制 SHA-256 摘要
     */
    public String digest(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境缺少 SHA-256 摘要算法", failure);
        }
    }

    /**
     * 以完整有效期写入会话。
     *
     * @param tokenDigest 令牌摘要
     * @param session     会话内容
     */
    private void write(String tokenDigest, MemberTokenSession session) {
        redis.set(sessionKey(tokenDigest), jsonMapper.writeValueAsString(session),
                Duration.ofSeconds(properties.getTokenTtlSeconds()));
    }

    /**
     * 解析会话内容，损坏时删除。
     *
     * @param tokenDigest 令牌摘要
     * @param payload     会话内容
     * @return 会话，不可用时为 {@code null}
     */
    private MemberTokenSession readSession(String tokenDigest, String payload) {
        try {
            MemberTokenSession session = jsonMapper.readValue(payload, MemberTokenSession.class);
            if (session != null && session.memberId() != null && session.deviceId() != null) {
                return session;
            }
        } catch (JacksonException failure) {
            log.warn("普通用户令牌会话内容无法解析，按无效令牌处理并删除");
        }
        redis.delete(sessionKey(tokenDigest));
        return null;
    }

    /**
     * 拼接会话逻辑键名。
     *
     * @param tokenDigest 令牌摘要
     * @return 逻辑键名
     */
    private String sessionKey(String tokenDigest) {
        return SESSION_KEY_PREFIX + tokenDigest;
    }
}
