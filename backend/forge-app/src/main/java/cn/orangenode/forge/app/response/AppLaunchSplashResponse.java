package cn.orangenode.forge.app.response;

/**
 * App 开屏。
 *
 * @param imageUrl        图片地址（相对接口基础地址）
 * @param durationSeconds 停留秒数
 * @param skippable       是否可跳过
 */
public record AppLaunchSplashResponse(String imageUrl, int durationSeconds, boolean skippable) {
}
