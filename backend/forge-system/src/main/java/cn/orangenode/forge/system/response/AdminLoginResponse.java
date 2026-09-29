package cn.orangenode.forge.system.response;

/**
 * 管理员登录响应。
 *
 * <p>令牌只在此响应中返回一次，服务端只保存摘要；前端保存在自己的
 * {@code sessionStorage} 中，不进入 URL、日志或公共请求包。</p>
 *
 * @param accessToken 不透明随机令牌
 * @param tokenType   令牌方案，固定为 {@code Bearer}
 * @param expiresIn   有效期秒数，首版不提供刷新令牌
 * @param admin       当前管理员基础信息
 */
public record AdminLoginResponse(String accessToken, String tokenType, long expiresIn, AdminSummaryResponse admin) {
}
