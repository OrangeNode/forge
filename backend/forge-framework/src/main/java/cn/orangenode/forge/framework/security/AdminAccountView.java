package cn.orangenode.forge.framework.security;

/**
 * 认证过滤链所需的管理员账号视图。
 *
 * <p>只包含令牌校验与权限解析必需的信息：账号状态决定令牌是否仍然有效，
 * 用户名与显示名称用于构造认证主体。</p>
 *
 * <p>密码编码结果不在此视图中出现：校验凭据属于登录用例，由拥有账号表的业务模块完成，
 * 基础模块不接触密码摘要。</p>
 *
 * @param id          管理员 ID
 * @param username    登录用户名，已按账号规范化规则存储
 * @param displayName 显示名称
 * @param enabled     账号是否为启用状态，停用后其令牌立即失效
 */
public record AdminAccountView(Long id, String username, String displayName, boolean enabled) {
}
