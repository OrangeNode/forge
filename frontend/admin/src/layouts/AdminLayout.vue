<script setup lang="ts">
import { NButton, NDropdown, NLayout, NLayoutHeader, NLayoutSider, NMenu, useMessage } from 'naive-ui'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import TabBar from '@/components/TabBar.vue'
import AppIcon from '@/components/AppIcon.vue'
import { toMenuOptions } from '@/router/menu-routes'
import { useSessionStore } from '@/stores/session'
import { useTabsStore } from '@/stores/tabs'

/**
 * 管理端基础布局（克制科技 · Calm Tech）。
 *
 * 整体固定为一屏高度：侧边栏与顶栏不跟随页面滚动，只有菜单区与内容区各自内部滚动，
 * 因此任何页面都不会把浏览器窗口顶出滚动条。
 * 左侧侧边栏承载按目录分组的后端菜单：目录是可折叠的子菜单，默认全部折叠，由用户点击目录标题展开；
 * 顶栏左侧的按钮可把整条侧边栏折叠为图标轨道，窄屏（≤768px）下侧边栏以浮层方式覆盖内容区，
 * 选中菜单后自动收起。
 */
const collapsed = ref(false)

/**
 * 已展开的目录键，初始为空数组，保证登录后所有目录都是折叠状态。
 *
 * 展开键由 NMenu 双向绑定维护：既不使用 default-expand-all，也不跟随当前路由自动展开父目录，
 * 避免初始渲染时目录被展开。
 */
const expandedKeys = ref<Array<string | number>>([])
const isNarrow = ref(false)
const session = useSessionStore()
const tabsStore = useTabsStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()

/**
 * 标签条实例，切换页面后把激活标签滚动进可见区域。
 */
const tabBarRef = ref<InstanceType<typeof TabBar> | null>(null)

/**
 * 侧边栏菜单项，来自后端菜单树并经本地白名单过滤。
 */
const menuOptions = computed(() => toMenuOptions(session.menus))

/**
 * 当前高亮的菜单键，取当前路由名。
 */
const activeMenuKey = computed(() => (typeof route.name === 'string' ? route.name : null))

/**
 * 窄屏下侧边栏收起宽度为 0（浮层），宽屏收起时保留图标轨道。
 */
const collapsedWidth = computed(() => (isNarrow.value ? 0 : 64))

/**
 * 当前日期文案，按本地时区展示。
 */
const todayText = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date()),
)

/**
 * 账号头像中的首字缩写。
 */
const avatarText = computed(() => {
  const name = session.profile?.displayName ?? session.profile?.username ?? 'OF'
  return name.slice(0, 2).toUpperCase()
})

/**
 * 账号下拉菜单项。
 */
const accountOptions = [{ key: 'logout', label: '退出登录' }]

/**
 * 窄屏媒体查询对象，组件卸载时释放监听。
 */
let narrowQuery: MediaQueryList | undefined

/**
 * 跨越断点时切换侧边栏的默认状态，不覆盖用户在宽屏下的手动选择。
 *
 * @param event 媒体查询状态变化事件
 */
function handleNarrowChange(event: MediaQueryListEvent): void {
  isNarrow.value = event.matches
  collapsed.value = event.matches
}

/**
 * 切换侧边栏展开与收起。
 */
function toggleCollapsed(): void {
  collapsed.value = !collapsed.value
}

/**
 * 切换菜单时导航到对应本地路由，窄屏下同时收起浮层侧边栏。
 *
 * @param key 菜单键，即本地路由名
 */
function handleMenuSelect(key: string | number): void {
  if (isNarrow.value) {
    collapsed.value = true
  }
  void router.push({ name: String(key) })
}

/**
 * 处理账号下拉菜单的选择结果。
 *
 * @param key 选项键
 */
function handleAccountSelect(key: string): void {
  if (key === 'logout') {
    void handleLogout()
  }
}

/**
 * 退出登录：即使后端撤销失败也清理本地会话与标签并回到登录页。
 */
async function handleLogout(): Promise<void> {
  try {
    await session.logout()
  } catch {
    // 本地会话已在退出流程中清理，跳转登录页即可，不重复提示后端失败
  }
  tabsStore.reset()
  message.success('已退出登录')
  await router.replace({ name: 'login' })
}

/**
 * 把当前路由登记为一个标签。
 *
 * 登录页与 404 这类页面不进入标签条；标签标题取路由元信息的标题。
 */
function registerCurrentTab(): void {
  const name = typeof route.name === 'string' ? route.name : ''
  if (name === '' || name === 'login' || name === 'not-found') {
    return
  }
  tabsStore.openTab({
    key: name,
    title: route.meta.title ?? name,
    path: route.fullPath,
  })
  tabBarRef.value?.scrollActiveIntoView()
}

/**
 * 标签条请求切换页面：按标签键跳转。
 *
 * @param key 标签键，即本地路由名
 */
function handleTabSwitch(key: string): void {
  void router.push({ name: key })
}

