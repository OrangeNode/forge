package cn.orangenode.forge.video.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.orangenode.forge.core.exception.BusinessException;
import cn.orangenode.forge.core.response.ErrorCode;
import cn.orangenode.forge.video.collector.CollectedVideo;
import cn.orangenode.forge.video.collector.MacCmsClient;
import cn.orangenode.forge.video.collector.MacCmsParser;
import cn.orangenode.forge.video.collector.SearchedPage;
import cn.orangenode.forge.video.config.VideoProperties;
import cn.orangenode.forge.video.entity.OriginEntity;
import cn.orangenode.forge.video.entity.SourceEntity;
import cn.orangenode.forge.video.entity.VideoEntity;
import cn.orangenode.forge.video.request.SearchCollectRequest;
import cn.orangenode.forge.video.response.CollectionBatchResponse;
import cn.orangenode.forge.video.response.SearchItemResponse;
import cn.orangenode.forge.video.response.SourceSearchResponse;
import cn.orangenode.forge.video.service.CollectionService;
import cn.orangenode.forge.video.service.SearchService;
import cn.orangenode.forge.video.service.SettingsService;
import cn.orangenode.forge.video.support.VideoValues;
import lombok.RequiredArgsConstructor;

/**
 * 资源站搜索只读取源站与本库，不写入数据；勾选采集复用采集运行，入库规则与范围采集一致。
 * 全站搜索的网络请求并发执行，并发数取后台“最大并发采集数”，查库比对在请求线程中完成。
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    /** 关键词长度上限，属于接口校验不变式。 */
    private static final int KEYWORD_LIMIT = 50;

    private final VideoStore store;
    private final MacCmsClient client;
    private final MacCmsParser parser;
    private final CollectionService collections;
    private final SettingsService settings;
    private final VideoProperties properties;

    /**
     * 单站搜索允许翻页，等待时间与全站搜索相同；资源站停用、请求失败或超时直接返回业务错误。
     */
    @Override
    public SourceSearchResponse search(Long sourceId, String keyword, Integer page) {
        String word = keyword(keyword);
        SourceEntity source = store.requireSource(sourceId);
        if (!Boolean.TRUE.equals(source.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "资源站已停用");
        }
        SourceSearchResponse result = searchSources(List.of(source), word, page == null || page < 1 ? 1 : page).getFirst();
        if (result.error() != null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, result.error());
        }
        return result;
    }

    /**
     * 每个启用资源站只取第一页，单站失败或超时只在该站结果中标出。
     */
    @Override
    public List<SourceSearchResponse> searchAll(String keyword) {
        String word = keyword(keyword);
        List<SourceEntity> sources = store.sources.selectList(Wrappers.<SourceEntity>lambdaQuery()
                .eq(SourceEntity::getEnabled, true).orderByAsc(SourceEntity::getId));
        return sources.isEmpty() ? List.of() : searchSources(sources, word, 1);
    }

    /**
     * 并发请求各资源站的指定页，总等待时间受配置限制（需短于后台请求超时），超时的请求被取消并标出。
     */
    private List<SourceSearchResponse> searchSources(List<SourceEntity> sources, String word, int page) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency(), sources.size()),
                Thread.ofPlatform().name("video-search-", 1).factory());
        try {
            Map<SourceEntity, Future<SearchedPage>> futures = new LinkedHashMap<>();
            for (SourceEntity source : sources) {
                futures.put(source, pool.submit(() -> fetch(source, word, page)));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.getSearchTimeoutSeconds());
            List<SourceSearchResponse> results = new ArrayList<>();
            for (Map.Entry<SourceEntity, Future<SearchedPage>> entry : futures.entrySet()) {
                results.add(await(entry.getKey(), entry.getValue(), deadline));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 等待单个资源站结果，失败与超时转为该站的错误说明，系统中断时停止整个搜索。
     */
    private SourceSearchResponse await(SourceEntity source, Future<SearchedPage> future, long deadline) {
        try {
            SearchedPage page = future.get(Math.max(deadline - System.nanoTime(), 0), TimeUnit.NANOSECONDS);
            return result(source, page);
        } catch (TimeoutException failure) {
            future.cancel(true);
            return failed(source, "搜索超时，资源站响应过慢，可稍后重试");
        } catch (ExecutionException failure) {
            return failed(source, failure.getCause() instanceof BusinessException business
                    ? business.getMessage() : "搜索失败，资源站返回的数据无法识别");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "搜索已中断");
        }
    }

    /**
     * 按资源站分组，每站提交一条勾选采集运行；某站已有活动采集或已停用时只记该站失败。
     */
    @Override
    public CollectionBatchResponse collect(SearchCollectRequest request) {
        if (request.items().size() > properties.getSearchCollectMaxItems()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "一次最多采集 " + properties.getSearchCollectMaxItems() + " 部，请分批提交");
        }
        Map<Long, List<String>> grouped = request.items().stream().collect(Collectors.groupingBy(
                item -> VideoValues.id(item.sourceId()), LinkedHashMap::new,
                Collectors.mapping(item -> item.upstreamId().strip(), Collectors.toList())));
        List<CollectionBatchResponse.Failure> failures = new ArrayList<>();
        int succeeded = 0;
        for (Map.Entry<Long, List<String>> entry : grouped.entrySet()) {
            try {
                collections.collectSelected(entry.getKey(), entry.getValue());
                succeeded++;
            } catch (BusinessException failure) {
                SourceEntity source = store.sources.selectById(entry.getKey());
                failures.add(new CollectionBatchResponse.Failure(entry.getKey().toString(),
                        (source == null ? "资源站" : source.getName()) + "：" + failure.getMessage()));
            }
        }
        return new CollectionBatchResponse(grouped.size(), succeeded, failures);
    }

    /**
     * 请求并解析一页搜索结果，不访问数据库，可在并发线程中执行。
     */
    private SearchedPage fetch(SourceEntity source, String keyword, int page) {
        return parser.search(client.get(client.searchUrl(source.getApiUrl(), keyword, page)), source.getSourceZone());
    }

    /**
     * 用两次批量查询标出命中影片在本库中的状态：该站已入库优先，其次是同名影片。
     */
    private SourceSearchResponse result(SourceEntity source, SearchedPage page) {
        List<String> upstreamIds = page.videos().stream().map(v -> v.video().upstreamId())
                .filter(java.util.Objects::nonNull).toList();
        Map<String, Long> collected = upstreamIds.isEmpty() ? Map.of() : store.origins.selectList(
                Wrappers.<OriginEntity>lambdaQuery().eq(OriginEntity::getSourceId, source.getId())
                        .in(OriginEntity::getUpstreamId, upstreamIds)).stream()
                .collect(Collectors.toMap(OriginEntity::getUpstreamId, OriginEntity::getVideoId, (a, b) -> a));
        List<String> names = page.videos().stream().map(v -> VideoValues.normalize(v.video().name()))
                .filter(name -> !name.isEmpty()).distinct().toList();
        List<Long> collectedVideoIds = collected.values().stream().distinct().toList();
        Map<Long, VideoEntity> videosById = collectedVideoIds.isEmpty() ? Map.of() : store.videos.selectBatchIds(collectedVideoIds)
                .stream().collect(Collectors.toMap(VideoEntity::getId, Function.identity()));
        Map<String, VideoEntity> videosByName = names.isEmpty() ? Map.of() : store.videos.selectList(
                Wrappers.<VideoEntity>lambdaQuery().in(VideoEntity::getMatchName, names).orderByAsc(VideoEntity::getId))
                .stream().collect(Collectors.toMap(VideoEntity::getMatchName, Function.identity(), (a, b) -> a));
        List<SearchItemResponse> items = page.videos().stream().map(hit -> {
            CollectedVideo v = hit.video();
            Long videoId = v.upstreamId() == null ? null : collected.get(v.upstreamId());
            VideoEntity match = videoId != null ? videosById.get(videoId)
                    : videosByName.get(VideoValues.normalize(v.name()));
            String status = videoId != null ? "COLLECTED" : match != null ? "SAME_NAME" : "NONE";
            return new SearchItemResponse(v.upstreamId(), v.name(), v.year(), hit.typeName(), v.remarks(),
                    v.lines().size(), v.lines().stream().mapToInt(l -> l.episodes().size()).max().orElse(0),
                    VideoValues.time(v.sourceUpdatedAt()), status,
                    match == null ? VideoValues.textId(videoId) : VideoValues.textId(match.getId()),
                    match == null ? null : match.getName(), v.problems());
        }).toList();
        return new SourceSearchResponse(source.getId().toString(), source.getName(), page.page(), page.pageCount(),
                page.total(), items, null);
    }

    /**
     * 构造请求失败的资源站结果。
     */
    private SourceSearchResponse failed(SourceEntity source, String error) {
        return new SourceSearchResponse(source.getId().toString(), source.getName(), 1, 0, 0, List.of(), error);
    }

    /**
     * 关键词去掉首尾空白后不能为空，长度受限。
     */
    private String keyword(String value) {
        String word = VideoValues.clean(value);
        if (word == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入搜索关键词");
        }
        if (word.codePointCount(0, word.length()) > KEYWORD_LIMIT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关键词不能超过 " + KEYWORD_LIMIT + " 字");
        }
        return word;
    }

    /**
     * 全站搜索的并发数取后台“最大并发采集数”，不超过配置上限，至少为 1。
     */
    private int concurrency() {
        Integer value = settings.get().maxConcurrency();
        int configured = value == null || value < 1 ? 1 : value;
        return Math.max(1, Math.min(configured, properties.getMaxConcurrencyLimit()));
    }
}
