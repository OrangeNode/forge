package cn.orangenode.forge.app.response;

import java.util.List;

/**
 * 后台首页推荐位项。
 *
 * @param id           推荐位 ID
 * @param title        标题
 * @param sourceType   数据来源
 * @param categoryId   分类 ID
 * @param categoryName 分类名称，已删除时为空
 * @param videos       手动挑选的影片（含名称，便于后台展示）
 * @param itemLimit    最多展示条数
 * @param enabled      是否启用
 * @param sortNo       显示顺序
 */
public record AppSectionResponse(String id, String title, String sourceType, String categoryId, String categoryName,
        List<AppPickedVideoResponse> videos, int itemLimit, boolean enabled, int sortNo) {
}
