package cn.orangenode.forge.framework.member;

/**
 * Redis 中保存的普通用户令牌会话内容。
 *
 * <p>只保存账号、设备、会话版本与时刻，不保存令牌明文与 VIP 状态，
 * VIP 与账号状态每次请求从账号目录实时读取。</p>
 *
 * @param memberId            普通用户 ID
 * @param deviceId            登录设备记录 ID
 * @param sessionVersion      签发时的账号会话版本
 * @param issuedAtEpochMilli  签发时刻（epoch 毫秒）
 * @param renewedAtEpochMilli 最近一次续期时刻（epoch 毫秒）
 */
record MemberTokenSession(Long memberId, Long deviceId, long sessionVersion, long issuedAtEpochMilli,
        long renewedAtEpochMilli) {
}
