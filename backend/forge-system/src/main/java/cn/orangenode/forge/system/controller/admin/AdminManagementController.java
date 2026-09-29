package cn.orangenode.forge.system.controller.admin;

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
import cn.orangenode.forge.framework.config.ForgePageProperties;
import cn.orangenode.forge.framework.page.PageRequest;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.system.request.AdminCreateRequest;
import cn.orangenode.forge.system.request.AdminPasswordResetRequest;
import cn.orangenode.forge.system.request.AdminQuery;
import cn.orangenode.forge.system.request.AdminRoleAssignRequest;
import cn.orangenode.forge.system.request.AdminStatusRequest;
import cn.orangenode.forge.system.request.AdminUpdateRequest;
import cn.orangenode.forge.system.response.AdminDetailResponse;
import cn.orangenode.forge.system.service.AdminManagementService;
import cn.orangenode.forge.system.support.SystemIds;
import cn.orangenode.forge.system.support.SystemPageRequests;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 管理员管理接口。
 *
 * <p>面向有权限的管理员维护账号：分页查询、详情、新增、修改显示名称、启停、重置密码与分配角色。
 * 首版不开放账号删除；当前登录管理员不能停用自己。每个写接口都声明操作审计，
 * 路径参数标注为审计对象 ID，查询接口按权限代码放行。</p>
 */
@Tag(name = "管理员管理", description = "管理员账号的查询与维护；应用可处理的响应统一为 HTTP 200，结果由 body.code 表达")
@RestController
@RequestMapping("/api/admin/v1/system/admins")
public class AdminManagementController {

    /**
     * 管理员管理用例。
     */
    private final AdminManagementService adminService;

    /**
     * 分页配置，提供默认页码、默认条数与每页上限。
     */
    private final ForgePageProperties pageProperties;

    /**
     * 构造管理员管理接口。
     *
     * @param adminService   管理员管理用例
     * @param pageProperties 分页配置
     */
    public AdminManagementController(AdminManagementService adminService, ForgePageProperties pageProperties) {
        this.adminService = adminService;
        this.pageProperties = pageProperties;
    }

    /**
     * 分页查询管理员。
     *
     * @param pageNum     页码，允许为空表示使用配置默认值
     * @param pageSize    每页条数，允许为空表示使用配置默认值
     * @param username    用户名关键字，允许为空表示不筛选
     * @param status      账号状态代码，允许为空表示不筛选
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 管理员分页结果
     */
    @Operation(summary = "分页查询管理员",
            description = "按用户名关键字与账号状态筛选；分页默认值与每页上限来自 forge.page 配置，"
                    + "越界的分页参数返回 body.code=400")
    @PreAuthorize("hasAuthority('system:admin:view')")
    @GetMapping
    public ApiResponse<PageResponse<AdminDetailResponse>> page(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String username,
            @RequestParam(required = false) String status, HttpServletRequest httpRequest) {
        PageRequest pageRequest = SystemPageRequests.resolve(pageNum, pageSize, pageProperties);
        return ApiResponse.successWithTrace(
                adminService.page(pageRequest, new AdminQuery(username, status)),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 查询管理员详情。
     *
     * @param id          管理员 ID 字符串
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 管理员详情
     */
    @Operation(summary = "查询管理员详情", description = "返回账号基础信息与当前角色摘要；账号不存在返回 body.code=404")
    @PreAuthorize("hasAuthority('system:admin:view')")
    @GetMapping("/{id}")
    public ApiResponse<AdminDetailResponse> detail(@PathVariable @AuditResourceId String id,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(adminService.detail(SystemIds.toLong(id, "管理员 ID")),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 创建管理员账号。
     *
     * @param request     创建入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 创建后的管理员详情
     */
    @Operation(summary = "新增管理员",
            description = "用户名按规范化规则处理并校验长度，密码按创建规则校验后只保存编码结果；"
                    + "用户名重复由数据库唯一约束兜底并返回 body.code=409")
    @PreAuthorize("hasAuthority('system:admin:create')")
    @AuditOperation(action = "system:admin:create", resourceType = "admin")
    @PostMapping
    public ApiResponse<AdminDetailResponse> create(@Valid @RequestBody AdminCreateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(adminService.create(request), TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 修改管理员显示名称。
     *
     * @param id          管理员 ID 字符串
     * @param request     修改入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 修改后的管理员详情
     */
    @Operation(summary = "修改管理员", description = "只允许修改显示名称；账号不存在返回 body.code=404")
    @PreAuthorize("hasAuthority('system:admin:update')")
    @AuditOperation(action = "system:admin:update", resourceType = "admin")
    @PutMapping("/{id}")
    public ApiResponse<AdminDetailResponse> update(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody AdminUpdateRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(adminService.update(SystemIds.toLong(id, "管理员 ID"), request),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 启用或停用管理员。
     *
     * @param id          管理员 ID 字符串
     * @param request     状态入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 修改后的管理员详情
     */
    @Operation(summary = "启用或停用管理员",
            description = "状态已经一致或账号不存在返回 body.code=409；不允许停用当前登录账号，"
                    + "停用成功后该账号全部令牌立即失效")
    @PreAuthorize("hasAuthority('system:admin:status')")
    @AuditOperation(action = "system:admin:status", resourceType = "admin")
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminDetailResponse> changeStatus(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody AdminStatusRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(adminService.changeStatus(SystemIds.toLong(id, "管理员 ID"), request),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 重置管理员密码。
     *
     * @param id          管理员 ID 字符串
     * @param request     密码重置入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "重置管理员密码",
            description = "新密码按创建规则校验后只保存编码结果，成功后撤销该账号全部令牌；"
                    + "响应不包含任何密码相关内容")
    @PreAuthorize("hasAuthority('system:admin:password')")
    @AuditOperation(action = "system:admin:password", resourceType = "admin")
    @PatchMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody AdminPasswordResetRequest request, HttpServletRequest httpRequest) {
        adminService.resetPassword(SystemIds.toLong(id, "管理员 ID"), request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 全量替换管理员的角色。
     *
     * @param id          管理员 ID 字符串
     * @param request     角色分配入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 修改后的管理员详情
     */
    @Operation(summary = "分配管理员角色",
            description = "提交的角色集合即最终结果，未提交的角色会被解除；关系表物理删除后重建，"
                    + "变更提交后权限缓存立即失效")
    @PreAuthorize("hasAuthority('system:admin:role')")
    @AuditOperation(action = "system:admin:role", resourceType = "admin")
    @PutMapping("/{id}/roles")
    public ApiResponse<AdminDetailResponse> replaceRoles(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody AdminRoleAssignRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(adminService.replaceRoles(SystemIds.toLong(id, "管理员 ID"), request),
                TraceIdFilter.currentTraceId(httpRequest));
    }
}
