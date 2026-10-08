package cn.orangenode.forge.framework.member;

import java.time.Instant;

/**
 * 普通用户认证过滤链所需的账号视图。
 *
 * <p>只包含令牌校验与观看授权必需的信息，不包含密码编码结果；
 * 由拥有普通用户表的业务模块读取当前数据库状态后提供，不做缓存。</p>
 *
 * @param id          普通用户 ID
 * @param email       登录邮箱，已规范化为小写
 * @param nickname    昵称
 * @param enabled     账号是否启用，停用后其令牌立即失效
 * @param vipExpireAt VIP 到期时刻，从未开通时为 {@code null}
 * @param sessionVersion 账号会话版本，停用、改密等操作递增后此前令牌全部失效
 */
public record MemberAccountView(Long id, String email, String nickname, boolean enabled, Instant vipExpireAt,
        long sessionVersion) {
}
