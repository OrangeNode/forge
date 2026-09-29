import type { MenuOption } from 'naive-ui'
import type { RouteLocationRaw } from 'vue-router'

import type { AdminMenuPayload } from '@/api/auth'

/**
 * 后端菜单标识到本地路由名的白名单。
 *
 * 后端只下发标识，前端只渲染白名单内的标识；不执行远程组件代码，也不按路径动态导入页面。
 * 新增页面时在这里登记，未登记的菜单标识会被忽略。
 */
const MENU_ROUTE_WHITELIST: Record<string, string> = {
  home: 'home',
}

/**
 * 解析菜单标识对应的本地路由。
 *
 * @param routeKey 后端下发的路由标识，目录节点为空
 * @returns 白名单内的路由位置；未登记时返回 undefined
 */
export function resolveMenuRoute(routeKey: string | null | undefined): RouteLocationRaw | undefined {
  if (!routeKey) {
    return undefined
  }
  const routeName = MENU_ROUTE_WHITELIST[routeKey]
  return routeName ? { name: routeName } : undefined
}

/**
 * 把后端菜单树转换为侧边栏菜单项。
 *
 * 白名单之外的节点不会渲染，因此后端配置错误只会导致菜单缺失，不会把用户带到不存在的页面。
 *
 * @param menus 后端返回的菜单树
 * @returns 侧边栏菜单项
 */
export function toMenuOptions(menus: AdminMenuPayload[]): MenuOption[] {
  const options: MenuOption[] = []
  for (const menu of menus) {
    const children = toMenuOptions(menu.children ?? [])
    const route = resolveMenuRoute(menu.routeKey)
    if (!route && children.length === 0) {
      continue
    }
    options.push({
      key: route ? String(menu.routeKey) : `menu-${menu.id ?? menu.name}`,
      label: menu.name,
      children: children.length > 0 ? children : undefined,
    })
  }
  return options
}
