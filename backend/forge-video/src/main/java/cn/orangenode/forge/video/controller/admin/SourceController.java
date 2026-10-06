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
import cn.orangenode.forge.video.service.SourceService;
import cn.orangenode.forge.video.request.SourceRequest;
import cn.orangenode.forge.video.request.MappingRequest;
import cn.orangenode.forge.video.response.SourceResponse;
import cn.orangenode.forge.video.response.SourceCategoryResponse;

/**
 * 资源站管理接口，复用管理员权限和统一响应。
 */
@Tag(name = "资源站管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/video/sources")
public class SourceController {
    private final SourceService service;

    /**
     * 按名称识别分类，可同时创建缺少分类并建立映射。
     */
    @Operation(summary = "自动识别并绑定分类，可创建缺少的分类")
    @PreAuthorize("hasAuthority('video:source:edit') and (!#request.createMissing() or hasAuthority('video:category:edit'))")
    @AuditOperation(action = "video:source:edit", resourceType = "video_source")
    @PostMapping("/{id}/categories/auto-map")
    public ApiResponse<cn.orangenode.forge.video.response.AutoMappingResponse> autoMap(@PathVariable String id,
            @Valid @RequestBody cn.orangenode.forge.video.request.AutoMappingRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.autoMap(VideoValues.id(id), request.createMissing()),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 资源站管理：list业务操作。
     */
    @Operation(summary = "资源站管理：list")
    @PreAuthorize("hasAnyAuthority('video:source:view', 'video:task:view')")
    @GetMapping
    public ApiResponse<List<SourceResponse>> list(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.list(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 资源站管理：create业务操作。
     */
    @Operation(summary = "资源站管理：create")
    @PreAuthorize("hasAuthority('video:source:edit')")
    @AuditOperation(action = "video:source:edit", resourceType = "video_source")
    @PostMapping
    public ApiResponse<SourceResponse> create(@Valid @RequestBody SourceRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.save(null, request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 资源站管理：update业务操作。
     */
    @Operation(summary = "资源站管理：update")
    @PreAuthorize("hasAuthority('video:source:edit')")
    @AuditOperation(action = "video:source:edit", resourceType = "video_source")
    @PutMapping("/{id}")
    public ApiResponse<SourceResponse> update(@PathVariable String id, @Valid @RequestBody SourceRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.save(VideoValues.id(id), request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 资源站管理：delete业务操作。
     */
    @Operation(summary = "资源站管理：delete")
    @PreAuthorize("hasAuthority('video:source:delete')")
    @AuditOperation(action = "video:source:delete", resourceType = "video_source")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest http) {
        service.delete(VideoValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 资源站管理：categories业务操作。
     */
    @Operation(summary = "资源站管理：categories")
    @PreAuthorize("hasAnyAuthority('video:source:view', 'video:task:view')")
    @GetMapping("/{id}/categories")
    public ApiResponse<List<SourceCategoryResponse>> categories(@PathVariable String id, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.categories(VideoValues.id(id)), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 资源站管理：test业务操作。
     */
    @Operation(summary = "资源站管理：test")
    @PreAuthorize("hasAuthority('video:source:edit')")
    @AuditOperation(action = "video:source:edit", resourceType = "video_source")
    @PostMapping("/{id}/test")
    public ApiResponse<List<SourceCategoryResponse>> test(@PathVariable String id, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.refresh(VideoValues.id(id)), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 资源站管理：refresh业务操作。
     */
    @Operation(summary = "资源站管理：refresh")
    @PreAuthorize("hasAuthority('video:source:edit')")
    @AuditOperation(action = "video:source:edit", resourceType = "video_source")
    @PostMapping("/{id}/categories/refresh")
    public ApiResponse<List<SourceCategoryResponse>> refresh(@PathVariable String id, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.refresh(VideoValues.id(id)), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 资源站管理：mapping业务操作。
     */
    @Operation(summary = "资源站管理：mapping")
    @PreAuthorize("hasAuthority('video:source:edit')")
    @AuditOperation(action = "video:source:edit", resourceType = "video_source")
    @PutMapping("/{id}/mapping")
    public ApiResponse<Void> mapping(@PathVariable String id, @Valid @RequestBody MappingRequest request, HttpServletRequest http) {
        service.map(VideoValues.id(id), request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
