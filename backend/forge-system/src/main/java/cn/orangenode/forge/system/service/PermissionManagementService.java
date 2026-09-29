package cn.orangenode.forge.system.service;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.framework.page.PageRequest;
import cn.orangenode.forge.system.request.PermissionCreateRequest;
import cn.orangenode.forge.system.request.PermissionQuery;
import cn.orangenode.forge.system.request.PermissionUpdateRequest;
import cn.orangenode.forge.system.response.PermissionResponse;

/**
 * 权限管理用例。
 *
 * <p>权限代码是 {@code @PreAuthorize} 的唯一依据，格式为 {@code 模块:资源:动作}：
 * 创建时校验格式并由数据库唯一约束兜底唯一性，修改只允许改名称与说明，
 * 被角色引用时不允许删除。任何变更成功后使全部权限缓存立即失效。</p>
 */
public interface PermissionManagementService {

    /**
     * 分页查询权限。
     *
     * @param pageRequest 分页入参
     * @param query       筛选条件，允许为 {@code null}
     * @return 权限分页结果
     */
    PageResponse<PermissionResponse> page(PageRequest pageRequest, PermissionQuery query);

    /**
     * 创建权限。
     *
     * @param request 创建入参
     * @return 创建后的权限信息
     */
    PermissionResponse create(PermissionCreateRequest request);

    /**
     * 修改权限名称与说明。
     *
     * @param permissionId 权限 ID
     * @param request      修改入参
     * @return 修改后的权限信息
     */
    PermissionResponse update(Long permissionId, PermissionUpdateRequest request);

    /**
     * 逻辑删除权限。
     *
     * @param permissionId 权限 ID
     */
    void delete(Long permissionId);
}
