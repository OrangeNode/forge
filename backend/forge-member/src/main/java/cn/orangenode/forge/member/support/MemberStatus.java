package cn.orangenode.forge.member.support;

/**
 * 普通用户账号状态。
 */
public enum MemberStatus {

    /**
     * 启用。
     */
    ENABLED,

    /**
     * 停用，令牌立即失效且不能登录。
     */
    DISABLED
}
