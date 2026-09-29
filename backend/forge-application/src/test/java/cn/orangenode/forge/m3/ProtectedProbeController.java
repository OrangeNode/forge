package cn.orangenode.forge.m3;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;

/**
 * 权限校验探针接口。
 *
 * <p>属于测试夹具：M3 交付的认证接口只要求认证，不体现“已认证但缺权限”的 403 语义，
 * 因此用探针接口验证方法级权限注解与权限代码解析的真实效果，不进入生产模块。</p>
 */
@RestController
@RequestMapping("/api/admin/v1/m3/probe")
public class ProtectedProbeController {

    /**
     * 只要求认证并回显当前认证主体，用于验证身份来自安全上下文。
     *
     * @param current 当前认证主体
     * @return 认证主体信息
     */
    @GetMapping("/principal")
    public ApiResponse<AuthenticatedAdmin> principal(@AuthenticationPrincipal AuthenticatedAdmin current) {
        return ApiResponse.ok(current);
    }

    /**
     * 要求指定权限代码，用于验证有权限与无权限两种结果。
     *
     * @return 固定成功响应
     */
    @GetMapping("/permission")
    @PreAuthorize("hasAuthority('" + M3TestSupport.PROBE_PERMISSION + "')")
    public ApiResponse<String> permission() {
        return ApiResponse.ok("probe-ok");
    }
}
