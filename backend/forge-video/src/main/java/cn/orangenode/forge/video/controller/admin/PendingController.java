package cn.orangenode.forge.video.controller.admin;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.video.support.VideoValues;
import cn.orangenode.forge.video.service.IngestService;
import cn.orangenode.forge.video.request.PendingResolveRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/**
 * 待确认数据的人工处理入口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/video/pending")
public class PendingController {
    private final IngestService service;
    private final cn.orangenode.forge.video.service.CollectionBatchService batchService;

    /**
     * 批量采集或删除待确认数据，逐条返回不能执行的原因。
     */
    @Operation(summary = "批量操作待确认数据")
    @PreAuthorize("hasAuthority('video:run:resolve')")
    @AuditOperation(action = "video:run:resolve", resourceType = "video_pending")
    @PostMapping("/batch")
    public ApiResponse<cn.orangenode.forge.video.response.CollectionBatchResponse> batch(
            @Valid @RequestBody cn.orangenode.forge.video.request.CollectionBatchRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(batchService.pending(request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 确认绑定、新建或忽略来源影片。
     */
    @Operation(summary = "确认存疑采集数据")
    @PreAuthorize("hasAuthority('video:run:resolve')")
    @AuditOperation(action = "video:run:resolve", resourceType = "video_pending")
    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable String id, @Valid @RequestBody PendingResolveRequest request,
            HttpServletRequest http) {
        service.resolve(VideoValues.id(id), request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
