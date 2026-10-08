package cn.orangenode.forge.app.response;

/**
 * 后台首页轮播项。
 *
 * @param id          轮播 ID
 * @param title       标题
 * @param imageFileId 图片文件 ID
 * @param imageUrl    App 图片地址
 * @param targetType  跳转类型
 * @param targetId    跳转目标 ID
 * @param targetName  跳转目标名称，目标已删除时为空
 * @param startAt     上线时间
 * @param endAt       下线时间
 * @param enabled     是否启用
 * @param sortNo      显示顺序
 * @param active      当前是否在线
 */
public record AppBannerResponse(String id, String title, String imageFileId, String imageUrl, String targetType,
        String targetId, String targetName, String startAt, String endAt, boolean enabled, int sortNo,
        boolean active) {
}
