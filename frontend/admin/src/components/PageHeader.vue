<script setup lang="ts">
/**
 * 页面头部：标题、说明与操作区。
 *
 * 各页面只描述自己的标题与右侧操作，保持页面之间的一致排版。
 */
defineProps<{
  /** 页面标题 */
  title: string
  /** 页面说明，可为空 */
  description?: string
}>()
</script>

<template>
  <header class="page-head">
    <div class="page-head__text">
      <h2>{{ title }}</h2>
      <p v-if="description">
        {{ description }}
      </p>
    </div>
    <div class="page-head__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
}

.page-head__text h2 {
  margin: 0;
  font-size: 20px;
  letter-spacing: -0.03em;
  color: #17212b;
}

.page-head__text p {
  margin: 4px 0 0;
  color: #8290a2;
  font-size: 12px;
}

.page-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 640px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
