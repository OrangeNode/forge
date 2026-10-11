import { describe, expect, it } from 'vitest'

import type { AdminMenuPayload } from '@/api/auth'
import { collectRouteKeys, resolveMenuRoute, toMenuOptions } from './menu-routes'

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

  it('顶级页面渲染为可点击项，目录渲染为可折叠子菜单', () => {
    const options = toMenuOptions([
      menu('1', '工作台', 'home'),
      menu('2', '系统管理', undefined, [menu('3', '用户管理', 'system-admin')]),
    ])

    expect(options).toEqual([
      { key: 'home', label: '工作台', icon: expect.any(Function) },
      {
        type: 'submenu',
        key: 'group-2',
        label: '系统管理',
        icon: expect.any(Function),
        children: [
          { key: 'system-admin', label: '用户管理', icon: expect.any(Function) },
        ],
      },
    ])
  })

  it('未登记标识的页面节点被忽略', () => {
    const options = toMenuOptions([
      menu('1', '未知页面', 'unknown-page'),
      menu('2', '工作台', 'home'),
    ])

    expect(options).toEqual([{ key: 'home', label: '工作台', icon: expect.any(Function) }])
  })

  it('子项全部未登记时目录分组不渲染', () => {
    const options = toMenuOptions([menu('1', '系统管理', undefined, [menu('2', '未知页面', 'unknown-page')])])

    expect(options).toEqual([])
  })
})

describe('App 管理合并页面', () => {
  it('组内页面标识解析为合并页面并带上标签参数', () => {
    expect(resolveMenuRoute('app-banner')).toEqual({ name: 'app-home', query: { tab: 'app-banner' } })
    expect(resolveMenuRoute('app-feedback')).toEqual({ name: 'app-feedback' })
  })

  it('同组页面只渲染一个入口，位置取第一次出现处', () => {
    const options = toMenuOptions([
      menu('1', 'App 管理', undefined, [
        menu('2', '开屏设置', 'app-splash'),
        menu('3', '首页轮播', 'app-banner'),
        menu('4', '反馈处理', 'app-feedback'),
        menu('5', '公告管理', 'app-announcement'),
        menu('6', '播放页滚动文字', 'app-marquee'),
      ]),
    ])

    expect(options[0]?.children).toEqual([
      { key: 'app-home', label: '首页与开屏', icon: expect.any(Function) },
      { key: 'app-feedback', label: '反馈处理', icon: expect.any(Function) },
      { key: 'app-notice', label: '公告与提示', icon: expect.any(Function) },
    ])
  })

  it('收集菜单树中的全部页面标识', () => {
    const keys = collectRouteKeys([menu('1', 'App 管理', undefined, [menu('2', '首页轮播', 'app-banner')]), menu('3', '工作台', 'home')])

    expect([...keys].sort()).toEqual(['app-banner', 'home'])
  })
})
