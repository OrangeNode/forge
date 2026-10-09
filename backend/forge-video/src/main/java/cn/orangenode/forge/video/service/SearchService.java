package cn.orangenode.forge.video.service;

import java.util.List;
import cn.orangenode.forge.video.request.SearchCollectRequest;
import cn.orangenode.forge.video.response.CollectionBatchResponse;
import cn.orangenode.forge.video.response.SourceSearchResponse;

/**
 * 资源站关键词搜索与勾选采集用例。
 */
public interface SearchService {
    /**
     * 按关键词搜索单个资源站的指定页，请求失败时返回业务错误。
     */
    SourceSearchResponse search(Long sourceId, String keyword, Integer page);

    /**
     * 并发搜索全部启用资源站的第一页，单站失败或超时只在该站结果中标出。
     */
    List<SourceSearchResponse> searchAll(String keyword);

    /**
     * 按资源站分组提交勾选采集，逐站返回失败原因。
     */
    CollectionBatchResponse collect(SearchCollectRequest request);
}
