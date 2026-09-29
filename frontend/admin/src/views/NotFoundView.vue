<script setup lang="ts">
import { NButton, NResult } from 'naive-ui'
import { useRouter } from 'vue-router'

/**
 * 未匹配路由的结果页。
 */
const router = useRouter()

/**
 * 返回工作台。
 */
async function backHome(): Promise<void> {
  await router.replace({ name: 'home' })
}
</script>

<template>
  <NResult
    status="404"
    title="页面不存在"
    description="请从左侧菜单进入已开放的页面。"
  >
    <template #footer>
      <NButton
        type="primary"
        @click="backHome"
      >
        返回工作台
      </NButton>
    </template>
  </NResult>
</template>
