package cn.orangenode.forge.system.response;

import java.util.List;

/**
 * 菜单树节点。
 *
 * <p>菜单只描述可见性：{@code routeKey} 是前端本地路由白名单中的标识，目录节点为空；
 * 后端不下发组件路径或组件代码，前端遇到白名单外的标识不会渲染。</p>
 *
 * @param id       菜单 ID，对外为字符串
 * @param parentId 父菜单 ID，顶级为 {@code 0}
 * @param name     菜单名称
 * @param routeKey 前端本地路由标识，目录为 {@code null}
 * @param sortNo   同级展示顺序
 * @param children 子菜单，没有子菜单时为空列表
 */
public record MenuNodeResponse(String id, String parentId, String name, String routeKey, Integer sortNo,
        List<MenuNodeResponse> children) {
}
