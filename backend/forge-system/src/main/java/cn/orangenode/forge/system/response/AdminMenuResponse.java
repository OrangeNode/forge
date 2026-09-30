package cn.orangenode.forge.system.response;

import java.util.List;

/**
 * 当前管理员可见菜单节点。
 *
 * <p>只包含展示结构：{@code routeKey} 是前端本地路由白名单中的标识，目录节点为空；
 * 后端不下发组件路径或组件代码，前端遇到白名单外的标识不会渲染。</p>
 *
 * @param id       菜单 ID，对外为字符串
 * @param name     菜单名称
 * @param icon     图标标识，由前端内置图标白名单解析
 * @param routeKey 前端本地路由标识，目录为 {@code null}
 * @param children 子菜单，没有子菜单时为空列表
 */
public record AdminMenuResponse(String id, String name, String icon, String routeKey,
        List<AdminMenuResponse> children) {
}