watch(() => route.fullPath, registerCurrentTab, { immediate: true })

onMounted(() => {
  narrowQuery = window.matchMedia('(max-width: 768px)')
  isNarrow.value = narrowQuery.matches
  collapsed.value = narrowQuery.matches
  narrowQuery.addEventListener('change', handleNarrowChange)
})

onBeforeUnmount(() => {
  narrowQuery?.removeEventListener('change', handleNarrowChange)
})
</script>

<template>
  <NLayout
    class="shell"
    has-sider
  >
    <div
      v-if="isNarrow && !collapsed"
      class="shell__backdrop"
      @click="collapsed = true"
    />
    <NLayoutSider
      v-model:collapsed="collapsed"
      class="shell__sider"
      :class="{ 'is-overlay': isNarrow }"
      collapse-mode="width"
      :collapsed-width="collapsedWidth"
      :width="228"
      :native-scrollbar="false"
    >
      <div class="brand">
        <span class="brand__logo">O</span>
        <span
          v-if="!collapsed"
          class="brand__text"
        >ORANGE FORGE</span>
      </div>
      <NMenu
        v-model:expanded-keys="expandedKeys"
        class="shell__menu"
        :options="menuOptions"
        :value="activeMenuKey"
        :collapsed="collapsed"
        :collapsed-width="collapsedWidth"
        :collapsed-icon-size="18"
        @update:value="handleMenuSelect"
      />
    </NLayoutSider>

    <div class="shell__main">
      <NLayoutHeader
        class="topbar"
      >
        <div class="topbar__left">
          <NButton
            quaternary
            size="small"
            class="topbar__toggle"
            :title="collapsed ? '展开菜单' : '收起菜单'"
            @click="toggleCollapsed"
          >
            <AppIcon
              name="menu"
              :size="19"
            />
          </NButton>
        </div>
        <div class="topbar__right">
          <span class="topbar__date">{{ todayText }}</span>
          <NDropdown
            :options="accountOptions"
            @select="handleAccountSelect"
          >
            <button
              class="account"
              type="button"
            >
              <span class="account__avatar">{{ avatarText }}</span>
              <span class="account__name">{{ session.profile?.displayName || '未登录' }}</span>
            </button>
          </NDropdown>
        </div>
      </NLayoutHeader>

      <TabBar
        ref="tabBarRef"
        @switch="handleTabSwitch"
      />

      <main class="content">
        <RouterView />
      </main>
    </div>
  </NLayout>
</template>

<style scoped>
.shell {
  height: 100dvh;
  overflow: hidden;
  background: #f4f8ff;
}

.shell__backdrop {
  position: fixed;
  z-index: 20;
  inset: 0;
  background: rgba(16, 22, 32, 0.28);
}

.shell__sider {
  z-index: 21;
  display: flex;
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid #d9e7f7;
  background: #eaf3ff;
}

.shell__sider.is-overlay {
  position: fixed;
  top: 0;
  left: 0;
  height: 100vh;
  box-shadow: 0 12px 32px rgba(16, 22, 32, 0.16);
}

.brand {
  display: flex;
  flex: none;
  align-items: center;
  gap: 9px;
  height: 64px;
  padding: 0 16px;
  overflow: hidden;
  border-bottom: 1px solid #d9e7f7;
}

.brand__logo {
  display: grid;
  flex: none;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 9px;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 800;
}

.brand__text {
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: 0.12em;
  color: #24446d;
}

.shell__menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 10px 18px;
}

.shell__menu :deep(.n-menu-item-content) {
  font-weight: 550;
}

.shell__menu :deep(.n-menu-item-content:hover) {
  box-shadow: 0 5px 15px rgba(60, 108, 171, 0.08);
}

.shell__menu :deep(.n-menu-item-content--selected) {
  box-shadow: inset 3px 0 #2563eb;
}

.shell__main {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  min-width: 0;
  height: 100vh;
  flex-direction: column;
  background: transparent;
}

.topbar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 64px;
  padding: 0 20px;
  background: #fff;
  box-shadow: 0 1px 0 rgba(19, 42, 78, 0.06), 0 7px 20px rgba(22, 51, 91, 0.04);
}

.topbar__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.topbar__toggle {
  color: #41536d;
  font-size: 16px;
}

.topbar__right {
  display: flex;
  flex: none;
  align-items: center;
  gap: 14px;
}

.topbar__date {
  color: #8290a2;
  font-size: 12px;
}

.account {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  border-radius: 10px;
  padding: 5px 10px 5px 6px;
  background: #f3f6fb;
  cursor: pointer;
}

.account__avatar {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: #dbe8ff;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 800;
}

.account__name {
  color: #3f4b56;
  font-size: 12px;
}

.content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: transparent;
}

@media (max-width: 640px) {
  .topbar {
    height: 56px;
    padding: 0 12px;
  }

  .topbar__date,
  .account__name {
    display: none;
  }
}
</style>
