package cn.orangenode.forge.app.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.app.request.AppFeatureRequest;
import cn.orangenode.forge.app.request.AppHotSearchRequest;
import cn.orangenode.forge.app.request.AppMarqueeRequest;
import cn.orangenode.forge.app.response.AppFeatureResponse;
import cn.orangenode.forge.app.response.AppHotSearchResponse;
import cn.orangenode.forge.app.response.AppMarqueeResponse;
import cn.orangenode.forge.app.service.AppSettingsService;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 后台 App 全局设置接口：功能开关、热搜与播放页滚动文字。
 */
@Tag(name = "App 管理：功能、热搜与滚动文字", description = "VIP 开关、注册开关、同时登录台数、客服公告、热搜词与播放页滚动文字")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/app")
public class AppSettingsController {
    private final AppSettingsService service;

    /**
     * 读取功能开关。
     */
    @Operation(summary = "读取功能开关")
    @PreAuthorize("hasAuthority('app:feature:view')")
    @GetMapping("/features")
    public ApiResponse<AppFeatureResponse> features(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.features(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改功能开关。
     */
    @Operation(summary = "修改功能开关", description = "VIP 开关与注册开关下一次请求即生效；同时登录台数在下一次登录时生效")
    @PreAuthorize("hasAuthority('app:feature:edit')")
    @AuditOperation(action = "app:feature:edit", resourceType = "app_settings")
    @PutMapping("/features")
    public ApiResponse<AppFeatureResponse> updateFeatures(@Valid @RequestBody AppFeatureRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.updateFeatures(request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 读取热搜设置。
     */
    @Operation(summary = "读取热搜设置")
    @PreAuthorize("hasAuthority('app:hotsearch:view')")
    @GetMapping("/hot-search")
    public ApiResponse<AppHotSearchResponse> hotSearch(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.hotSearch(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改热搜设置。
     */
    @Operation(summary = "修改热搜设置", description = "空白词忽略、重复词去重；包含屏蔽词的热搜词不返回给 App")
    @PreAuthorize("hasAuthority('app:hotsearch:edit')")
    @AuditOperation(action = "app:hotsearch:edit", resourceType = "app_settings")
    @PutMapping("/hot-search")
    public ApiResponse<AppHotSearchResponse> updateHotSearch(@Valid @RequestBody AppHotSearchRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.updateHotSearch(request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 读取播放页滚动文字。
     */
    @Operation(summary = "读取播放页滚动文字")
    @PreAuthorize("hasAuthority('app:marquee:view')")
    @GetMapping("/marquee")
    public ApiResponse<AppMarqueeResponse> marquee(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.marquee(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改播放页滚动文字。
     */
    @Operation(summary = "修改播放页滚动文字", description = "启用时文字不能为空；颜色为 #RRGGBB；App 下一次打开播放页生效")
    @PreAuthorize("hasAuthority('app:marquee:edit')")
    @AuditOperation(action = "app:marquee:edit", resourceType = "app_settings")
    @PutMapping("/marquee")
    public ApiResponse<AppMarqueeResponse> updateMarquee(@Valid @RequestBody AppMarqueeRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.updateMarquee(request), TraceIdFilter.currentTraceId(http));
    }
}
