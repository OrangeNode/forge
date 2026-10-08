package cn.orangenode.forge.framework.member;

/**
 * 一次普通用户登录签发的令牌。
 *
 * <p>令牌明文只在签发响应中返回一次；摘要用于登录设备记录定位并撤销对应会话，
 * 不能还原为令牌，也不对外返回。</p>
 *
 * @param value            不透明随机令牌
 * @param digest           令牌 SHA-256 摘要
 * @param expiresInSeconds 有效期秒数，每次续期后重新计算
 */
public record MemberToken(String value, String digest, long expiresInSeconds) {
}
