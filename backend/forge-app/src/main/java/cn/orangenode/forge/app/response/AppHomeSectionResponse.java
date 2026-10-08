package cn.orangenode.forge.app.response;

import java.util.List;

/**
 * App 首页推荐行。
 *
 * @param id         推荐位 ID
 * @param title      标题
 * @param sourceType 数据来源
 * @param categoryId 分类 ID，“查看更多”可跳转到该分类
 * @param videos     影片卡片
 */
public record AppHomeSectionResponse(String id, String title, String sourceType, String categoryId,
        List<AppVideoCardResponse> videos) {
}
