package cn.orangenode.forge.framework.member;

/**
 * 一次令牌校验的结果。
 *
 * @param memberId       普通用户 ID
 * @param deviceId       登录设备记录 ID
 * @param sessionVersion 签发时的账号会话版本，由调用方与账号当前版本比较
 * @param renewed        本次校验是否完成了续期
 */
public record MemberSessionTicket(Long memberId, Long deviceId, long sessionVersion, boolean renewed) {
}
