package cn.orangenode.forge.member.response;

/**
 * 后台激活码列表项。
 *
 * @param id           激活码记录 ID
 * @param code         激活码，每 4 位用短横线分隔
 * @param status       状态 UNUSED/USED/REVOKED/EXPIRED（旧数据未使用且已过截止时间显示为 EXPIRED）
 * @param vipDays      注册成功后开通的 VIP 天数，空表示不开通
 * @param expireAt     旧数据的使用截止时间，新生成的码为空
 * @param remark       备注
 * @param usedMemberId 使用该码注册的用户 ID
 * @param usedUsername 使用该码注册的用户名
 * @param usedAt       使用时间
 * @param usedIp       注册来源地址
 * @param revokedAt    作废时间
 * @param createdAt    生成时间
 */
public record MemberActivationCodeResponse(String id, String code, String status, Integer vipDays, String expireAt,
        String remark, String usedMemberId, String usedUsername, String usedAt, String usedIp, String revokedAt,
        String createdAt) {
}
