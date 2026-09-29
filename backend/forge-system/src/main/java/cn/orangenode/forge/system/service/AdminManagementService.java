package cn.orangenode.forge.system.service;

import cn.orangenode.forge.core.page.PageResponse;
import cn.orangenode.forge.framework.page.PageRequest;
import cn.orangenode.forge.system.request.AdminCreateRequest;
import cn.orangenode.forge.system.request.AdminPasswordResetRequest;
import cn.orangenode.forge.system.request.AdminQuery;
import cn.orangenode.forge.system.request.AdminRoleAssignRequest;
import cn.orangenode.forge.system.request.AdminStatusRequest;
import cn.orangenode.forge.system.request.AdminUpdateRequest;
import cn.orangenode.forge.system.response.AdminDetailResponse;

/**
 * 管理员管理用例。
 *
 * <p>面向有权限的管理员维护账号：查询、新增、修改显示名称、启停、重置密码与分配角色。
 * 首版不开放账号删除；停用与重置密码会撤销该账号全部令牌，
 * 角色关系变更与权限缓存失效在同一业务事务内完成后触发。</p>
 */
public interface AdminManagementService {

    /**
     * 分页查询管理员。
     *
     * @param pageRequest 分页入参
     * @param query       筛选条件，允许为 {@code null}
     * @return 管理员分页结果
     */
    PageResponse<AdminDetailResponse> page(PageRequest pageRequest, AdminQuery query);

    /**
     * 查询管理员详情，含当前角色摘要。
     *
     * @param adminId 管理员 ID
     * @return 管理员详情
     */
    AdminDetailResponse detail(Long adminId);

    /**
     * 创建管理员账号。
     *
     * @param request 创建入参
     * @return 创建后的管理员详情
     */
    AdminDetailResponse create(AdminCreateRequest request);

    /**
     * 修改管理员显示名称。
     *
     * @param adminId 管理员 ID
     * @param request 修改入参
     * @return 修改后的管理员详情
     */
    AdminDetailResponse update(Long adminId, AdminUpdateRequest request);

    /**
     * 启用或停用管理员。
     *
     * <p>使用条件更新保证并发下的状态结果；停用成功后撤销该账号全部令牌，
     * 既有令牌在下一次请求即失效。</p>
     *
     * @param adminId 管理员 ID
     * @param request 状态入参
     * @return 修改后的管理员详情
     */
    AdminDetailResponse changeStatus(Long adminId, AdminStatusRequest request);

    /**
     * 重置管理员密码。
     *
     * <p>只保存编码结果，成功后撤销该账号全部令牌，不返回任何密码相关内容。</p>
     *
     * @param adminId 管理员 ID
     * @param request 密码重置入参
     */
    void resetPassword(Long adminId, AdminPasswordResetRequest request);

    /**
     * 全量替换管理员的角色。
     *
     * <p>关系表物理删除后重建：提交的角色集合即最终结果，不会出现重复插入；
     * 变更成功后使全部权限缓存立即失效。</p>
     *
     * @param adminId 管理员 ID
     * @param request 角色分配入参
     * @return 修改后的管理员详情
     */
    AdminDetailResponse replaceRoles(Long adminId, AdminRoleAssignRequest request);
}
