package cn.orangenode.forge.member.controller.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.audit.AuditResourceId;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.member.request.MemberActivationCreateRequest;
import cn.orangenode.forge.member.response.MemberActivationCodeResponse;
import cn.orangenode.forge.member.service.MemberActivationService;
import cn.orangenode.forge.member.support.MemberValues;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 后台注册激活码接口。
 */
@Tag(name = "注册激活码", description = "批量生成、查询与作废注册激活码，查看每个码被哪个账号使用")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/v1/member/activation-codes")
public class MemberActivationController {
    private final MemberActivationService service;

    /**
     * 分页查询激活码。
     */
    @Operation(summary = "分页查询激活码", description = "keyword 匹配激活码或使用者用户名；status 为 UNUSED/USED/REVOKED/EXPIRED；"
            + "按生成时间倒序")
    @PreAuthorize("hasAuthority('member:activation:view')")
    @GetMapping
    public ApiResponse<PageResponse<MemberActivationCodeResponse>> page(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status, @RequestParam(required = false) String remark,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.page(pageNum, pageSize, keyword, status, remark),
                TraceIdFilter.currentTraceId(http));
    }

    /**
     * 批量生成激活码。
     */
    @Operation(summary = "批量生成激活码", description = "数量超过上限或有效期早于当前时间 400；返回新生成的激活码")
    @PreAuthorize("hasAuthority('member:activation:create')")
    @AuditOperation(action = "member:activation:create", resourceType = "member_activation_code")
    @PostMapping
    public ApiResponse<List<MemberActivationCodeResponse>> create(
            @Valid @RequestBody MemberActivationCreateRequest request, HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.create(request), TraceIdFilter.currentTraceId(http));
    }

    /**
     * 作废激活码。
     */
    @Operation(summary = "作废激活码", description = "激活码不存在 404；已使用或已作废 409；作废后不能再用于注册")
    @PreAuthorize("hasAuthority('member:activation:revoke')")
    @AuditOperation(action = "member:activation:revoke", resourceType = "member_activation_code")
    @PostMapping("/{id}/revoke")
    public ApiResponse<MemberActivationCodeResponse> revoke(@PathVariable @AuditResourceId String id,
            HttpServletRequest http) {
        return ApiResponse.successWithTrace(service.revoke(MemberValues.id(id)), TraceIdFilter.currentTraceId(http));
    }
}
