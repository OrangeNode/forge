package cn.orangenode.forge.app.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.app.request.AppFeedbackHandleRequest;
import cn.orangenode.forge.app.response.AppFeedbackResponse;
import cn.orangenode.forge.app.service.AppFeedbackService;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.page.PageResponse;
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
 * 后台反馈处理接口。
 */
@Tag(name = "App 反馈处理", description = "查看意见反馈与播放报错，标记已处理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/app/feedbacks")
public class AppFeedbackAdminController {
    private final AppFeedbackService service;

    /**
     * 分页查询反馈。
     */
    @Operation(summary = "分页查询反馈", description = "可按状态 PENDING/RESOLVED 与类型 PLAYBACK/GENERAL 筛选，待处理在前")
    @PreAuthorize("hasAuthority('app:feedback:view')")
    @GetMapping
    public ApiResponse<PageResponse<AppFeedbackResponse>> page(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String status,
            @RequestParam(required = false) String type, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.page(pageNum, pageSize, status, type),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 标记已处理。
     */
    @Operation(summary = "标记已处理", description = "反馈不存在 404；已处理 409")
    @PreAuthorize("hasAuthority('app:feedback:handle')")
    @AuditOperation(action = "app:feedback:handle", resourceType = "app_feedback")
    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody(required = false) AppFeedbackHandleRequest request, HttpServletRequest http) {
        service.resolve(AppValues.id(id, "反馈"), request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
