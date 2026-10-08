package cn.orangenode.forge.framework.catalog;

/**
 * 首页与推荐位使用的影片卡片。
 *
 * @param id         影片 ID
 * @param name       影片名称
 * @param posterUrl  海报地址
 * @param remarks    更新备注，如“更新至 10 集”
 * @param year       年份，可为空
 * @param categoryId 分类 ID
 */
public record VideoCard(Long id, String name, String posterUrl, String remarks, Integer year, Long categoryId) {
}
