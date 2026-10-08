package cn.orangenode.forge.app.response;

import java.util.List;

/**
 * App 首页数据。
 *
 * @param banners  当前在线的轮播
 * @param sections 启用的推荐行，没有影片的行不返回
 */
public record AppHomeResponse(List<AppHomeBannerResponse> banners, List<AppHomeSectionResponse> sections) {
}
