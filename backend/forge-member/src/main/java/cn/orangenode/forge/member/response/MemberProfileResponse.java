package cn.orangenode.forge.member.response;

/**
 * App 当前用户资料。
 *
 * @param id          用户 ID
 * @param email       登录邮箱
 * @param nickname    昵称
 * @param avatarUrl   头像地址
 * @param vipActive   VIP 当前是否有效
 * @param vipExpireAt VIP 到期时间，从未开通为空
 * @param vipRequired 当前是否只有 VIP 能观看；为 false 时登录即可观看
 */
public record MemberProfileResponse(String id, String email, String nickname, String avatarUrl, boolean vipActive,
        String vipExpireAt, boolean vipRequired) {
}
