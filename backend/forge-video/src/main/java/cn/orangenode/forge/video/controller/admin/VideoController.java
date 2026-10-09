package cn.orangenode.forge.video.controller.admin;

import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.video.support.VideoValues;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import cn.orangenode.forge.video.service.CatalogService;
import cn.orangenode.forge.video.collector.RemoteImage;
import cn.orangenode.forge.video.request.VideoEditRequest;
import cn.orangenode.forge.video.request.StatusRequest;
import cn.orangenode.forge.video.request.PrimaryOriginRequest;
import cn.orangenode.forge.video.request.LineEditRequest;
import cn.orangenode.forge.video.response.VideoResponse;
import cn.orangenode.forge.video.response.VideoDetailResponse;

/**
 * 视频管理接口，复用管理员权限和统一响应。
 */
@Tag(name = "视频管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/video/videos")
public class VideoController {
    private final CatalogService service;

    /**
     * 视频管理：list业务操作。
     */
    @Operation(summary = "视频管理：list")
    @PreAuthorize("hasAnyAuthority('video:video:view', 'video:run:resolve')")
    @GetMapping
    public ApiResponse<PageResponse<VideoResponse>> list(@RequestParam(required = false) Integer pageNum, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) String categoryId, @RequestParam(required = false) Integer year, @RequestParam(required = false) String area, @RequestParam(required = false) String language, @RequestParam(required = false) Boolean published, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.videos(pageNum, pageSize, keyword, categoryId, year, area, language, published), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频管理：detail业务操作。
     */
    @Operation(summary = "视频管理：detail")
    @PreAuthorize("hasAuthority('video:video:view')")
    @GetMapping("/{id}")
    public ApiResponse<VideoDetailResponse> detail(@PathVariable String id, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.detail(VideoValues.id(id)), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 代取影片海报，供后台画布按模板生成轮播图；同源返回避免浏览器跨域污染画布。
     * 成功为二进制图片，失败发生在写出前，返回统一 JSON。
     */
    @Operation(summary = "影片海报", description = "服务端代取影片海报图片；影片不存在 404，没有海报 400，海报站不可用 503")
    @PreAuthorize("hasAuthority('video:video:view')")
    @GetMapping("/{id}/poster")
    public void poster(@PathVariable String id, HttpServletResponse response) throws IOException {
        RemoteImage image = service.poster(VideoValues.id(id));
        response.setContentType(image.contentType());
        response.setContentLength(image.bytes().length);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "private, no-store");
        response.getOutputStream().write(image.bytes());
    }

    /**
     * 视频管理：edit业务操作。
     */
    @Operation(summary = "视频管理：edit")
    @PreAuthorize("hasAuthority('video:video:edit')")
    @AuditOperation(action = "video:video:edit", resourceType = "video_video")
    @PutMapping("/{id}")
    public ApiResponse<VideoResponse> edit(@PathVariable String id, @Valid @RequestBody VideoEditRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.edit(VideoValues.id(id), request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频管理：status业务操作。
     */
    @Operation(summary = "视频管理：status")
    @PreAuthorize("hasAuthority('video:video:edit')")
    @AuditOperation(action = "video:video:edit", resourceType = "video_video")
    @PatchMapping("/{id}/status")
    public ApiResponse<Void> status(@PathVariable String id, @Valid @RequestBody StatusRequest request, HttpServletRequest http) {
        service.publish(VideoValues.id(id), request.enabled());
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频管理：primary业务操作。
     */
    @Operation(summary = "视频管理：primary")
    @PreAuthorize("hasAuthority('video:video:edit')")
    @AuditOperation(action = "video:video:edit", resourceType = "video_video")
    @PutMapping("/{id}/primary-origin")
    public ApiResponse<Void> primary(@PathVariable String id, @Valid @RequestBody PrimaryOriginRequest request, HttpServletRequest http) {
        service.primary(VideoValues.id(id), VideoValues.id(request.originId()));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频管理：line业务操作。
     */
    @Operation(summary = "视频管理：line")
    @PreAuthorize("hasAuthority('video:video:edit')")
    @AuditOperation(action = "video:video:edit", resourceType = "video_video")
    @PutMapping("/lines/{id}")
    public ApiResponse<Void> line(@PathVariable String id, @Valid @RequestBody LineEditRequest request, HttpServletRequest http) {
        service.editLine(VideoValues.id(id), request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频管理：delete业务操作。
     */
    @Operation(summary = "视频管理：delete")
    @PreAuthorize("hasAuthority('video:video:delete')")
    @AuditOperation(action = "video:video:delete", resourceType = "video_video")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest http) {
        service.deleteVideo(VideoValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
