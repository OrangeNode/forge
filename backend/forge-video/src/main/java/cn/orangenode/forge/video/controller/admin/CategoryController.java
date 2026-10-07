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
import cn.orangenode.forge.video.service.CatalogService;
import cn.orangenode.forge.video.request.CategoryRequest;
import cn.orangenode.forge.video.request.CategoryBatchDeleteRequest;
import cn.orangenode.forge.video.response.CollectionBatchResponse;
import cn.orangenode.forge.video.response.CategoryResponse;

/**
 * 视频分类接口，复用管理员权限和统一响应。
 */
@Tag(name = "视频分类")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/video/categories")
public class CategoryController {
    private final CatalogService service;

    /**
     * 视频分类：tree业务操作。
     */
    @Operation(summary = "视频分类：tree")
    @PreAuthorize("hasAnyAuthority('video:category:view', 'video:source:view', 'video:video:view', 'video:run:resolve')")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> tree(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.categories(false), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频分类：create业务操作。
     */
    @Operation(summary = "视频分类：create")
    @PreAuthorize("hasAuthority('video:category:edit')")
    @AuditOperation(action = "video:category:edit", resourceType = "video_category")
    @PostMapping
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveCategory(null, request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频分类：update业务操作。
     */
    @Operation(summary = "视频分类：update")
    @PreAuthorize("hasAuthority('video:category:edit')")
    @AuditOperation(action = "video:category:edit", resourceType = "video_category")
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> update(@PathVariable String id, @Valid @RequestBody CategoryRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveCategory(VideoValues.id(id), request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频分类：delete业务操作。
     */
    @Operation(summary = "视频分类：delete")
    @PreAuthorize("hasAuthority('video:category:delete')")
    @AuditOperation(action = "video:category:delete", resourceType = "video_category")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest http) {
        service.deleteCategory(VideoValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 批量删除勾选分类，子分类先删；被影片或子分类引用的条目逐条返回原因。
     */
    @Operation(summary = "视频分类：批量删除")
    @PreAuthorize("hasAuthority('video:category:delete')")
    @AuditOperation(action = "video:category:delete", resourceType = "video_category")
    @PostMapping("/batch-delete")
    public ApiResponse<CollectionBatchResponse> batchDelete(@Valid @RequestBody CategoryBatchDeleteRequest request,
            HttpServletRequest http) {
        List<Long> ids = request.ids().stream().map(VideoValues::id).toList();
        return ApiResponse.successWithTrace(service.deleteCategories(ids), TraceIdFilter.currentTraceId(http));
    }
}
