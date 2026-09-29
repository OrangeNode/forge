package cn.orangenode.forge.system.service;

import java.util.List;

import cn.orangenode.forge.system.request.MenuCreateRequest;
import cn.orangenode.forge.system.request.MenuUpdateRequest;
import cn.orangenode.forge.system.response.MenuNodeResponse;

/**
 * 菜单管理用例。
 *
 * <p>菜单只描述前端可见性：{@code routeKey} 是前端本地路由白名单中的标识，目录节点为空。
 * 菜单被角色引用或存在子菜单时不允许删除，不做静默级联；
 * 菜单可见性变更后使全部权限缓存立即失效，使授权调整在下一次请求生效。</p>
 */
public interface MenuManagementService {

    /**
     * 查询完整菜单树。
     *
     * @return 菜单树，没有菜单时返回空列表
     */
    List<MenuNodeResponse> tree();

    /**
     * 创建菜单节点。
     *
     * @param request 创建入参
     * @return 创建后的菜单节点，不含子菜单
     */
    MenuNodeResponse create(MenuCreateRequest request);

    /**
     * 修改菜单节点。
     *
     * @param menuId  菜单 ID
     * @param request 修改入参
     * @return 修改后的菜单节点，不含子菜单
     */
    MenuNodeResponse update(Long menuId, MenuUpdateRequest request);

    /**
     * 逻辑删除菜单节点。
     *
     * @param menuId 菜单 ID
     */
    void delete(Long menuId);
}
