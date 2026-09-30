<script setup lang="ts">
/**
 * 页面内容容器。
 *
 * 每个管理页面用它作为根节点：内部自行滚动并统一内边距，
 * 因此页面高度始终等于内容区高度，不会把浏览器窗口顶出滚动条。
 * 表单状态与数据请求由各页面自己负责，这里只提供布局，避免做成通用 CRUD 容器。
 */
defineProps<{
  /** 内容区最大宽度，默认铺满内容区 */
  maxWidth?: string
}>()
</script>

<template>
  <section
    class="page-body"
    :style="maxWidth ? { maxWidth } : undefined"
  >
    <slot />
  </section>
</template>

<style scoped>
.page-body {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px 24px;
}

@media (max-width: 640px) {
  .page-body {
    padding: 14px 12px 20px;
  }
}
</style>
