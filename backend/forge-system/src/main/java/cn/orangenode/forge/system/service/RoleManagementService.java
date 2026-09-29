package cn.orangenode.forge.system.service;

import java.util.List;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.framework.page.PageRequest;
import cn.orangenode.forge.system.request.RoleCreateRequest;
import cn.orangenode.forge.system.request.RoleGrantRequest;
import cn.orangenode.forge.system.request.RoleQuery;
import cn.orangenode.forge.system.request.RoleUpdateRequest;
import cn.orangenode.forge.system.response.RoleDetailResponse;
import cn.orangenode.forge.system.response.RoleOptionResponse;

/**
 * 角色管理用例。
 *
 * <p>角色是权限与菜单可见性的载体：角色被管理员引用时不允许删除，角色与权限、角色与菜单
 * 关系为物理删除的全量替换语义。内置超级管理员角色按配置识别，不允许删除或修改代码，
 * 该角色的权限与菜单由解析规则直接给出，不依赖逐条关系。</p>
 */
public interface RoleManagementService {

    /**
     * 分页查询角色。
     *
     * @param pageRequest 分页入参
     * @param query       筛选条件，允许为 {@code null}
     * @return 角色分页结果
     */
    PageResponse<RoleDetailResponse> page(PageRequest pageRequest, RoleQuery query);

    /**
     * 查询全部角色下拉选项。
     *
     * @return 角色下拉选项列表，没有角色时返回空列表
     */
    List<RoleOptionResponse> options();

    /**
     * 查询角色详情，含已授予的权限与菜单 ID。
     *
     * @param roleId 角色 ID
     * @return 角色详情
     */
    RoleDetailResponse detail(Long roleId);

    /**
     * 创建角色，并按需写入初始授权关系。
     *
     * @param request 创建入参
     * @return 创建后的角色详情
     */
    RoleDetailResponse create(RoleCreateRequest request);

    /**
     * 修改角色基础信息。
     *
     * <p>内置超级管理员角色不允许修改代码，避免改变内置权限语义。</p>
     *
     * @param roleId  角色 ID
     * @param request 修改入参
     * @return 修改后的角色详情
     */
    RoleDetailResponse update(Long roleId, RoleUpdateRequest request);

    /**
     * 逻辑删除角色并清理其授权关系。
     *
     * @param roleId 角色 ID
     */
    void delete(Long roleId);

    /**
     * 全量替换角色的权限与菜单授权。
     *
     * <p>两张关系表都先按角色物理删除再重建，避免重复插入；变更成功后使全部权限缓存立即失效。</p>
     *
     * @param roleId  角色 ID
     * @param request 授权入参
     */
    void replaceGrants(Long roleId, RoleGrantRequest request);
}
