package cn.orangenode.forge.member.controller.app;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.web.ClientAddresses;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.member.request.MemberCodeRequest;
import cn.orangenode.forge.member.request.MemberLoginRequest;
import cn.orangenode.forge.member.request.MemberPasswordResetRequest;
import cn.orangenode.forge.member.request.MemberRegisterRequest;
import cn.orangenode.forge.member.response.MemberLoginResponse;
import cn.orangenode.forge.member.service.MemberAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * App 普通用户认证接口：发送验证码、注册、登录与找回密码匿名可用，退出需要普通用户令牌。
 */
@Tag(name = "App 用户认证", description = "普通用户邮箱验证码注册、登录、找回密码与退出；令牌与管理员令牌互不通用")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/member/auth")
public class MemberAuthController {
    private final MemberAuthService service;

    /**
     * 发送邮箱验证码。
     */
    @Operation(summary = "发送邮箱验证码", description = "注册要求开放注册且邮箱未注册（已注册 409、未开放 403）；"
            + "找回密码对未注册邮箱同样返回成功但不发信；发送过于频繁 429；发信服务未配置或发送失败 503")
    @PostMapping("/email-code")
    public ApiResponse<Void> sendCode(@Valid @RequestBody MemberCodeRequest request, HttpServletRequest http) {
        service.sendCode(request, ClientAddresses.resolve(http));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 邮箱验证码注册并登录当前设备。
     */
    @Operation(summary = "邮箱验证码注册", description = "验证码错误或失效 400；邮箱已注册 409；未开放注册 403；成功后直接返回令牌")
    @PostMapping("/register")
    public ApiResponse<MemberLoginResponse> register(@Valid @RequestBody MemberRegisterRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.register(request, ClientAddresses.resolve(http)),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 邮箱密码登录。
     */
    @Operation(summary = "邮箱密码登录", description = "邮箱或密码错误 401；账号停用 403；失败过多 429；"
            + "超过同时登录台数时踢出最早登录的设备")
    @PostMapping("/login")
    public ApiResponse<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.login(request, ClientAddresses.resolve(http)),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 邮箱验证码找回密码。
     */
    @Operation(summary = "邮箱验证码重置密码", description = "验证码错误或失效 400；成功后该账号全部令牌失效")
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody MemberPasswordResetRequest request,
            HttpServletRequest http) {
        service.resetPassword(request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 退出当前设备。
     */
    @Operation(summary = "退出登录", description = "撤销当前令牌并标记当前设备离线；未登录 401")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest http) {
        service.logout();
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
