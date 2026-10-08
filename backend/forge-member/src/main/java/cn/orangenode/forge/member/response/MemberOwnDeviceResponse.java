package cn.orangenode.forge.member.response;

/**
 * 当前用户自己的登录设备。
 *
 * @param id           设备记录 ID
 * @param deviceName   设备名称
 * @param clientIp     最近来源地址
 * @param online       是否在线
 * @param current      是否为发起请求的这台设备
 * @param loginAt      最近登录时间
 * @param lastActiveAt 最近活跃时间
 */
public record MemberOwnDeviceResponse(String id, String deviceName, String clientIp, boolean online,
        boolean current, String loginAt, String lastActiveAt) {
}
