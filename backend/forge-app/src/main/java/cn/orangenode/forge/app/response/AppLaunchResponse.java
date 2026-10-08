package cn.orangenode.forge.app.response;

/**
 * App 启动配置，未登录即可读取。
 *
 * @param splash           当前生效的开屏，没有时为空
 * @param vipRequired      是否只有 VIP 能观看
 * @param registrationOpen 是否开放注册
 * @param serviceText      客服文字
 * @param noticeText       公告文字
 */
public record AppLaunchResponse(AppLaunchSplashResponse splash, boolean vipRequired, boolean registrationOpen,
        String serviceText, String noticeText) {
}
