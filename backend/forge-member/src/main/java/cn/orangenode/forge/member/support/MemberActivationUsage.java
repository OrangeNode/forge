package cn.orangenode.forge.member.support;

/**
 * 激活码的使用用途，写入 {@code used_for}，后台据此区分注册与续费。
 */
public enum MemberActivationUsage {
    /**
     * 用于注册新账号。
     */
    REGISTER,

    /**
     * 已登录用户用于续费 VIP。
     */
    RENEW
}
