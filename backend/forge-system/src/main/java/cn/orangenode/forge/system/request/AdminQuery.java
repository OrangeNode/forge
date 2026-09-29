package cn.orangenode.forge.system.request;

/**
 * 管理员列表筛选条件。
 *
 * <p>用户名按规范化规则处理后再做模糊匹配，与保存、登录使用同一套大小写口径；
 * 状态为可选精确匹配，取值在校验层按 {@code AdminAccountStatus} 检查。</p>
 *
 * @param username 用户名关键字，允许为空表示不筛选
 * @param status   账号状态代码，允许为空表示不筛选
 */
public record AdminQuery(String username, String status) {
}
