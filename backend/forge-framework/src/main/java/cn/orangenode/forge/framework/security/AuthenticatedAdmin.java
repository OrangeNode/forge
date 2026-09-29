package cn.orangenode.forge.framework.security;

/**
 * 已认证管理员主体。
 *
 * <p>作为安全上下文中的 principal，仅保存一次请求需要的身份信息，
 * 请求结束后随上下文清理，不能跨请求复用。</p>
 *
 * <p>当前管理员接口只能从本主体取账号 ID，不接受客户端提交的“当前账号 ID”。</p>
 *
 * @param id          管理员 ID
 * @param username    登录用户名
 * @param displayName 显示名称
 */
public record AuthenticatedAdmin(Long id, String username, String displayName) {
}
