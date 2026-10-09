package cn.orangenode.forge.video.controller.admin;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.video.request.SearchCollectRequest;
import cn.orangenode.forge.video.response.CollectionBatchResponse;
import cn.orangenode.forge.video.response.SourceSearchResponse;
import cn.orangenode.forge.video.service.SearchService;
import cn.orangenode.forge.video.support.VideoValues;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 资源搜索接口：按关键词搜索单个或全部启用的资源站，勾选结果后提交采集。
 */
@Tag(name = "资源搜索")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/video/search")
public class SearchController {
    private final SearchService service;

    /**
     * 并发搜索全部启用资源站的第一页，单站失败只在该站结果中标出。
     */
    @Operation(summary = "全站搜索")
    @PreAuthorize("hasAuthority('video:search:view')")
    @GetMapping
    public ApiResponse<List<SourceSearchResponse>> searchAll(@RequestParam String keyword, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.searchAll(keyword), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 搜索单个资源站的指定页。
     */
    @Operation(summary = "单站搜索")
    @PreAuthorize("hasAuthority('video:search:view')")
    @GetMapping("/sources/{id}")
    public ApiResponse<SourceSearchResponse> search(@PathVariable String id, @RequestParam String keyword,
            @RequestParam(required = false) Integer page, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.search(VideoValues.id(id), keyword, page),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 按资源站分组提交勾选采集，入库规则与范围采集一致。
     */
    @Operation(summary = "采集勾选的搜索结果")
    @PreAuthorize("hasAuthority('video:search:collect')")
    @AuditOperation(action = "video:search:collect", resourceType = "video_run")
    @PostMapping("/collect")
    public ApiResponse<CollectionBatchResponse> collect(@Valid @RequestBody SearchCollectRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.collect(request), TraceIdFilter.currentTraceId(http));
    }
}
