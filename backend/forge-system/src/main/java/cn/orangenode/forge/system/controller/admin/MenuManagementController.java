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
import org.springframework.web.bind.annotation.RestController;

import cn.orangenode.forge.core.response.ApiResponse;
import cn.orangenode.forge.framework.audit.AuditOperation;
import cn.orangenode.forge.framework.audit.AuditResourceId;
import cn.orangenode.forge.framework.web.TraceIdFilter;
import cn.orangenode.forge.system.request.MenuCreateRequest;
import cn.orangenode.forge.system.request.MenuUpdateRequest;
import cn.orangenode.forge.system.response.MenuNodeResponse;
import cn.orangenode.forge.system.service.MenuManagementService;
import cn.orangenode.forge.system.support.SystemIds;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * 菜单管理接口。
 *
 * <p>菜单只描述前端可见性：{@code routeKey} 指向前端本地路由白名单中的标识，目录节点为空，
 * 后端不下发组件路径或组件代码。存在子菜单或已被角色授予可见性的菜单不允许删除。</p>
 */
@Tag(name = "菜单管理", description = "菜单树查询与节点维护；应用可处理的响应统一为 HTTP 200，结果由 body.code 表达")
@RestController
@RequestMapping("/api/admin/v1/system/menus")
public class MenuManagementController {

    /**
     * 菜单管理用例。
     */
    private final MenuManagementService menuService;

    /**
     * 构造菜单管理接口。
     *
     * @param menuService 菜单管理用例
     */
    public MenuManagementController(MenuManagementService menuService) {
        this.menuService = menuService;
    }

    /**
     * 查询完整菜单树。
     *
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 菜单树
     */
    @Operation(summary = "查询菜单树", description = "返回全部有效菜单并按父子关系组装为树，节点含 ID、父 ID、名称、路由标识与排序号")
    @PreAuthorize("hasAuthority('system:menu:view')")
    @GetMapping
    public ApiResponse<List<MenuNodeResponse>> tree(HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(menuService.tree(), TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 创建菜单节点。
     *
     * @param request     创建入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 创建后的菜单节点
     */
    @Operation(summary = "新增菜单",
            description = "父菜单为空或 0 表示顶级；路由标识唯一冲突返回 body.code=409；"
                    + "菜单可见性变更提交后权限缓存立即失效")
    @PreAuthorize("hasAuthority('system:menu:create')")
    @AuditOperation(action = "system:menu:create", resourceType = "menu")
    @PostMapping
    public ApiResponse<MenuNodeResponse> create(@Valid @RequestBody MenuCreateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(menuService.create(request), TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 修改菜单节点。
     *
     * @param id          菜单 ID 字符串
     * @param request     修改入参
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 修改后的菜单节点
     */
    @Operation(summary = "修改菜单",
            description = "父菜单不能是菜单自身或其子菜单，否则返回 body.code=400；路由标识唯一冲突返回 body.code=409")
    @PreAuthorize("hasAuthority('system:menu:update')")
    @AuditOperation(action = "system:menu:update", resourceType = "menu")
    @PutMapping("/{id}")
    public ApiResponse<MenuNodeResponse> update(@PathVariable @AuditResourceId String id,
            @Valid @RequestBody MenuUpdateRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.successWithTrace(menuService.update(SystemIds.toLong(id, "菜单 ID"), request),
                TraceIdFilter.currentTraceId(httpRequest));
    }

    /**
     * 删除菜单节点。
     *
     * @param id          菜单 ID 字符串
     * @param httpRequest 当前 HTTP 请求，用于读取追踪编号
     * @return 不含业务数据的成功响应
     */
    @Operation(summary = "删除菜单",
            description = "存在子菜单或已被角色授予可见性时返回 body.code=409；删除为逻辑删除，路由标识仍占用唯一键")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    @AuditOperation(action = "system:menu:delete", resourceType = "menu")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @AuditResourceId String id, HttpServletRequest httpRequest) {
        menuService.delete(SystemIds.toLong(id, "菜单 ID"));
        return ApiResponse.successWithTrace(null, TraceIdFilter.currentTraceId(httpRequest));
    }
}
