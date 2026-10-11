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

import cn.orangenode.forge.app.request.AppAnnouncementRequest;
import cn.orangenode.forge.app.response.AppAnnouncementResponse;
import cn.orangenode.forge.app.service.AppAnnouncementService;
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
 * 后台 App 公告接口。
 */
@Tag(name = "App 管理：公告", description = "App 进入首页弹出的公告；HTML 内容保存前按白名单过滤")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/app/announcements")
public class AppAnnouncementController {
    private final AppAnnouncementService service;

    /**
     * 查询公告列表。
     */
    @Operation(summary = "公告列表")
    @PreAuthorize("hasAuthority('app:announcement:view')")
    @GetMapping
    public ApiResponse<List<AppAnnouncementResponse>> list(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.announcements(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 新增公告。
     */
    @Operation(summary = "新增公告", description = "时间区间非法、跳转影片不存在、链接不是 http(s) 或 HTML 过滤后为空返回 400")
    @PreAuthorize("hasAuthority('app:announcement:edit')")
    @AuditOperation(action = "app:announcement:edit", resourceType = "app_announcement")
    @PostMapping
    public ApiResponse<AppAnnouncementResponse> create(@Valid @RequestBody AppAnnouncementRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.save(null, request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改公告。
     */
    @Operation(summary = "修改公告", description = "标题、内容或按钮变化时内容版本加一，已看过的用户会再看到一次；公告不存在 404")
    @PreAuthorize("hasAuthority('app:announcement:edit')")
    @AuditOperation(action = "app:announcement:edit", resourceType = "app_announcement")
    @PutMapping("/{id}")
    public ApiResponse<AppAnnouncementResponse> update(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody AppAnnouncementRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.save(AppValues.id(id, "公告 ID"), request),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 删除公告。
     */
    @Operation(summary = "删除公告", description = "逻辑删除；公告不存在 404")
    @PreAuthorize("hasAuthority('app:announcement:delete')")
    @AuditOperation(action = "app:announcement:delete", resourceType = "app_announcement")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @AuditResourceId String id, HttpServletRequest http) {
        service.delete(AppValues.id(id, "公告 ID"));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
