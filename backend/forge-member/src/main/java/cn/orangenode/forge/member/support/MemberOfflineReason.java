package cn.orangenode.forge.member.support;

/**
 * 登录设备离线原因。
 */
public enum MemberOfflineReason {

    /**
     * 用户主动退出。
     */
    LOGOUT,

    /**
     * 超过同时登录台数，最早登录的设备被踢出。
     */
    LIMIT,

    /**
     * 后台强制下线。
     */
    ADMIN,

    /**
     * 停用、重置或修改密码导致全部令牌撤销。
     */
    REVOKED,

    /**
     * 令牌长期未使用自然过期。
     */
    EXPIRED
}
