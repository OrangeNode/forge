import type { MenuOption } from 'naive-ui'
import { h } from 'vue'
import type { RouteLocationRaw } from 'vue-router'

import type { AdminMenuPayload } from '@/api/auth'
import AppIcon from '@/components/AppIcon.vue'

/**
 * 后端菜单标识到本地路由名的白名单。
 *
 * 后端只下发标识，前端只渲染白名单内的标识；不执行远程组件代码，也不按路径动态导入页面。
 * 新增页面时在这里登记，未登记的菜单标识会被忽略。
 */
const MENU_ROUTE_WHITELIST: Record<string, string> = {
  home: 'home',
  'system-admin': 'system-admin',
  'system-role': 'system-role',
  'system-menu': 'system-menu',
  'file-list': 'file-list',
  'storage-config': 'storage-config',
  'audit-operation': 'audit-operation',
  'audit-login': 'audit-login',
}

/** 路由未配置图标时的本地回退，保证旧数据库升级期间菜单仍有一致图标。 */
const ROUTE_ICON_FALLBACKS: Record<string, string> = {
  home: 'dashboard',
  'system-admin': 'users',
  'system-role': 'shield',
  'system-menu': 'menu',
  'file-list': 'file',
  'storage-config': 'database',
  'audit-operation': 'clipboard',
  'audit-login': 'login',
}

/**
 * 创建菜单图标渲染函数。
 *
 * @param name 图标白名单标识
 * @returns Naive UI 菜单图标渲染函数
 */
function menuIcon(name: string): () => ReturnType<typeof h> {
  return () => h(AppIcon, { name, size: 17 })
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
 * 顶级目录渲染为可折叠的子菜单，页面节点渲染为可点击项；白名单之外的节点不渲染，
 * 因此后端配置错误只会导致菜单缺失，不会把用户带到不存在的页面。
 *
 * 目录如果没有任何已登记的页面节点，整组不渲染，避免出现点开后什么都没有的空目录。
 *
 * @param menus 后端返回的菜单树
 * @returns 侧边栏菜单项
 */
export function toMenuOptions(menus: AdminMenuPayload[]): MenuOption[] {
  const options: MenuOption[] = []
  for (const menu of menus) {
    const children = toMenuOptions(menu.children ?? [])
    const route = resolveMenuRoute(menu.routeKey)
    const configuredIcon = (menu as AdminMenuPayload & { icon?: string }).icon
    if (route) {
      options.push({
        key: String(menu.routeKey),
        label: menu.name ?? '',
        icon: menuIcon(configuredIcon ?? ROUTE_ICON_FALLBACKS[menu.routeKey ?? ''] ?? 'menu'),
      })
      continue
    }
    if (children.length > 0) {
      options.push({
        type: 'submenu',
        key: `group-${menu.id ?? menu.name}`,
        label: menu.name ?? '',
        icon: menuIcon(configuredIcon ?? 'settings'),
        children,
      })
    }
  }
  return options
}
