package cn.orangenode.forge.app.controller.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.app.request.AppBannerRequest;
import cn.orangenode.forge.app.request.AppSectionRequest;
import cn.orangenode.forge.app.request.AppSplashRequest;
import cn.orangenode.forge.app.response.AppBannerResponse;
import cn.orangenode.forge.app.response.AppSectionResponse;
import cn.orangenode.forge.app.response.AppSplashResponse;
import cn.orangenode.forge.app.service.AppContentService;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.audit.AuditResourceId;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 后台 App 首页内容接口：开屏、首页轮播与首页推荐位。
 */
@Tag(name = "App 管理：首页内容", description = "开屏、轮播与推荐位维护；图片先通过文件管理上传，再提交文件 ID")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/app")
public class AppContentController {
    private final AppContentService service;

    /**
     * 查询开屏列表。
     */
    @Operation(summary = "开屏列表")
    @PreAuthorize("hasAuthority('app:splash:view')")
    @GetMapping("/splashes")
    public ApiResponse<List<AppSplashResponse>> splashes(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.splashes(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 新增开屏。
     */
    @Operation(summary = "新增开屏", description = "图片文件不存在或时间区间非法返回 400")
    @PreAuthorize("hasAuthority('app:splash:edit')")
    @AuditOperation(action = "app:splash:edit", resourceType = "app_splash")
    @PostMapping("/splashes")
    public ApiResponse<AppSplashResponse> createSplash(@Valid @RequestBody AppSplashRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveSplash(null, request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改开屏。
     */
    @Operation(summary = "修改开屏", description = "开屏不存在 404")
    @PreAuthorize("hasAuthority('app:splash:edit')")
    @AuditOperation(action = "app:splash:edit", resourceType = "app_splash")
    @PutMapping("/splashes/{id}")
    public ApiResponse<AppSplashResponse> updateSplash(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody AppSplashRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveSplash(AppValues.id(id, "开屏 ID"), request),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 删除开屏。
     */
    @Operation(summary = "删除开屏", description = "逻辑删除；开屏不存在 404")
    @PreAuthorize("hasAuthority('app:splash:delete')")
    @AuditOperation(action = "app:splash:delete", resourceType = "app_splash")
    @DeleteMapping("/splashes/{id}")
    public ApiResponse<Void> deleteSplash(@PathVariable @AuditResourceId String id, HttpServletRequest http) {
        service.deleteSplash(AppValues.id(id, "开屏 ID"));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 查询轮播列表。
     */
    @Operation(summary = "轮播列表")
    @PreAuthorize("hasAuthority('app:banner:view')")
    @GetMapping("/banners")
    public ApiResponse<List<AppBannerResponse>> banners(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.banners(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 新增轮播。
     */
    @Operation(summary = "新增轮播", description = "跳转影片或分类不存在、图片不存在返回 400")
    @PreAuthorize("hasAuthority('app:banner:edit')")
    @AuditOperation(action = "app:banner:edit", resourceType = "app_banner")
    @PostMapping("/banners")
    public ApiResponse<AppBannerResponse> createBanner(@Valid @RequestBody AppBannerRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveBanner(null, request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改轮播。
     */
    @Operation(summary = "修改轮播", description = "轮播不存在 404")
    @PreAuthorize("hasAuthority('app:banner:edit')")
    @AuditOperation(action = "app:banner:edit", resourceType = "app_banner")
    @PutMapping("/banners/{id}")
    public ApiResponse<AppBannerResponse> updateBanner(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody AppBannerRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveBanner(AppValues.id(id, "轮播 ID"), request),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 删除轮播。
     */
    @Operation(summary = "删除轮播", description = "逻辑删除；轮播不存在 404")
    @PreAuthorize("hasAuthority('app:banner:delete')")
    @AuditOperation(action = "app:banner:delete", resourceType = "app_banner")
    @DeleteMapping("/banners/{id}")
    public ApiResponse<Void> deleteBanner(@PathVariable @AuditResourceId String id, HttpServletRequest http) {
        service.deleteBanner(AppValues.id(id, "轮播 ID"));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 查询推荐位列表。
     */
    @Operation(summary = "推荐位列表")
    @PreAuthorize("hasAuthority('app:section:view')")
    @GetMapping("/sections")
    public ApiResponse<List<AppSectionResponse>> sections(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.sections(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 新增推荐位。
     */
    @Operation(summary = "新增推荐位", description = "CATEGORY 需要有效分类，MANUAL 需要至少一部有效影片，否则 400")
    @PreAuthorize("hasAuthority('app:section:edit')")
    @AuditOperation(action = "app:section:edit", resourceType = "app_section")
    @PostMapping("/sections")
    public ApiResponse<AppSectionResponse> createSection(@Valid @RequestBody AppSectionRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveSection(null, request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改推荐位。
     */
    @Operation(summary = "修改推荐位", description = "推荐位不存在 404")
    @PreAuthorize("hasAuthority('app:section:edit')")
    @AuditOperation(action = "app:section:edit", resourceType = "app_section")
    @PutMapping("/sections/{id}")
    public ApiResponse<AppSectionResponse> updateSection(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody AppSectionRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.saveSection(AppValues.id(id, "推荐位 ID"), request),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 删除推荐位。
     */
    @Operation(summary = "删除推荐位", description = "逻辑删除；推荐位不存在 404")
    @PreAuthorize("hasAuthority('app:section:delete')")
    @AuditOperation(action = "app:section:delete", resourceType = "app_section")
    @DeleteMapping("/sections/{id}")
    public ApiResponse<Void> deleteSection(@PathVariable @AuditResourceId String id, HttpServletRequest http) {
        service.deleteSection(AppValues.id(id, "推荐位 ID"));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
