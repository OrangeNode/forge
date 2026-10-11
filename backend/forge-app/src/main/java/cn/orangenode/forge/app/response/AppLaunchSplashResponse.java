package cn.orangenode.forge.app.response;

/**
 * App 开屏。
 *
 * @param imageUrl        图片地址（相对接口基础地址）
 * @param durationSeconds 停留秒数
 * @param skippable       是否可跳过
 * @param targetType      点击跳转类型 NONE / VIDEO / URL
 * @param targetValue     跳转影片 ID 或外部链接
 * @param tvImageUrl      电视端封面图地址；未单独配置时与 imageUrl 相同
 */
public record AppLaunchSplashResponse(String imageUrl, int durationSeconds, boolean skippable, String targetType,
        String targetValue, String tvImageUrl) {
}
