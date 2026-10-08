package cn.orangenode.forge.member.controller.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.audit.AuditResourceId;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.member.request.MemberAdminPasswordRequest;
import cn.orangenode.forge.member.request.MemberStatusRequest;
import cn.orangenode.forge.member.request.MemberVipExtendRequest;
import cn.orangenode.forge.member.request.MemberVipSetRequest;
import cn.orangenode.forge.member.response.MemberUserResponse;
import cn.orangenode.forge.member.response.MemberVipLogResponse;
import cn.orangenode.forge.member.service.MemberManagementService;
import cn.orangenode.forge.member.support.MemberValues;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 后台普通用户管理接口。
 */
@Tag(name = "会员管理", description = "普通用户查询、启停、重置密码与 VIP；结果由 body.code 表达")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/member/users")
public class MemberUserController {
    private final MemberManagementService service;

    /**
     * 分页查询普通用户。
     */
    @Operation(summary = "分页查询普通用户", description = "按邮箱关键字、状态 ENABLED/DISABLED、VIP 状态 ACTIVE/EXPIRED/NONE 筛选")
    @PreAuthorize("hasAuthority('member:user:view')")
    @GetMapping
    public ApiResponse<PageResponse<MemberUserResponse>> page(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String email,
            @RequestParam(required = false) String status, @RequestParam(required = false) String vip,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.page(pageNum, pageSize, email, status, vip),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 查询普通用户详情。
     */
    @Operation(summary = "普通用户详情", description = "用户不存在 404")
    @PreAuthorize("hasAuthority('member:user:view')")
    @GetMapping("/{id}")
    public ApiResponse<MemberUserResponse> detail(@PathVariable String id, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.detail(MemberValues.id(id)), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 启停普通用户。
     */
    @Operation(summary = "启停普通用户", description = "状态未变化 409；停用后该用户全部令牌立即失效")
    @PreAuthorize("hasAuthority('member:user:status')")
    @AuditOperation(action = "member:user:status", resourceType = "member_user")
    @PatchMapping("/{id}/status")
    public ApiResponse<MemberUserResponse> status(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody MemberStatusRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.changeStatus(MemberValues.id(id), request),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 重置普通用户密码。
     */
    @Operation(summary = "重置普通用户密码", description = "重置后该用户全部令牌立即失效")
    @PreAuthorize("hasAuthority('member:user:password')")
    @AuditOperation(action = "member:user:password", resourceType = "member_user")
    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody MemberAdminPasswordRequest request, HttpServletRequest http) {
        service.resetPassword(MemberValues.id(id), request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(http));
    }

    /**
     * 开通或延长 VIP。
     */
    @Operation(summary = "开通或延长 VIP", description = "未开通或已过期从当前时刻起算，未过期在原到期时间上顺延；下一次 App 请求即生效")
    @PreAuthorize("hasAuthority('member:user:vip')")
    @AuditOperation(action = "member:user:vip", resourceType = "member_user")
    @PostMapping("/{id}/vip")
    public ApiResponse<MemberUserResponse> extendVip(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody MemberVipExtendRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.extendVip(MemberValues.id(id), request),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 直接设置或取消 VIP 到期时间。
     */
    @Operation(summary = "设置或取消 VIP", description = "expireAt 为空表示取消 VIP")
    @PreAuthorize("hasAuthority('member:user:vip')")
    @AuditOperation(action = "member:user:vip", resourceType = "member_user")
    @PutMapping("/{id}/vip")
    public ApiResponse<MemberUserResponse> setVip(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody MemberVipSetRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.setVip(MemberValues.id(id), request),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 查询 VIP 变更记录。
     */
    @Operation(summary = "VIP 变更记录", description = "按时间倒序")
    @PreAuthorize("hasAuthority('member:user:view')")
    @GetMapping("/{id}/vip-logs")
    public ApiResponse<List<MemberVipLogResponse>> vipLogs(@PathVariable String id, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.vipLogs(MemberValues.id(id)), TraceIdFilter.currentTraceId(http));
    }
}
