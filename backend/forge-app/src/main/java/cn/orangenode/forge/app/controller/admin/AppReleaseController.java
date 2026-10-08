package cn.orangenode.forge.app.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.app.request.AppReleaseMinVersionRequest;
import cn.orangenode.forge.app.request.AppReleasePublishRequest;
import cn.orangenode.forge.app.response.AppReleaseResponse;
import cn.orangenode.forge.app.service.AppReleaseService;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 后台 App 版本更新接口：手机与电视共用一个 APK，只有一套版本配置。
 */
@Tag(name = "App 管理：版本更新", description = "上传 APK 后发布；版本号、包名与 SHA-256 自动解析，低于最低可用版本的 App 必须更新")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/app/release")
public class AppReleaseController {
    private final AppReleaseService service;

    /**
     * 读取当前版本配置。
     */
    @Operation(summary = "读取版本配置")
    @PreAuthorize("hasAuthority('app:release:view')")
    @GetMapping
    public ApiResponse<AppReleaseResponse> current(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.current(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 发布新版本。
     */
    @Operation(summary = "发布新版本", description = "包名不符、版本号不高于当前版本或文件不是 APK 时返回 400；成功后为 APK 生成公开外链")
    @PreAuthorize("hasAuthority('app:release:edit')")
    @AuditOperation(action = "app:release:edit", resourceType = "app_release")
    @PostMapping
    public ApiResponse<AppReleaseResponse> publish(@Valid @RequestBody AppReleasePublishRequest request,
            @AuthenticationPrincipal AuthenticatedAdmin current, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.publish(request, current == null ? null : current.id()),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 调整最低可用版本。
     */
    @Operation(summary = "调整最低可用版本", description = "为空表示不强制更新；不能高于最新版本号")
    @PreAuthorize("hasAuthority('app:release:edit')")
    @AuditOperation(action = "app:release:edit", resourceType = "app_release")
    @PutMapping("/min-version")
    public ApiResponse<AppReleaseResponse> updateMinVersion(@Valid @RequestBody AppReleaseMinVersionRequest request,
            @AuthenticationPrincipal AuthenticatedAdmin current, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.updateMinVersion(request, current == null ? null : current.id()),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 撤销发布。
     */
    @Operation(summary = "撤销发布", description = "清空版本配置，App 不再提示更新；APK 文件与外链保留")
    @PreAuthorize("hasAuthority('app:release:edit')")
    @AuditOperation(action = "app:release:edit", resourceType = "app_release")
    @DeleteMapping
    public ApiResponse<AppReleaseResponse> withdraw(@AuthenticationPrincipal AuthenticatedAdmin current,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.withdraw(current == null ? null : current.id()),
                TraceIdFilter.currentTraceId(http));
    }
}
