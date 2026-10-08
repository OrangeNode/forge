package cn.orangenode.forge.member.response;

/**
 * VIP 变更记录。
 *
 * @param id             记录 ID
 * @param changeType     变更类型 EXTEND/SET/CANCEL
 * @param days           延长天数
 * @param beforeExpireAt 变更前到期时间
 * @param afterExpireAt  变更后到期时间
 * @param remark         备注
 * @param operatorId     操作管理员 ID
 * @param createdAt      变更时间
 */
public record MemberVipLogResponse(String id, String changeType, Integer days, String beforeExpireAt,
        String afterExpireAt, String remark, String operatorId, String createdAt) {
}
