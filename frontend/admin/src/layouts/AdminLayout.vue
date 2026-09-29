<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { NLayout, NLayoutContent, NLayoutHeader, NLayoutSider } from 'naive-ui'

/**
 * 管理端基础布局。
 *
 * 侧边栏在窄屏下默认收起且可手动展开，内容区自行滚动，
 * 避免 375px 宽度下出现页面整体横向溢出。
 */
const collapsed = ref(false)

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
    </NLayoutSider>
    <NLayout>
      <NLayoutHeader
        bordered
        class="admin-layout__header"
      >
        <span class="admin-layout__title">管理端骨架</span>
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
