package cn.orangenode.forge.member.response;

/**
 * 后台普通用户列表与详情项，不含密码与令牌。
 *
 * @param id            用户 ID
 * @param email         登录邮箱
 * @param nickname      昵称
 * @param avatarUrl     头像地址
 * @param status        账号状态 ENABLED/DISABLED
 * @param vipActive     VIP 当前是否有效
 * @param vipExpireAt   VIP 到期时间
 * @param onlineDevices 在线设备数
 * @param registerIp    注册来源地址
 * @param lastLoginAt   最近登录时间
 * @param lastLoginIp   最近登录来源地址
 * @param createdAt     注册时间
 */
public record MemberUserResponse(String id, String email, String nickname, String avatarUrl, String status,
        boolean vipActive, String vipExpireAt, long onlineDevices, String registerIp, String lastLoginAt,
        String lastLoginIp, String createdAt) {
}
