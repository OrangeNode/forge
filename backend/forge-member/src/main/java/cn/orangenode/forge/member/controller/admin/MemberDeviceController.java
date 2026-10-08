package cn.orangenode.forge.member.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.audit.AuditResourceId;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.member.response.MemberDeviceResponse;
import cn.orangenode.forge.member.service.MemberManagementService;
import cn.orangenode.forge.member.support.MemberValues;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 后台普通用户登录设备接口。
 */
@Tag(name = "会员登录设备", description = "查看普通用户登录设备并强制下线")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/member")
public class MemberDeviceController {
    private final MemberManagementService service;

    /**
     * 分页查询登录设备。
     */
    @Operation(summary = "分页查询登录设备", description = "可按用户 ID、邮箱关键字与在线状态筛选，在线设备在前")
    @PreAuthorize("hasAnyAuthority('member:device:view', 'member:user:view')")
    @GetMapping("/devices")
    public ApiResponse<PageResponse<MemberDeviceResponse>> devices(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String email, @RequestParam(required = false) Boolean online,
            HttpServletRequest http) {
        Long member = memberId == null || memberId.isBlank() ? null : MemberValues.id(memberId);
        return ApiResponse.successWithTrace(service.devices(pageNum, pageSize, member, email, online),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 强制单个设备下线。
     */
    @Operation(summary = "强制设备下线", description = "设备不存在 404；已离线 409；该设备令牌立即失效")
    @PreAuthorize("hasAuthority('member:device:offline')")
    @AuditOperation(action = "member:device:offline", resourceType = "member_device")
    @PostMapping("/devices/{id}/offline")
    public ApiResponse<Void> offline(@PathVariable @AuditResourceId String id, HttpServletRequest http) {
        service.forceOffline(MemberValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 强制普通用户全部设备下线。
     */
    @Operation(summary = "强制用户全部设备下线", description = "用户不存在 404；该用户此前签发的全部令牌立即失效")
    @PreAuthorize("hasAuthority('member:device:offline')")
    @AuditOperation(action = "member:device:offline", resourceType = "member_user")
    @PostMapping("/users/{id}/offline")
    public ApiResponse<Void> offlineAll(@PathVariable @AuditResourceId String id, HttpServletRequest http) {
        service.forceOfflineAll(MemberValues.id(id));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }
}
