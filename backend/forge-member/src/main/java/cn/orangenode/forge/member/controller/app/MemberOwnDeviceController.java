package cn.orangenode.forge.member.controller.app;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.member.response.MemberOwnDeviceResponse;
import cn.orangenode.forge.member.service.MemberOwnDeviceService;
import cn.orangenode.forge.member.support.MemberValues;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * App 我的设备接口：查看自己的登录设备并让其他设备下线。
 */
@Tag(name = "App 我的设备", description = "需要普通用户令牌；只能操作自己的设备")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app/v1/member/devices")
public class MemberOwnDeviceController {
    private final MemberOwnDeviceService service;

    /**
     * 列出自己的登录设备。
     */
    @Operation(summary = "我的设备", description = "在线设备在前；current 标记发起请求的这台设备")
    @GetMapping
    public ApiResponse<List<MemberOwnDeviceResponse>> list(HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.list(), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 让自己的另一台设备下线。
     */
    @Operation(summary = "让设备下线", description = "不是自己的设备 404；当前设备 400（请用退出登录）；已离线 409")
    @PostMapping("/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable String id, HttpServletRequest http) {
        service.offline(MemberValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
