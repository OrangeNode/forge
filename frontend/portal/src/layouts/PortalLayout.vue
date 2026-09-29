<script setup lang="ts">
import { NLayout, NLayoutContent, NLayoutHeader } from 'naive-ui'
</script>

<template>
  <NLayout class="portal-layout">
    <NLayoutHeader
      bordered
      class="portal-layout__header"
    >
      <span class="portal-layout__brand">Orange Forge</span>
    </NLayoutHeader>
    <NLayoutContent class="portal-layout__content">
      <RouterView />
    </NLayoutContent>
  </NLayout>
</template>

<style scoped>
.portal-layout {
  min-height: 100vh;
}

.portal-layout__header {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 16px;
}

.portal-layout__brand {
  font-weight: 600;
}

.portal-layout__content {
  padding: 16px;
  overflow-x: auto;
}
</style>
