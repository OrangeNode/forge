package cn.orangenode.forge.framework.security;

import java.util.Optional;

/**
 * Bearer 令牌请求头解析。
 *
 * <p>认证过滤链与退出接口共用同一处解析规则，避免两处各自判断前缀导致行为不一致。
 * 方案名按大小写不敏感匹配，令牌本身保持原样。</p>
 */
public final class BearerTokens {

    /**
     * Bearer 方案前缀。
     */
    private static final String PREFIX = "Bearer ";

    /**
     * 工具类不允许实例化。
     */
    private BearerTokens() {
    }

    /**
     * 从 Authorization 请求头中取出令牌。
     *
     * @param authorizationHeader 请求头原始值，允许为 {@code null}
     * @return 令牌，缺失或格式不符时为空
     */
    public static Optional<String> resolve(String authorizationHeader) {
        if (authorizationHeader == null) {
            return Optional.empty();
        }
        String value = authorizationHeader.trim();
        if (value.length() <= PREFIX.length() || !value.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return Optional.empty();
        }
        String token = value.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
