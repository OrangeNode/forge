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
import cn.orangenode.forge.video.request.CollectRequest;
import cn.orangenode.forge.video.request.TaskRequest;
import cn.orangenode.forge.video.response.TaskResponse;
import cn.orangenode.forge.video.response.RunResponse;
import cn.orangenode.forge.video.response.UrlResponse;

/**
 * 采集任务接口，复用管理员权限和统一响应。
 */
@Tag(name = "采集任务")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/video/tasks")
public class TaskController {
    private final CollectionService service;

    /**
     * 采集任务：list业务操作。
     */
    @Operation(summary = "采集任务：list")
    @PreAuthorize("hasAuthority('video:task:view')")
    @GetMapping
    public ApiResponse<List<TaskResponse>> list(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.tasks(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集任务：create业务操作。
     */
    @Operation(summary = "采集任务：create")
    @PreAuthorize("hasAuthority('video:task:edit')")
    @AuditOperation(action = "video:task:edit", resourceType = "video_task")
    @PostMapping
    public ApiResponse<TaskResponse> create(@Valid @RequestBody TaskRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveTask(null, request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集任务：update业务操作。
     */
    @Operation(summary = "采集任务：update")
    @PreAuthorize("hasAuthority('video:task:edit')")
    @AuditOperation(action = "video:task:edit", resourceType = "video_task")
    @PutMapping("/{id}")
    public ApiResponse<TaskResponse> update(@PathVariable String id, @Valid @RequestBody TaskRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveTask(VideoValues.id(id), request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集任务：delete业务操作。
     */
    @Operation(summary = "采集任务：delete")
    @PreAuthorize("hasAuthority('video:task:delete')")
    @AuditOperation(action = "video:task:delete", resourceType = "video_task")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest http) {
        service.deleteTask(VideoValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集任务：collect业务操作。
     */
    @Operation(summary = "采集任务：collect")
    @PreAuthorize("hasAuthority('video:task:collect')")
    @AuditOperation(action = "video:task:collect", resourceType = "video_task")
    @PostMapping("/collect")
    public ApiResponse<RunResponse> collect(@Valid @RequestBody CollectRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.collect(request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 采集任务：preview业务操作。
     */
    @Operation(summary = "采集任务：preview")
    @PreAuthorize("hasAuthority('video:task:view')")
    @AuditOperation(action = "video:task:view", resourceType = "video_task")
    @PostMapping("/preview")
    public ApiResponse<UrlResponse> preview(@Valid @RequestBody CollectRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(new UrlResponse(service.preview(request)), TraceIdFilter.currentTraceId(http));
    }
}
