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
import cn.orangenode.forge.video.service.SettingsService;
import cn.orangenode.forge.video.request.SettingsRequest;
import cn.orangenode.forge.video.response.SettingsResponse;

/**
 * 视频采集设置接口，复用管理员权限和统一响应。
 */
@Tag(name = "视频采集设置")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/video/settings")
public class SettingsController {
    private final SettingsService service;

    /**
     * 视频采集设置：get业务操作。
     */
    @Operation(summary = "视频采集设置：get")
    @PreAuthorize("hasAuthority('video:settings:view')")
    @GetMapping
    public ApiResponse<SettingsResponse> get(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.get(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 视频采集设置：save业务操作。
     */
    @Operation(summary = "视频采集设置：save")
    @PreAuthorize("hasAuthority('video:settings:edit')")
    @AuditOperation(action = "video:settings:edit", resourceType = "video_settings")
    @PutMapping
    public ApiResponse<SettingsResponse> save(@Valid @RequestBody SettingsRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.save(request), TraceIdFilter.currentTraceId(http));
    }
}
