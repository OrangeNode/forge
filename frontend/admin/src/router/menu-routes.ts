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
  'video-source': 'video-source',
  'video-category': 'video-category',
  'video-videos': 'video-videos',
  'video-settings': 'video-settings',
  'video-task': 'video-task',
  'video-run': 'video-run',
  'video-search': 'video-search',
  'member-user': 'member-user',
  'member-device': 'member-device',
  'member-activation': 'member-activation',
  'app-splash': 'app-splash',
  'app-banner': 'app-banner',
  'app-section': 'app-section',
  'app-hot-search': 'app-hot-search',
  'app-feature': 'app-feature',
  'app-feedback': 'app-feedback',
  'app-release': 'app-release',
  'app-announcement': 'app-announcement',
  'app-marquee': 'app-marquee',
  home: 'home',
  'system-admin': 'system-admin',
  'system-role': 'system-role',
  'system-menu': 'system-menu',
  'file-list': 'file-list',
  'storage-config': 'storage-config',
  'audit-operation': 'audit-operation',
  'audit-login': 'audit-login',
}

/**
 * 合并页面分组。
 *
 * 后端菜单与角色授权仍按原来的细粒度页面登记（每个页面一组权限），前端把同组的页面合成一个
 * 侧边栏入口，进入后用标签页切换；没有授权的页面在合并页里不显示对应标签。
 */
export interface MenuGroup {
  /** 合并页面的本地路由名，同时作为侧边栏菜单键 */
  routeName: string
  /** 侧边栏与顶部标签显示的名称 */
  label: string
  /** 侧边栏图标标识 */
  icon: string
  /** 组内页面的菜单标识，按标签页顺序排列 */
  tabs: string[]
}

/** App 管理的合并分组，标签页顺序即数组顺序。 */
export const MENU_GROUPS: MenuGroup[] = [
  { routeName: 'app-home', label: '首页与开屏', icon: 'dashboard', tabs: ['app-splash', 'app-banner', 'app-section', 'app-hot-search'] },
  { routeName: 'app-notice', label: '公告与提示', icon: 'message', tabs: ['app-announcement', 'app-marquee'] },
  { routeName: 'app-operation', label: '功能与版本', icon: 'shield', tabs: ['app-feature', 'app-release'] },
]

/**
 * 查找菜单标识所属的合并分组。
 *
 * @param routeKey 后端下发的菜单标识
 * @returns 所属分组；不属于任何分组时返回 undefined
 */
export function findMenuGroup(routeKey: string | null | undefined): MenuGroup | undefined {
  if (!routeKey) {
    return undefined
  }
  return MENU_GROUPS.find((group) => group.tabs.includes(routeKey))
}

/**
 * 收集菜单树中出现的全部菜单标识，用于判断合并页里显示哪些标签。
 *
 * @param menus 后端返回的菜单树
 * @returns 菜单标识集合
 */
export function collectRouteKeys(menus: AdminMenuPayload[]): Set<string> {
  const keys = new Set<string>()
  for (const menu of menus) {
    if (menu.routeKey) {
      keys.add(menu.routeKey)
    }
    for (const key of collectRouteKeys(menu.children ?? [])) {
      keys.add(key)
    }
  }
  return keys
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
  if (!routeName) {
    return undefined
  }
  const group = findMenuGroup(routeKey)
  return group ? { name: group.routeName, query: { tab: routeKey } } : { name: routeName }
}

/**
 * 把后端菜单树转换为侧边栏菜单项。
 *
 * 顶级目录渲染为可折叠的子菜单，页面节点渲染为可点击项；白名单之外的节点不渲染，
 * 因此后端配置错误只会导致菜单缺失，不会把用户带到不存在的页面。
 *
 * 目录如果没有任何已登记的页面节点，整组不渲染，避免出现点开后什么都没有的空目录。
 * 属于同一合并分组的页面只在第一次出现的位置渲染一个入口。
 *
 * @param menus 后端返回的菜单树
 * @returns 侧边栏菜单项
 */
export function toMenuOptions(menus: AdminMenuPayload[]): MenuOption[] {
  const options: MenuOption[] = []
  const renderedGroups = new Set<string>()
  for (const menu of menus) {
    const children = toMenuOptions(menu.children ?? [])
    const route = resolveMenuRoute(menu.routeKey)
    const configuredIcon = (menu as AdminMenuPayload & { icon?: string }).icon
    const group = findMenuGroup(menu.routeKey)
    if (route && group) {
      if (!renderedGroups.has(group.routeName)) {
        renderedGroups.add(group.routeName)
        options.push({ key: group.routeName, label: group.label, icon: menuIcon(group.icon) })
      }
      continue
    }
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
