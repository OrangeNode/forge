package cn.orangenode.forge.member.controller.app;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.web.ClientAddresses;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.member.request.MemberLoginRequest;
import cn.orangenode.forge.member.request.MemberRegisterRequest;
import cn.orangenode.forge.member.response.MemberLoginResponse;
import cn.orangenode.forge.member.service.MemberAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * App 普通用户认证接口：注册、登录匿名可用，退出需要普通用户令牌；忘记密码由管理员在后台重置。
 */
@Tag(name = "App 用户认证", description = "普通用户用户名注册、登录与退出；令牌与管理员令牌互不通用")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/member/auth")
public class MemberAuthController {
    private final MemberAuthService service;

    /**
     * 用户名加激活码注册并登录当前设备。
     */
    @Operation(summary = "用户名注册", description = "用户名为 4 到 20 位字母、数字或下划线，不区分大小写唯一；已被注册 409；"
            + "必须填写激活码：不正确、已使用、已作废或已过期 400，同一来源当天输错过多 429；"
            + "未开放注册 403；同一来源注册过于频繁 429；成功后直接返回令牌")
    @PostMapping("/register")
    public ApiResponse<MemberLoginResponse> register(@Valid @RequestBody MemberRegisterRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.register(request, ClientAddresses.resolve(http)),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 用户名密码登录。
     */
    @Operation(summary = "用户名密码登录", description = "用户名或密码错误 401；账号停用 403；失败过多 429；"
            + "超过同时登录台数时踢出最早登录的设备")
    @PostMapping("/login")
    public ApiResponse<MemberLoginResponse> login(@Valid @RequestBody MemberLoginRequest request,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.login(request, ClientAddresses.resolve(http)),
                TraceIdFilter.currentTraceId(http));
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
