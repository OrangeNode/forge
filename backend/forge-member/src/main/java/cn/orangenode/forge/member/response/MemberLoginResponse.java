package cn.orangenode.forge.member.response;

/**
 * App 登录或注册成功响应；令牌只在这里返回一次。
 *
 * @param accessToken 普通用户令牌
 * @param tokenType   令牌方案，固定为 Bearer
 * @param expiresIn   有效期秒数，滑动续期
 * @param profile     当前用户资料
 */
public record MemberLoginResponse(String accessToken, String tokenType, long expiresIn, MemberProfileResponse profile) {
}
