package cn.orangenode.forge.member.response;

/**
 * 登录设备记录。
 *
 * @param id            设备记录 ID
 * @param memberId      用户 ID
 * @param memberEmail   用户邮箱
 * @param deviceCode    设备编号
 * @param deviceName    设备名称
 * @param clientIp      最近来源地址
 * @param online        是否在线
 * @param offlineReason 离线原因 LOGOUT/LIMIT/ADMIN/REVOKED/EXPIRED
 * @param loginAt       最近登录时间
 * @param lastActiveAt  最近活跃时间
 * @param offlineAt     离线时间
 */
public record MemberDeviceResponse(String id, String memberId, String memberEmail, String deviceCode,
        String deviceName, String clientIp, boolean online, String offlineReason, String loginAt,
        String lastActiveAt, String offlineAt) {
}
