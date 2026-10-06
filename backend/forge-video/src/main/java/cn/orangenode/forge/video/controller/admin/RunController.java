package cn.orangenode.forge.video.controller.admin;

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
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import cn.orangenode.forge.video.service.CollectionService;
import cn.orangenode.forge.video.response.RunResponse;
import cn.orangenode.forge.video.response.PendingResponse;

/**
 * 采集记录接口，复用管理员权限和统一响应。
 */
@Tag(name = "采集记录")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/video/runs")
public class RunController {
    private final CollectionService service;
    private final cn.orangenode.forge.video.service.CollectionBatchService batchService;

    /**
     * 批量重新采集、停止或删除勾选记录和全部筛选结果。
     */
    @Operation(summary = "批量操作运行记录")
    @PreAuthorize("hasAuthority('video:run:operate')")
    @AuditOperation(action = "video:run:operate", resourceType = "video_run")
    @PostMapping("/batch")
    public ApiResponse<cn.orangenode.forge.video.response.CollectionBatchResponse> batch(
            @Valid @RequestBody cn.orangenode.forge.video.request.CollectionBatchRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(batchService.runs(request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集记录：list业务操作。
     */
    @Operation(summary = "采集记录：list")
    @PreAuthorize("hasAuthority('video:run:view')")
    @GetMapping
    public ApiResponse<PageResponse<RunResponse>> list(@RequestParam(required = false) Integer pageNum, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String status, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.runs(pageNum, pageSize, status), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集记录：get业务操作。
     */
    @Operation(summary = "采集记录：get")
    @PreAuthorize("hasAuthority('video:run:view')")
    @GetMapping("/{id}")
    public ApiResponse<RunResponse> get(@PathVariable String id, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.run(VideoValues.id(id)), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集记录：stop业务操作。
     */
    @Operation(summary = "采集记录：stop")
    @PreAuthorize("hasAuthority('video:run:operate')")
    @AuditOperation(action = "video:run:operate", resourceType = "video_run")
    @PostMapping("/{id}/stop")
    public ApiResponse<Void> stop(@PathVariable String id, HttpServletRequest http) {
        service.stop(VideoValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集记录：resume业务操作。
     */
    @Operation(summary = "采集记录：resume")
    @PreAuthorize("hasAuthority('video:run:operate')")
    @AuditOperation(action = "video:run:operate", resourceType = "video_run")
    @PostMapping("/{id}/resume")
    public ApiResponse<Void> resume(@PathVariable String id, HttpServletRequest http) {
        service.resume(VideoValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集记录：pending业务操作。
     */
    @Operation(summary = "采集记录：pending")
    @PreAuthorize("hasAuthority('video:run:view')")
    @GetMapping("/pending")
    public ApiResponse<PageResponse<PendingResponse>> pending(@RequestParam(required = false) Integer pageNum, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String status, @RequestParam(required = false) String runId, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.pending(pageNum, pageSize, status, runId), TraceIdFilter.currentTraceId(http));
    }
}
