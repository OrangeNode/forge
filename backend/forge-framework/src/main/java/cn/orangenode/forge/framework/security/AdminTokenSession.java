package cn.orangenode.forge.framework.security;

/**
 * Redis 中保存的令牌会话内容。
 *
 * <p>只保存账号 ID、签发时的账号会话版本与签发时刻：不保存权限，
 * 避免把权限固化在会话里直到自然过期；不保存令牌本身，令牌以摘要作为键名。</p>
 *
 * <p>签发时刻使用 epoch 毫秒，避免依赖 JSON 映射器的日期格式配置。</p>
 *
 * @param adminId           管理员 ID
 * @param sessionVersion    签发时的账号会话版本，与当前版本不一致即视为已撤销
 * @param issuedAtEpochMilli 签发时刻（epoch 毫秒）
 */
record AdminTokenSession(Long adminId, long sessionVersion, long issuedAtEpochMilli) {
}
