package cn.orangenode.forge.framework.security;

/**
 * 一次登录签发的令牌及其有效期。
 *
 * <p>令牌只在签发响应中返回一次，服务端只保存其摘要；有效期以秒为单位，
 * 与接口契约中的 {@code expiresIn} 字段一致。</p>
 *
 * @param value            不透明随机令牌
 * @param expiresInSeconds 有效期秒数
 */
public record AdminToken(String value, long expiresInSeconds) {
}
