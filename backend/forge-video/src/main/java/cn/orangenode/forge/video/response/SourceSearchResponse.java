package cn.orangenode.forge.video.response;

import java.util.List;

/**
 * 单个资源站的搜索结果；error 非空表示该站请求失败或超时，不影响其他资源站。
 */
public record SourceSearchResponse(String sourceId, String sourceName, int page, int pageCount, int total,
        List<SearchItemResponse> items, String error) {
}
