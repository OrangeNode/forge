package cn.orangenode.forge.member.support;

/**
 * 激活码状态；{@link #EXPIRED} 只用于查询与展示，由有效期推算，不写入数据库。
 */
public enum MemberActivationStatus {
    /**
     * 未使用。
     */
    UNUSED,

    /**
     * 已用于注册。
     */
    USED,

    /**
     * 已作废。
     */
    REVOKED,

    /**
     * 未使用但已过有效期。
     */
    EXPIRED
}
