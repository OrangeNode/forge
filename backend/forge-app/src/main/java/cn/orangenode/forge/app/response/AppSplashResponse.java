package cn.orangenode.forge.app.response;

/**
 * 后台开屏项。
 *
 * @param id              开屏 ID
 * @param name            名称
 * @param imageFileId     封面图文件 ID
 * @param imageUrl        App 图片地址
 * @param durationSeconds 停留秒数
 * @param skippable       是否可跳过
 * @param startAt         生效开始时间
 * @param endAt           生效结束时间
 * @param enabled         是否启用
 * @param sortNo          优先顺序
 * @param active          当前是否处于生效状态
 * @param targetType      点击跳转类型 NONE / VIDEO / URL
 * @param targetValue     跳转影片 ID 或外部链接
 * @param targetName      跳转影片名称，非影片或影片已删除时为空
 */
public record AppSplashResponse(String id, String name, String imageFileId, String imageUrl, int durationSeconds,
        boolean skippable, String startAt, String endAt, boolean enabled, int sortNo, boolean active,
        String targetType, String targetValue, String targetName) {
}
