package cn.orangenode.forge.video.collector;

import java.util.List;

/**
 * 资源站一页搜索结果；total 为源站声明的命中总数，未声明时为 0。
 */
public record SearchedPage(int page, int pageCount, int total, List<SearchedVideo> videos) {
}
