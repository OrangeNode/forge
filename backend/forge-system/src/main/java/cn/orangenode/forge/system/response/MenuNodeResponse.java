package cn.orangenode.forge.system.response;

import java.util.List;

/**
 * 菜单树节点。
 *
 * <p>菜单同时描述可见性与接口权限：{@code routeKey} 是前端本地路由白名单中的标识，目录节点为空；
 * 后端不下发组件路径或组件代码，前端遇到白名单外的标识不会渲染。
 * {@code permissions} 是该节点声明的接口权限，角色授予该节点即同时获得这些权限。</p>
 *
 * @param id          菜单 ID，对外为字符串
 * @param parentId    父菜单 ID，顶级为 {@code 0}
 * @param name        菜单名称
 * @param menuType    菜单类型：directory 目录、page 页面
 * @param icon        图标标识
 * @param routeKey    前端本地路由标识，目录为 {@code null}
 * @param permissions 该节点声明的接口权限，没有声明时为空列表
 * @param sortNo      同级展示顺序
 * @param children    子菜单，没有子菜单时为空列表
 */
public record MenuNodeResponse(String id, String parentId, String name, String menuType, String icon, String routeKey,
        List<MenuPermissionResponse> permissions, Integer sortNo, List<MenuNodeResponse> children) {
}
