<script setup lang="ts">
import { useThemeVars } from 'naive-ui'
import AppIcon from '@/components/AppIcon.vue'
/**
 * 页面头部：标题、说明与操作区。
 *
 * 各页面只描述自己的标题与右侧操作，保持页面之间的一致排版。
 */
defineProps<{
  /** 列表图标标识 */
  icon?: string
  /** 列表标题 */
  title: string
  /** 页面说明，可为空 */
  description?: string
}>()
const theme = useThemeVars()
</script>

<template>
  <header class="page-head">
    <div class="page-head__intro">
      <span
        v-if="icon"
        class="page-head__icon"
      ><AppIcon
        :name="icon"
        :size="20"
      /></span>
      <div class="page-head__text">
        <h2>{{ title }}</h2>
        <p v-if="description">
          {{ description }}
        </p>
      </div>
    </div>
    <div class="page-head__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid v-bind('theme.dividerColor');
}

.page-head__text h2 {
  margin: 0;
  font-size: 16px;
  letter-spacing: -0.03em;
  color: v-bind('theme.textColor1');
}

.page-head__text p {
  margin: 4px 0 0;
  color: v-bind('theme.textColor3');
  font-size: 12px;
}

.page-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.page-head__actions :deep(.n-upload) { width: auto; }
.page-head__intro { display: flex; align-items: center; gap: 12px; min-width: 0; }
.page-head__icon { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 42px; height: 42px; border-radius: 12px; color: v-bind('theme.primaryColor'); background: v-bind('theme.bodyColor'); }
.page-head__actions :deep(.action-button) { min-width: 104px; height: 40px; padding: 0 17px; border-radius: 10px; }
@media (max-width: 640px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
