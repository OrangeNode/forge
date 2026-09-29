package cn.orangenode.forge.system.controller.admin;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.security.AuthenticatedAdmin;
import cn.orangenode.forge.framework.security.BearerTokens;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.system.request.AdminLoginRequest;
import cn.orangenode.forge.system.response.AdminLoginResponse;
import cn.orangenode.forge.system.response.AdminMenuResponse;
import cn.orangenode.forge.system.response.AdminProfileResponse;
import cn.orangenode.forge.system.service.AdminAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 管理员认证接口。
 *
 * <p>登录是匿名入口，其余接口都要求携带 Bearer 令牌；当前管理员身份只从安全上下文读取，
 * 不接受客户端提交的账号 ID。是否登录成功由 {@code body.code} 表达，
 * 未认证与无权限同样返回 HTTP 200。</p>
 */
@Tag(name = "管理员认证", description = "登录、退出与当前身份查询；应用可处理的响应统一为 HTTP 200，结果由 body.code 表达")
@RestController
@RequestMapping("/api/admin/v1/auth")
public class AdminAuthController {

    /**
     * 管理员认证用例。
     */
    private final AdminAuthService authService;

    /**
     * 构造管理员认证接口。
     *
     * @param authService 管理员认证用例
     */
    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    /**
     * 使用用户名与密码登录并签发令牌。
     *
     * @param request     登录入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 令牌与当前管理员基础信息
     */
    @Operation(summary = "管理员登录",
            description = "校验用户名与密码并签发不透明令牌；凭据错误返回 body.code=401，"
                    + "同一用户名失败次数超过配置上限返回 body.code=429，两者 HTTP 状态都是 200")
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(authService.login(request), TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 退出登录并撤销当前令牌。
     *
     * @param authorization Authorization 请求头，承载当前 Bearer 令牌
     * @param httpRequest   当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "退出登录", description = "撤销当前请求使用的令牌；撤销后该令牌立即失效，未认证时返回 body.code=401")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            HttpServletRequest httpRequest) {
        authService.logout(BearerTokens.resolve(authorization).orElse(null));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 查询当前管理员身份与权限。
     *
     * @param current     当前认证主体，来自安全上下文
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 身份与权限响应
     */
    @Operation(summary = "当前管理员身份", description = "返回当前管理员的 ID、用户名、显示名称与实时解析的权限代码")
    @GetMapping("/me")
    public ApiResponse<AdminProfileResponse> me(@AuthenticationPrincipal AuthenticatedAdmin current,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(authService.currentProfile(current.id()),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 查询当前管理员可见菜单。
     *
     * @param current     当前认证主体，来自安全上下文
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 菜单树
     */
    @Operation(summary = "当前管理员菜单",
            description = "按当前管理员的角色返回菜单树，routeKey 为前端本地路由标识，前端只渲染白名单内的标识")
    @GetMapping("/menus")
    public ApiResponse<List<AdminMenuResponse>> menus(@AuthenticationPrincipal AuthenticatedAdmin current,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(authService.currentMenus(current.id()),
                TraceIdFilter.currentTraceId(httpRequest));
    }
}
