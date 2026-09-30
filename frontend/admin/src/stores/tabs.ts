import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 多标签配置。
 */
export interface TabInput {
  /** 标签唯一键，取本地路由名 */
  key: string
  /** 标签标题 */
  title: string
  /** 完整路径，标签切换时按它跳转，保留查询参数 */
  path: string
}

/**
 * 常驻标签的路由名：工作台不允许关闭，也是“关闭全部”之后保留的唯一标签。
 */
export const PINNED_TAB_KEY = 'home'

/**
 * 标签数量上限，来自用户确认的“最多保留最近 12 个”。
 */
export const MAX_TABS = 12

/**
 * 管理端多标签状态。
 *
 * 只保存已经打开过的页面标签与当前激活标签，标签对应哪个组件由路由决定，
 * 因此标签不会绕过路由守卫，也不会缓存接口数据：切换标签等于一次正常路由跳转，
 * 页面重新读取自己的数据，避免出现“标签里的数据是旧的”。
 */
export const useTabsStore = defineStore('tabs', () => {
  /**
   * 已打开的标签，按打开先后排序。
   */
  const tabs = ref<TabInput[]>([])

  /**
   * 当前激活的标签键。
   */
  const activeKey = ref('')

  /**
   * 当前激活标签，没有匹配项时为 undefined。
   */
  const activeTab = computed(() => tabs.value.find((tab) => tab.key === activeKey.value))

  /**
   * 登记一次导航：已存在的标签只更新标题与路径并激活，新标签追加到末尾。
   *
   * 超过上限时从最久未打开的非常驻标签开始移除，常驻标签始终保留。
   *
   * @param tab 本次进入的路由信息
   */
  function openTab(tab: TabInput): void {
    const existing = tabs.value.find((item) => item.key === tab.key)
    if (existing) {
      existing.title = tab.title
      existing.path = tab.path
    } else {
      tabs.value.push({ ...tab })
      trimOverflow()
    }
    activeKey.value = tab.key
  }

  /**
   * 激活一个已存在的标签。
   *
   * @param key 标签键
   */
  function selectTab(key: string): void {
    if (tabs.value.some((tab) => tab.key === key)) {
      activeKey.value = key
    }
  }

  /**
   * 关闭一个标签，并给出关闭后应该停留的标签键。
   *
   * 关闭的不是当前标签时激活标签不变；关闭当前标签时优先停留右侧相邻标签，其次左侧。
   *
   * @param key 要关闭的标签键
   * @returns 关闭后应激活的标签键；没有变化时返回空字符串
   */
  function closeTab(key: string): string {
    if (key === PINNED_TAB_KEY) {
      return ''
    }
    const index = tabs.value.findIndex((tab) => tab.key === key)
    if (index < 0) {
      return ''
    }
    tabs.value.splice(index, 1)
    if (activeKey.value !== key) {
      return ''
    }
    const next = tabs.value[index] ?? tabs.value[index - 1]
    const nextKey = next ? next.key : PINNED_TAB_KEY
    activeKey.value = nextKey
    return nextKey
  }

  /**
   * 关闭全部标签，只保留常驻标签。
   *
   * @returns 关闭后应激活的标签键
   */
  function closeAll(): string {
    tabs.value = tabs.value.filter((tab) => tab.key === PINNED_TAB_KEY)
    activeKey.value = PINNED_TAB_KEY
    return PINNED_TAB_KEY
  }

  /**
   * 关闭除指定标签与常驻标签之外的全部标签。
   *
   * @param key 保留的标签键
   * @returns 关闭后应激活的标签键
   */
  function closeOthers(key: string): string {
    tabs.value = tabs.value.filter((tab) => tab.key === key || tab.key === PINNED_TAB_KEY)
    activeKey.value = key
    return key
  }

  /**
   * 关闭指定标签左侧的全部标签，常驻标签始终保留。
   *
   * @param key 基准标签键
   * @returns 关闭后应激活的标签键
   */
  function closeLeft(key: string): string {
    const index = tabs.value.findIndex((tab) => tab.key === key)
    if (index <= 0) {
      return ''
    }
    tabs.value = tabs.value.filter((tab, position) => position >= index || tab.key === PINNED_TAB_KEY)
    activeKey.value = key
    return key
  }

  /**
   * 关闭指定标签右侧的全部标签。
   *
   * @param key 基准标签键
   * @returns 关闭后应激活的标签键
   */
  function closeRight(key: string): string {
    const index = tabs.value.findIndex((tab) => tab.key === key)
    if (index < 0 || index === tabs.value.length - 1) {
      return ''
    }
    tabs.value = tabs.value.filter((tab, position) => position <= index || tab.key === PINNED_TAB_KEY)
    activeKey.value = key
    return key
  }

  /**
   * 退出登录时清空全部标签。
   */
  function reset(): void {
    tabs.value = []
    activeKey.value = ''
  }

  /**
   * 判断指定标签是否存在可关闭的左侧标签。
   *
   * @param key 基准标签键
   * @returns 左侧存在可关闭标签时返回 true
   */
  function hasClosableLeft(key: string): boolean {
    const index = tabs.value.findIndex((tab) => tab.key === key)
    return tabs.value.some((tab, position) => position < index && tab.key !== PINNED_TAB_KEY)
  }

  /**
   * 判断指定标签右侧是否存在标签。
   *
   * @param key 基准标签键
   * @returns 右侧存在标签时返回 true
   */
  function hasClosableRight(key: string): boolean {
    const index = tabs.value.findIndex((tab) => tab.key === key)
    return index >= 0 && index < tabs.value.length - 1
  }

  /**
   * 判断除常驻标签外是否还有其他标签。
   *
   * @returns 存在可关闭标签时返回 true
   */
  function hasClosableOthers(): boolean {
    return tabs.value.some((tab) => tab.key !== PINNED_TAB_KEY)
  }

  /**
   * 超出上限时移除最久未打开的非常驻标签。
   */
  function trimOverflow(): void {
    while (tabs.value.length > MAX_TABS) {
      const removable = tabs.value.findIndex((tab) => tab.key !== PINNED_TAB_KEY)
      if (removable < 0) {
        return
      }
      tabs.value.splice(removable, 1)
    }
  }

  return {
    tabs,
    activeKey,
    activeTab,
    openTab,
    selectTab,
    closeTab,
    closeAll,
    closeOthers,
    closeLeft,
    closeRight,
    hasClosableLeft,
    hasClosableRight,
    hasClosableOthers,
    reset,
  }
})
