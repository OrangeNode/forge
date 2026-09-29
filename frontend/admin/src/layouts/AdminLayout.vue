<script setup lang="ts">
import { NButton, NDropdown, NLayout, NLayoutContent, NLayoutHeader, NLayoutSider, NMenu, useMessage } from 'naive-ui'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { toMenuOptions } from '@/router/menu-routes'
import { useSessionStore } from '@/stores/session'

/**
 * 管理端基础布局（克制科技 · Calm Tech）。
 *
 * 左侧白色侧边栏承载按分组渲染的后端菜单，右侧顶栏显示当前页面标题、当前日期与账号入口；
 * 窄屏下侧边栏默认收起并可手动展开，内容区自行滚动，避免页面整体横向溢出。
 */
const collapsed = ref(false)
const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()

/**
 * 侧边栏菜单项，来自后端菜单树并经本地白名单过滤。
 */
const menuOptions = computed(() => toMenuOptions(session.menus))

/**
 * 当前高亮的菜单键，取当前路由名。
 */
const activeMenuKey = computed(() => (typeof route.name === 'string' ? route.name : null))

/**
 * 顶栏标题与说明。
 */
const pageTitle = computed(() => route.meta.title ?? '管理端')
const pageDescription = computed(() => route.meta.description ?? '')

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
 * 按当前视口宽度调整侧边栏的默认展开状态。
 *
 * 只在跨越断点时改变一次，不覆盖用户手动收起或展开的选择。
 *
 * @param event 媒体查询状态变化事件
 */
function handleNarrowChange(event: MediaQueryListEvent): void {
  collapsed.value = event.matches
}

/**
 * 切换菜单时导航到对应本地路由。
 *
 * @param key 菜单键，即本地路由名
 */
function handleMenuSelect(key: string | number): void {
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
 * 退出登录：即使后端撤销失败也清理本地会话并回到登录页。
 */
async function handleLogout(): Promise<void> {
  try {
    await session.logout()
  } catch {
    // 本地会话已在退出流程中清理，跳转登录页即可，不重复提示后端失败
  }
  message.success('已退出登录')
  await router.replace({ name: 'login' })
}

onMounted(() => {
  narrowQuery = window.matchMedia('(max-width: 768px)')
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
    <NLayoutSider
      v-model:collapsed="collapsed"
      class="shell__sider"
      bordered
      collapse-mode="width"
      :collapsed-width="0"
      :width="228"
      :native-scrollbar="false"
    >
      <div class="brand">
        <span class="brand__logo">O</span>
        <span class="brand__text">ORANGE FORGE</span>
      </div>
      <NMenu
        class="shell__menu"
        :options="menuOptions"
        :value="activeMenuKey"
        :collapsed="collapsed"
        :collapsed-width="0"
        :collapsed-icon-size="18"
        @update:value="handleMenuSelect"
      />
      <div class="shell__footer">
        <div class="footer-card">
          <b>当前环境</b>
          <span>{{ session.profile?.username || '未登录' }}</span>
        </div>
      </div>
    </NLayoutSider>

    <NLayout class="shell__main">
      <NLayoutHeader
        class="topbar"
        bordered
      >
        <div class="topbar__left">
          <NButton
            quaternary
            size="small"
            class="topbar__toggle"
            @click="collapsed = !collapsed"
          >
            ☰
          </NButton>
          <div class="topbar__heading">
            <h1>{{ pageTitle }}</h1>
            <p v-if="pageDescription">
              {{ pageDescription }}
            </p>
          </div>
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
      <NLayoutContent class="content">
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  background: radial-gradient(circle at 12% 0%, rgba(255, 125, 51, 0.1), transparent 26rem), #f5f7fb;
}

.shell__sider {
  background: #fff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 20px 16px 14px;
}

.brand__logo {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #5b7cfa, #8f63ef);
  color: #fff;
  font-size: 15px;
  font-weight: 800;
}

.brand__text {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #3f4b56;
}

.shell__menu {
  padding: 0 10px 16px;
}

.shell__footer {
  margin-top: auto;
  padding: 12px 14px 18px;
}

.footer-card {
  border-radius: 14px;
  padding: 12px 13px;
  background: linear-gradient(145deg, #212b3b, #101620);
  color: #fff;
  font-size: 11px;
  line-height: 1.6;
}

.footer-card b {
  display: block;
  margin-bottom: 3px;
  font-size: 11px;
}

.footer-card span {
  color: #b7c2d0;
}

.shell__main {
  background: transparent;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 68px;
  padding: 0 20px;
  background: #fff;
}

.topbar__left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.topbar__toggle {
  font-size: 16px;
}

.topbar__heading h1 {
  margin: 0;
  font-size: 19px;
  letter-spacing: -0.03em;
  color: #17212b;
}

.topbar__heading p {
  margin: 2px 0 0;
  color: #8290a2;
  font-size: 12px;
}

.topbar__right {
  display: flex;
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
  border: 1px solid #e8ecf2;
  border-radius: 10px;
  padding: 5px 10px 5px 6px;
  background: #fff;
  cursor: pointer;
}

.account__avatar {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: #ffe5d1;
  color: #c85b19;
  font-size: 11px;
  font-weight: 800;
}

.account__name {
  color: #3f4b56;
  font-size: 12px;
}

.content {
  padding: 20px;
  overflow-x: auto;
}

@media (max-width: 640px) {
  .topbar {
    height: 60px;
    padding: 0 12px;
  }

  .topbar__date,
  .account__name {
    display: none;
  }

  .content {
    padding: 14px 12px 24px;
  }
}
</style>
