<script setup lang="ts">
import { computed } from 'vue'

/**
 * 项目内置线性图标。
 *
 * 菜单只保存图标标识，前端仍使用白名单内的 SVG 路径，不执行后端下发的任意代码。
 */
const props = withDefaults(
  defineProps<{
    /** 图标白名单标识 */
    name?: string | null
    /** 图标尺寸，单位像素 */
    size?: number
  }>(),
  { name: 'menu', size: 18 },
)

const ICON_PATHS: Record<string, string[]> = {
  dashboard: ['M4 13h6V4H4v9Z', 'M14 20h6v-9h-6v9Z', 'M14 4h6v3h-6V4Z', 'M4 17h6v3H4v-3Z'],
  settings: ['M4 6h16', 'M7 12h10', 'M9 18h6'],
  users: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z', 'M22 21v-2a4 4 0 0 0-3-3.87', 'M16 3.13a4 4 0 0 1 0 7.75'],
  shield: ['M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z', 'm9 12 2 2 4-4'],
  menu: ['M4 6h16', 'M4 12h16', 'M4 18h16'],
  folder: ['M3 6h6l2 2h10v10H3V6Z'],
  file: ['M6 2h8l4 4v16H6V2Z', 'M14 2v6h6'],
  database: ['M4 6c0 2 3.6 4 8 4s8-2 8-4-3.6-4-8-4-8 2-8 4Z', 'M4 6v6c0 2 3.6 4 8 4s8-2 8-4V6', 'M4 12v6c0 2 3.6 4 8 4s8-2 8-4v-6'],
  activity: ['M3 12h4l2-7 4 14 2-7h6'],
  clipboard: ['M9 5h6', 'M9 3h6v4H9V3Z', 'M6 5H4v17h16V5h-2', 'M8 12h8', 'M8 16h6'],
  login: ['M10 17l5-5-5-5', 'M15 12H3', 'M14 3h7v18h-7'],
  refresh: ['M20 6v5h-5', 'M4 18v-5h5', 'M18.5 9A7 7 0 0 0 6 6.5L4 11', 'M5.5 15A7 7 0 0 0 18 17.5L20 13'],
  plus: ['M12 5v14', 'M5 12h14'],
  edit: ['M12 20h9', 'M16.5 3.5a2.12 2.12 0 0 1 3 3L9 17l-4 1 1-4 10.5-10.5Z'],
  trash: ['M3 6h18', 'M8 6V3h8v3', 'M5 6l1 15h12l1-15', 'M10 10v7', 'M14 10v7'],
  close: ['M6 6l12 12', 'M18 6 6 18'],
  'close-others': ['M5 5l5 5', 'M10 5l-5 5', 'M14 7h7v12h-7'],
  left: ['M14 18l-6-6 6-6', 'M20 12H8'],
  right: ['M10 6l6 6-6 6', 'M4 12h12'],
  'x-circle': ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z', 'm15 9-6 6', 'm9 9 6 6'],
}

/** 当前图标路径，未知标识回退为菜单图标。 */
const paths = computed(() => ICON_PATHS[props.name ?? ''] ?? ICON_PATHS.menu)
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path
      v-for="path in paths"
      :key="path"
      :d="path"
    />
  </svg>
</template>

<style scoped>
.app-icon {
  display: block;
  flex: none;
}
</style>
