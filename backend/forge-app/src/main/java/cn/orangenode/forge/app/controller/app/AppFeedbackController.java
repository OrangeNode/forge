package cn.orangenode.forge.app.controller.app;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.app.request.AppFeedbackSubmitRequest;
import cn.orangenode.forge.app.service.AppFeedbackService;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * App 意见反馈与播放报错提交接口，需要普通用户令牌。
 */
@Tag(name = "App 意见反馈", description = "需要普通用户令牌；播放报错自动带影片、线路与集数")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/feedback")
public class AppFeedbackController {
    private final AppFeedbackService service;

    /**
     * 提交反馈。
     */
    @Operation(summary = "提交反馈", description = "播放报错缺少影片 400；影片不存在 404；超过每日条数 429")
    @PostMapping
    public ApiResponse<Void> submit(@Valid @RequestBody AppFeedbackSubmitRequest request, HttpServletRequest http) {
        service.submit(request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
