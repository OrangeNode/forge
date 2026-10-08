package cn.orangenode.forge.app.response;

/**
 * App 首页影片卡片，不含播放地址。
 *
 * @param id         影片 ID
 * @param name       名称
 * @param posterUrl  海报地址
 * @param remarks    更新备注
 * @param year       年份
 * @param categoryId 分类 ID
 */
public record AppVideoCardResponse(String id, String name, String posterUrl, String remarks, Integer year,
        String categoryId) {
}
