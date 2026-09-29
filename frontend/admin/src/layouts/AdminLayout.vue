<script setup lang="ts">
import { NButton, NLayout, NLayoutContent, NLayoutHeader, NLayoutSider, NMenu, useMessage } from 'naive-ui'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { toMenuOptions } from '@/router/menu-routes'
import { useSessionStore } from '@/stores/session'

/**
 * 管理端基础布局骨架。
 *
 * 侧边栏在窄屏下默认收起且可手动展开，内容区自行滚动，避免 375px 宽度下出现页面整体横向溢出；
 * 菜单项来自后端下发且通过本地白名单过滤，视觉样式等设计确定后再统一调整。
 */
const collapsed = ref(false)
const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()

/**
 * 侧边栏菜单项。
 */
const menuOptions = computed(() => toMenuOptions(session.menus))

/**
 * 当前高亮的菜单键，取当前路由名。
 */
const activeMenuKey = computed(() => (typeof route.name === 'string' ? route.name : null))

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
    class="admin-layout"
    has-sider
  >
    <NLayoutSider
      v-model:collapsed="collapsed"
      bordered
      collapse-mode="width"
      :collapsed-width="0"
      :width="220"
      show-trigger
    >
      <div class="admin-layout__brand">
        Orange Forge 管理端
      </div>
      <NMenu
        :options="menuOptions"
        :value="activeMenuKey"
        :collapsed="collapsed"
        :collapsed-width="0"
        @update:value="handleMenuSelect"
      />
    </NLayoutSider>
    <NLayout>
      <NLayoutHeader
        bordered
        class="admin-layout__header"
      >
        <span class="admin-layout__title">
          {{ session.profile?.displayName || '未登录' }}
        </span>
        <NButton
          size="small"
          quaternary
          @click="handleLogout"
        >
          退出登录
        </NButton>
      </NLayoutHeader>
      <NLayoutContent class="admin-layout__content">
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-layout__brand {
  padding: 16px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
}

.admin-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 16px;
}

.admin-layout__title {
  font-size: 15px;
}

.admin-layout__content {
  padding: 16px;
  overflow-x: auto;
}
</style>
