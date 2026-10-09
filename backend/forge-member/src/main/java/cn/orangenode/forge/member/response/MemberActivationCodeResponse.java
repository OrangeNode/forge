package cn.orangenode.forge.member.response;

/**
 * 后台激活码列表项。
 *
 * @param id           激活码记录 ID
 * @param code         激活码，每 4 位用短横线分隔
 * @param status       状态 UNUSED/USED/REVOKED/EXPIRED（未使用且已过有效期显示为 EXPIRED）
 * @param expireAt     有效期截止时间，空表示长期有效
 * @param remark       备注
 * @param usedMemberId 使用该码注册的用户 ID
 * @param usedUsername 使用该码注册的用户名
 * @param usedAt       使用时间
 * @param usedIp       注册来源地址
 * @param revokedAt    作废时间
 * @param createdAt    生成时间
 */
public record MemberActivationCodeResponse(String id, String code, String status, String expireAt, String remark,
        String usedMemberId, String usedUsername, String usedAt, String usedIp, String revokedAt, String createdAt) {
}
