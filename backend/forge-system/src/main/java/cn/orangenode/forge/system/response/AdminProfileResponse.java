package cn.orangenode.forge.system.response;

import java.util.List;

/**
 * 当前管理员身份与权限。
 *
 * <p>权限代码只用于前端隐藏无权限的入口，后端每个受保护接口仍独立执行权限校验；
 * 权限每次请求实时解析，没有需要等待过期的会话快照。</p>
 *
 * @param id              管理员 ID，对外为字符串
 * @param username        登录用户名
 * @param displayName     显示名称
 * @param permissionCodes 当前有效权限代码，格式为 {@code 模块:资源:动作}
 */
public record AdminProfileResponse(String id, String username, String displayName, List<String> permissionCodes) {
}
