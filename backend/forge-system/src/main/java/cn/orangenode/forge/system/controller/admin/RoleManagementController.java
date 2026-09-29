package cn.orangenode.forge.system.controller.admin;

import java.util.List;

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
import cn.orangenode.forge.system.request.RoleCreateRequest;
import cn.orangenode.forge.system.request.RoleGrantRequest;
import cn.orangenode.forge.system.request.RoleQuery;
import cn.orangenode.forge.system.request.RoleUpdateRequest;
import cn.orangenode.forge.system.response.RoleDetailResponse;
import cn.orangenode.forge.system.response.RoleOptionResponse;
import cn.orangenode.forge.system.service.RoleManagementService;
import cn.orangenode.forge.system.support.SystemIds;
import cn.orangenode.forge.system.support.SystemPageRequests;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 角色管理接口。
 *
 * <p>角色是权限与菜单可见性的载体：被管理员引用时不允许删除，授权为权限与菜单的全量替换。
 * 内置超级管理员角色不允许删除或修改代码；授权变更提交后权限缓存立即失效。</p>
 */
@Tag(name = "角色管理", description = "角色查询、维护与授权；应用可处理的响应统一为 HTTP 200，结果由 body.code 表达")
@RestController
@RequestMapping("/api/admin/v1/system/roles")
public class RoleManagementController {

    /**
     * 角色管理用例。
     */
    private final RoleManagementService roleService;

    /**
     * 分页配置，提供默认页码、默认条数与每页上限。
     */
    private final ForgePageProperties pageProperties;

    /**
     * 构造角色管理接口。
     *
     * @param roleService    角色管理用例
     * @param pageProperties 分页配置
     */
    public RoleManagementController(RoleManagementService roleService, ForgePageProperties pageProperties) {
        this.roleService = roleService;
        this.pageProperties = pageProperties;
    }

    /**
     * 分页查询角色。
     *
     * @param pageNum     页码，允许为空表示使用配置默认值
     * @param pageSize    每页条数，允许为空表示使用配置默认值
     * @param name        角色名称关键字，允许为空表示不筛选
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 角色分页结果
     */
    @Operation(summary = "分页查询角色", description = "按名称关键字筛选；列表只返回角色基础信息，授权明细由详情接口返回")
    @PreAuthorize("hasAuthority('system:role:view')")
    @GetMapping
    public ApiResponse<PageResponse<RoleDetailResponse>> page(@RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String name,
            HttpServletRequest httpRequest) {
        PageRequest pageRequest = SystemPageRequests.resolve(pageNum, pageSize, pageProperties);
        return ApiResponse.successWithTrace(roleService.page(pageRequest, new RoleQuery(name)),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 查询角色下拉选项。
     *
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 角色下拉选项列表
     */
    @Operation(summary = "查询角色下拉选项", description = "返回全部有效角色的 ID、代码与名称，按展示顺序排序")
    @PreAuthorize("hasAuthority('system:role:view')")
    @GetMapping("/options")
    public ApiResponse<List<RoleOptionResponse>> options(HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(roleService.options(), TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 查询角色详情。
     *
     * @param id          角色 ID 字符串
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 角色详情
     */
    @Operation(summary = "查询角色详情", description = "返回角色基础信息、是否为内置超级管理员角色，以及已授予的权限与菜单 ID")
    @PreAuthorize("hasAuthority('system:role:view')")
    @GetMapping("/{id}")
    public ApiResponse<RoleDetailResponse> detail(@PathVariable @AuditResourceId String id,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(roleService.detail(SystemIds.toLong(id, "角色 ID")),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 创建角色。
     *
     * @param request     创建入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 创建后的角色详情
     */
    @Operation(summary = "新增角色",
            description = "角色代码去空格并转为小写后保存，唯一性由数据库唯一约束兜底并返回 body.code=409；"
                    + "可同时提交初始权限与菜单 ID")
    @PreAuthorize("hasAuthority('system:role:create')")
    @AuditOperation(action = "system:role:create", resourceType = "role")
    @PostMapping
    public ApiResponse<RoleDetailResponse> create(@Valid @RequestBody RoleCreateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(roleService.create(request), TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 修改角色基础信息。
     *
     * @param id          角色 ID 字符串
     * @param request     修改入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 修改后的角色详情
     */
    @Operation(summary = "修改角色", description = "内置超级管理员角色的代码不允许修改，尝试修改返回 body.code=400")
    @PreAuthorize("hasAuthority('system:role:update')")
    @AuditOperation(action = "system:role:update", resourceType = "role")
    @PutMapping("/{id}")
    public ApiResponse<RoleDetailResponse> update(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody RoleUpdateRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(roleService.update(SystemIds.toLong(id, "角色 ID"), request),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 删除角色。
     *
     * @param id          角色 ID 字符串
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "删除角色",
            description = "被管理员引用的角色返回 body.code=409；内置超级管理员角色不允许删除；"
                    + "删除为逻辑删除并清理该角色的授权关系")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @AuditOperation(action = "system:role:delete", resourceType = "role")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @AuditResourceId String id, HttpServletRequest httpRequest) {
        roleService.delete(SystemIds.toLong(id, "角色 ID"));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 全量替换角色的授权。
     *
     * @param id          角色 ID 字符串
     * @param request     授权入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "角色授权",
            description = "提交的权限与菜单集合即最终结果；关系表物理删除后重建，变更提交后权限缓存立即失效")
    @PreAuthorize("hasAuthority('system:role:grant')")
    @AuditOperation(action = "system:role:grant", resourceType = "role")
    @PutMapping("/{id}/grants")
    public ApiResponse<Void> replaceGrants(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody RoleGrantRequest request, HttpServletRequest httpRequest) {
        roleService.replaceGrants(SystemIds.toLong(id, "角色 ID"), request);
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }
}
