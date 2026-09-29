package cn.orangenode.forge.audit.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.audit.request.LoginLogQueryRequest;
import cn.orangenode.forge.audit.request.OperationLogQueryRequest;
import cn.orangenode.forge.audit.response.LoginLogResponse;
import cn.orangenode.forge.audit.response.OperationLogResponse;
import cn.orangenode.forge.audit.service.AuditLogQueryService;
import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.web.TraceIdFilter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 审计日志查询接口。
 *
 * <p>只提供只读查询：审计表只追加与按保留期限清理，不提供修改或删除接口，
 * 因此查询方法不加操作审计注解，避免制造无意义的审计噪声。</p>
 *
 * <p>两个接口分别要求权限码 {@code audit:login:view} 与 {@code audit:operation:view}；
 * 缺少权限由安全层统一写出 HTTP 200 + {@code body.code=403}，方法级拒绝与过滤链拒绝语义一致。</p>
 */
@Tag(name = "审计日志", description = "登录日志与操作日志分页查询；只读接口，响应统一为 HTTP 200，结果由 body.code 表达")
@RestController
@RequestMapping("/api/admin/v1/audit")
public class AuditLogController {

    /**
     * 审计日志查询用例。
     */
    private final AuditLogQueryService queryService;

    /**
     * 构造审计日志查询接口。
     *
     * @param queryService 审计日志查询用例
     */
    public AuditLogController(AuditLogQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 分页查询登录日志。
     *
     * @param request     分页、用户名、结果与时间范围筛选入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 登录日志分页结果
     */
    @Operation(summary = "分页查询登录日志",
            description = "按用户名（包含匹配）、结果与带时区的 ISO 8601 时间范围筛选，按创建时间倒序；"
                    + "时间格式非法返回 body.code=400，缺少 audit:login:view 权限返回 body.code=403")
    @PreAuthorize("hasAuthority('audit:login:view')")
    @GetMapping("/login-logs")
    public ApiResponse<PageResponse<LoginLogResponse>> pageLoginLogs(
            @Valid @ModelAttribute LoginLogQueryRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(queryService.pageLoginLogs(request),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 分页查询操作日志。
     *
     * @param request     分页、操作者名称、动作、结果码与时间范围筛选入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 操作日志分页结果
     */
    @Operation(summary = "分页查询操作日志",
            description = "按操作者名称（包含匹配）、动作代码、结果码与带时区的 ISO 8601 时间范围筛选，"
                    + "按创建时间倒序；时间格式非法返回 body.code=400，缺少 audit:operation:view 权限返回 body.code=403")
    @PreAuthorize("hasAuthority('audit:operation:view')")
    @GetMapping("/operation-logs")
    public ApiResponse<PageResponse<OperationLogResponse>> pageOperationLogs(
            @Valid @ModelAttribute OperationLogQueryRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(queryService.pageOperationLogs(request),
                TraceIdFilter.currentTraceId(httpRequest));
    }
}
