import { describe, expect, it } from 'vitest'

import type { AdminMenuPayload } from '@/api/auth'
import { resolveMenuRoute, toMenuOptions } from './menu-routes'

/**
 * 构造菜单节点，字段与后端返回结构一致。
 *
 * @param id 菜单 ID
 * @param name 菜单名称
 * @param routeKey 前端路由标识，目录传 undefined
 * @param children 子菜单
 * @returns 菜单节点
 */
function menu(
  id: string,
  name: string,
  routeKey?: string,
  children?: AdminMenuPayload[],
): AdminMenuPayload {
  return { id, name, routeKey, children }
}

describe('菜单白名单映射', () => {
  it('已登记的标识解析为本地路由名', () => {
    expect(resolveMenuRoute('system-admin')).toEqual({ name: 'system-admin' })
  })

  it('未登记或为空的标识不解析', () => {
    expect(resolveMenuRoute('not-registered')).toBeUndefined()
    expect(resolveMenuRoute(null)).toBeUndefined()
    expect(resolveMenuRoute(undefined)).toBeUndefined()
  })

  it('顶级页面渲染为可点击项，目录渲染为分组', () => {
    const options = toMenuOptions([
      menu('1', '工作台', 'home'),
      menu('2', '系统管理', undefined, [menu('3', '管理员账号', 'system-admin')]),
    ])

    expect(options).toEqual([
      { key: 'home', label: '工作台' },
      {
        type: 'group',
        key: 'group-2',
        label: '系统管理',
        children: [{ key: 'system-admin', label: '管理员账号' }],
      },
    ])
  })

  it('未登记标识的页面节点被忽略', () => {
    const options = toMenuOptions([
      menu('1', '未知页面', 'unknown-page'),
      menu('2', '工作台', 'home'),
    ])

    expect(options).toEqual([{ key: 'home', label: '工作台' }])
  })

  it('子项全部未登记时目录分组不渲染', () => {
    const options = toMenuOptions([menu('1', '系统管理', undefined, [menu('2', '未知页面', 'unknown-page')])])

    expect(options).toEqual([])
  })
})
