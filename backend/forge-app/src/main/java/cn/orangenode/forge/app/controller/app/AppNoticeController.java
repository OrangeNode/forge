package cn.orangenode.forge.app.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.app.request.AppAnnouncementReadRequest;
import cn.orangenode.forge.app.response.AppAnnouncementPopupResponse;
import cn.orangenode.forge.app.response.AppMarqueeResponse;
import cn.orangenode.forge.app.service.AppAnnouncementService;
import cn.orangenode.forge.app.service.AppPublicService;
import cn.orangenode.forge.app.support.AppValues;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.member.CurrentMember;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * App 公告弹窗与播放页滚动文字接口，需要普通用户令牌。
 */
@Tag(name = "App 公告与提示", description = "需要普通用户令牌，未登录 body.code=401")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/app")
public class AppNoticeController {
    private final AppAnnouncementService announcements;
    private final AppPublicService service;

    /**
     * 读取进入首页时要弹出的公告。
     */
    @Operation(summary = "首页弹窗公告", description = "生效中且当前用户没看过当前版本的公告里优先级最高的一条；没有时 data 为空")
    @GetMapping("/announcement")
    public ApiResponse<AppAnnouncementPopupResponse> announcement(HttpServletRequest http) {
        return ApiResponse.successWithTrace(announcements.popup(CurrentMember.require().id()).orElse(null),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 上报已看过公告。
     */
    @Operation(summary = "公告已读", description = "关闭弹窗时上报弹出时的版本，之后不再弹出该版本；公告不存在 404")
    @PostMapping("/announcement/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id, @Valid @RequestBody AppAnnouncementReadRequest request,
            HttpServletRequest http) {
        announcements.markRead(CurrentMember.require().id(), AppValues.id(id, "公告 ID"), request.revision());
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 读取播放页滚动文字。
     */
    @Operation(summary = "播放页滚动文字", description = "未启用时 data 为空")
    @GetMapping("/player-marquee")
    public ApiResponse<AppMarqueeResponse> playerMarquee(HttpServletRequest http) {
        CurrentMember.require();
        return ApiResponse.successWithTrace(service.playerMarquee(), TraceIdFilter.currentTraceId(http));
    }
}
