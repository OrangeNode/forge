package cn.orangenode.forge.system.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import cn.orangenode.forge.system.request.PermissionCreateRequest;
import cn.orangenode.forge.system.request.PermissionQuery;
import cn.orangenode.forge.system.request.PermissionUpdateRequest;
import cn.orangenode.forge.system.response.PermissionResponse;
import cn.orangenode.forge.system.service.PermissionManagementService;
import cn.orangenode.forge.system.support.SystemIds;
import cn.orangenode.forge.system.support.SystemPageRequests;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 权限管理接口。
 *
 * <p>权限代码是 {@code @PreAuthorize} 的唯一依据：创建时校验 {@code 模块:资源:动作} 格式，
 * 唯一性由数据库唯一约束兜底；修改只允许改名称与说明，被角色引用时不允许删除。
 * 任何变更提交后权限缓存立即失效。</p>
 */
@Tag(name = "权限管理", description = "权限代码的查询与维护；应用可处理的响应统一为 HTTP 200，结果由 body.code 表达")
@RestController
@RequestMapping("/api/admin/v1/system/permissions")
public class PermissionManagementController {

    /**
     * 权限管理用例。
     */
    private final PermissionManagementService permissionService;

    /**
     * 分页配置，提供默认页码、默认条数与每页上限。
     */
    private final ForgePageProperties pageProperties;

    /**
     * 构造权限管理接口。
     *
     * @param permissionService 权限管理用例
     * @param pageProperties    分页配置
     */
    public PermissionManagementController(PermissionManagementService permissionService,
            ForgePageProperties pageProperties) {
        this.permissionService = permissionService;
        this.pageProperties = pageProperties;
    }

    /**
     * 分页查询权限。
     *
     * @param pageNum     页码，允许为空表示使用配置默认值
     * @param pageSize    每页条数，允许为空表示使用配置默认值
     * @param code        权限代码关键字，允许为空表示不筛选
     * @param name        权限名称关键字，允许为空表示不筛选
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 权限分页结果
     */
    @Operation(summary = "分页查询权限", description = "按权限代码与名称关键字筛选，结果按权限代码排序")
    @PreAuthorize("hasAuthority('system:permission:view')")
    @GetMapping
    public ApiResponse<PageResponse<PermissionResponse>> page(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String code,
            @RequestParam(required = false) String name, HttpServletRequest httpRequest) {
        PageRequest pageRequest = SystemPageRequests.resolve(pageNum, pageSize, pageProperties);
        return ApiResponse.successWithTrace(permissionService.page(pageRequest, new PermissionQuery(code, name)),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 创建权限。
     *
     * @param request     创建入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 创建后的权限信息
     */
    @Operation(summary = "新增权限",
            description = "权限代码必须符合 模块:资源:动作 格式且只允许小写字母、数字、下划线与连字符，"
                    + "格式不合法返回 body.code=400，代码重复返回 body.code=409")
    @PreAuthorize("hasAuthority('system:permission:create')")
    @AuditOperation(action = "system:permission:create", resourceType = "permission")
    @PostMapping
    public ApiResponse<PermissionResponse> create(@Valid @RequestBody PermissionCreateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(permissionService.create(request),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 修改权限名称与说明。
     *
     * @param id          权限 ID 字符串
     * @param request     修改入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 修改后的权限信息
     */
    @Operation(summary = "修改权限", description = "只允许修改名称与说明，权限代码不可修改；权限不存在返回 body.code=404")
    @PreAuthorize("hasAuthority('system:permission:update')")
    @AuditOperation(action = "system:permission:update", resourceType = "permission")
    @PutMapping("/{id}")
    public ApiResponse<PermissionResponse> update(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody PermissionUpdateRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(permissionService.update(SystemIds.toLong(id, "权限 ID"), request),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 删除权限。
     *
     * @param id          权限 ID 字符串
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "删除权限",
            description = "已被角色授予的权限返回 body.code=409；删除为逻辑删除，权限代码仍占用唯一键")
    @PreAuthorize("hasAuthority('system:permission:delete')")
    @AuditOperation(action = "system:permission:delete", resourceType = "permission")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @AuditResourceId String id, HttpServletRequest httpRequest) {
        permissionService.delete(SystemIds.toLong(id, "权限 ID"));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }
}
