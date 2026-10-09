package cn.orangenode.forge.member.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.web.ClientAddresses;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.member.request.MemberActivationRedeemRequest;
import cn.orangenode.forge.member.request.MemberPasswordChangeRequest;
import cn.orangenode.forge.member.request.MemberProfileUpdateRequest;
import cn.orangenode.forge.member.response.MemberProfileResponse;
import cn.orangenode.forge.member.service.MemberProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * App 当前用户资料接口，全部需要普通用户令牌。
 */
@Tag(name = "App 个人资料", description = "需要普通用户令牌；未登录 body.code=401")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/member/profile")
public class MemberProfileController {
    private final MemberProfileService service;

    /**
     * 读取当前用户资料与 VIP 状态。
     */
    @Operation(summary = "当前用户资料", description = "返回昵称、头像、VIP 是否有效、到期时间及当前是否只有 VIP 能观看")
    @GetMapping
    public ApiResponse<MemberProfileResponse> current(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.current(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改昵称与头像。
     */
    @Operation(summary = "修改个人资料", description = "只允许修改昵称与头像地址")
    @PutMapping
    public ApiResponse<MemberProfileResponse> update(@Valid @RequestBody MemberProfileUpdateRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.update(request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 用激活码续费 VIP。
     */
    @Operation(summary = "激活码续费", description = "时长接在当前 VIP 到期时间之后，已过期从现在起算；激活码不正确、已使用、"
            + "已作废或不含会员时长 400；同一来源地址当天输错过多 429；并发抢用同一码时后到者 409")
    @PostMapping("/activation")
    public ApiResponse<MemberProfileResponse> redeem(@Valid @RequestBody MemberActivationRedeemRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.redeem(request, ClientAddresses.resolve(http)),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 修改密码。
     */
    @Operation(summary = "修改密码", description = "原密码错误 400；成功后全部令牌失效，需要重新登录")
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody MemberPasswordChangeRequest request,
            HttpServletRequest http) {
        service.changePassword(request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
