package cn.orangenode.forge.video.controller.app;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.member.PlaybackAccessGuard;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.video.response.CategoryResponse;
import cn.orangenode.forge.video.response.AppVideoResponse;
import cn.orangenode.forge.video.response.PlayLineResponse;
import cn.orangenode.forge.video.service.CatalogService;
import cn.orangenode.forge.video.support.VideoValues;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * App 视频只读接口：全部要求普通用户登录，播放线路另需通过观看授权（VIP 开关开启时要求 VIP 未过期）。
 */
@Tag(name = "App 视频只读", description = "需要普通用户令牌；未登录 body.code=401，播放线路非 VIP 时 body.code=403")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/video")
public class AppVideoController {
    private final CatalogService service;
    private final PlaybackAccessGuard playbackGuard;

    /**
     * 查询可用分类树。
     */
    @Operation(summary = "查询可用视频分类")
    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> categories(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.categories(true), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 分页查询公开影片，列表不携带分集地址。
     */
    @Operation(summary = "影片列表与搜索")
    @GetMapping("/videos")
    public ApiResponse<PageResponse<AppVideoResponse>> list(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String area, @RequestParam(required = false) String language,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.appVideos(pageNum, pageSize, keyword, categoryId, year, area, language),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 查询影片公开资料。
     */
    @Operation(summary = "影片详情")
    @GetMapping("/videos/{id}")
    public ApiResponse<AppVideoResponse> detail(@PathVariable String id, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.appDetail(VideoValues.id(id)), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 查询启用线路及完整播放地址，只返回 M3U8/MP4 分集，先校验观看授权。
     */
    @Operation(summary = "播放线路与分集地址",
            description = "VIP 开关开启时要求 VIP 未过期，否则 body.code=403；只返回 M3U8/MP4 分集，没有可播分集的线路不返回")
    @GetMapping("/videos/{id}/play-lines")
    public ApiResponse<List<PlayLineResponse>> lines(@PathVariable String id, HttpServletRequest http) {
        playbackGuard.assertCanPlay();
        return ApiResponse.successWithTrace(service.lines(VideoValues.id(id), true), TraceIdFilter.currentTraceId(http));
    }
}
