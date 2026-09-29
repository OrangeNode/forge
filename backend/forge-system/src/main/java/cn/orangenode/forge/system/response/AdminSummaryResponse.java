package cn.orangenode.forge.system.response;

/**
 * 管理员基础信息。
 *
 * <p>用于登录响应等只需要身份展示的场景，不包含权限明细；
 * 需要权限的调用方使用 {@link AdminProfileResponse}。</p>
 *
 * @param id          管理员 ID，对外为字符串
 * @param username    登录用户名
 * @param displayName 显示名称
 */
public record AdminSummaryResponse(String id, String username, String displayName) {
}
