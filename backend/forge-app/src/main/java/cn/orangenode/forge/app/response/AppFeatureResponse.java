package cn.orangenode.forge.app.response;

/**
 * 功能开关。
 *
 * @param vipRequired      VIP 开关
 * @param registrationOpen 是否开放注册
 * @param maxDevices       同时登录设备上限
 * @param serviceText      客服文字
 * @param noticeText       公告文字
 * @param updatedAt        最近修改时间
 */
public record AppFeatureResponse(boolean vipRequired, boolean registrationOpen, int maxDevices, String serviceText,
        String noticeText, String updatedAt) {
}
