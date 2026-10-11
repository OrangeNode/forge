package cn.orangenode.forge.app.response;

/**
 * App 首页轮播。
 *
 * @param id         轮播 ID
 * @param title      标题
 * @param imageUrl   图片地址（相对接口基础地址）
 * @param targetType 跳转类型 NONE/VIDEO/CATEGORY/URL
 * @param targetId   跳转目标 ID
 * @param targetUrl  跳转类型为 URL 时的外部链接
 * @param tvImageUrl 电视端图片地址；未单独配置时与 imageUrl 相同
 */
public record AppHomeBannerResponse(String id, String title, String imageUrl, String targetType, String targetId,
        String targetUrl, String tvImageUrl) {
}
