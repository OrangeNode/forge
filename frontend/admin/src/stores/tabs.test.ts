import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { MAX_TABS, PINNED_TAB_KEY, useTabsStore } from './tabs'

/**
 * 构造标签输入。
 *
 * @param key 标签键，即本地路由名
 * @param title 标签标题
 * @returns 标签输入
 */
function tab(key: string, title = key): { key: string; title: string; path: string } {
  return { key, title, path: `/${key}` }
}

describe('多标签状态', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('登记常驻标签与页面标签，重复登记只激活不重复添加', () => {
    const store = useTabsStore()
    store.openTab(tab(PINNED_TAB_KEY, '工作台'))
    store.openTab(tab('system-role', '角色管理'))
    store.openTab(tab('system-role', '角色管理'))

    expect(store.tabs.map((item) => item.key)).toEqual([PINNED_TAB_KEY, 'system-role'])
    expect(store.activeKey).toBe('system-role')
  })

  it('关闭当前标签后停留右侧相邻标签，其次左侧', () => {
    const store = useTabsStore()
    store.openTab(tab(PINNED_TAB_KEY))
    store.openTab(tab('a'))
    store.openTab(tab('b'))
    store.openTab(tab('c'))

    store.selectTab('b')
    expect(store.closeTab('b')).toBe('c')
    expect(store.tabs.map((item) => item.key)).toEqual([PINNED_TAB_KEY, 'a', 'c'])

    store.selectTab('c')
    expect(store.closeTab('c')).toBe('a')
  })

  it('常驻标签不允许关闭', () => {
    const store = useTabsStore()
    store.openTab(tab(PINNED_TAB_KEY))

    expect(store.closeTab(PINNED_TAB_KEY)).toBe('')
    expect(store.tabs.map((item) => item.key)).toEqual([PINNED_TAB_KEY])
  })

  it('关闭其他与关闭全部都会保留常驻标签', () => {
    const store = useTabsStore()
    store.openTab(tab(PINNED_TAB_KEY))
    store.openTab(tab('a'))
    store.openTab(tab('b'))
    store.openTab(tab('c'))

    expect(store.closeOthers('b')).toBe('b')
    expect(store.tabs.map((item) => item.key)).toEqual([PINNED_TAB_KEY, 'b'])

    store.openTab(tab('c'))
    expect(store.closeAll()).toBe(PINNED_TAB_KEY)
    expect(store.tabs.map((item) => item.key)).toEqual([PINNED_TAB_KEY])
  })

  it('关闭左侧不关闭常驻标签，关闭右侧保留基准标签与其左侧', () => {
    const store = useTabsStore()
    store.openTab(tab(PINNED_TAB_KEY))
    store.openTab(tab('a'))
    store.openTab(tab('b'))
    store.openTab(tab('c'))
    store.openTab(tab('d'))

    expect(store.closeLeft('c')).toBe('c')
    expect(store.tabs.map((item) => item.key)).toEqual([PINNED_TAB_KEY, 'c', 'd'])

    expect(store.closeRight('c')).toBe('c')
    expect(store.tabs.map((item) => item.key)).toEqual([PINNED_TAB_KEY, 'c'])
  })

  it('超出上限时按打开顺序移除最久未使用的非常驻标签', () => {
    const store = useTabsStore()
    store.openTab(tab(PINNED_TAB_KEY))
    for (let index = 0; index < MAX_TABS; index += 1) {
      store.openTab(tab(`page-${index}`))
    }

    expect(store.tabs).toHaveLength(MAX_TABS)
    expect(store.tabs[0].key).toBe(PINNED_TAB_KEY)
    expect(store.tabs.some((item) => item.key === 'page-0')).toBe(false)
    expect(store.tabs.at(-1)?.key).toBe(`page-${MAX_TABS - 1}`)
  })

  it('可用性判断与批量关闭按钮的禁用状态一致', () => {
    const store = useTabsStore()
    store.openTab(tab(PINNED_TAB_KEY))
    store.openTab(tab('a'))
    store.openTab(tab('b'))

    expect(store.hasClosableOthers()).toBe(true)
    expect(store.hasClosableLeft('b')).toBe(true)
    expect(store.hasClosableLeft(PINNED_TAB_KEY)).toBe(false)
    expect(store.hasClosableRight('b')).toBe(false)
    expect(store.hasClosableRight('a')).toBe(true)
  })

  it('退出登录清空全部标签', () => {
    const store = useTabsStore()
    store.openTab(tab(PINNED_TAB_KEY))
    store.openTab(tab('a'))
    store.reset()

    expect(store.tabs).toEqual([])
    expect(store.activeKey).toBe('')
  })
})
