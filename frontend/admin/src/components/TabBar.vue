<script setup lang="ts">
import { NDropdown } from 'naive-ui'
import type { DropdownOption } from 'naive-ui'
import { computed, h, nextTick, ref } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import { PINNED_TAB_KEY, useTabsStore } from '@/stores/tabs'

/**
 * 多标签条。
 *
 * 位于顶栏下方，展示当前已打开的页面标签；左键切换，中键或标签上的关闭按钮关闭，
 * 右键弹出「关闭当前 / 关闭其他 / 关闭左侧 / 关闭右侧 / 关闭全部」。
 * 关闭动作只修改标签集合，真正跳转由父布局监听 {@code switch} 事件执行，
 * 本组件不直接操作路由，便于单独测试。
 */
const emit = defineEmits<{
  /** 请求切换到指定标签对应的页面 */
  (event: 'switch', key: string): void
}>()

const tabsStore = useTabsStore()

/**
 * 右键菜单的目标标签键。
 */
const contextKey = ref('')

/**
 * 右键菜单是否可见。
 */
const contextVisible = ref(false)

/**
 * 右键菜单的显示位置。
 */
const contextX = ref(0)
const contextY = ref(0)

/**
 * 标签滚动容器，用于把激活标签滚动进可见区域。
 */
const scrollRef = ref<HTMLElement | null>(null)

/**
 * 右键菜单项，按目标标签的实际情况禁用无效项。
 */
const contextOptions = computed<DropdownOption[]>(() => {
  const key = contextKey.value
  return [
    { key: 'close', label: '关闭当前', icon: () => h(AppIcon, { name: 'close', size: 16 }), disabled: key === PINNED_TAB_KEY },
    { key: 'close-others', label: '关闭其他', icon: () => h(AppIcon, { name: 'close-others', size: 16 }), disabled: !tabsStore.hasClosableOthers() },
    { key: 'close-left', label: '关闭左侧', icon: () => h(AppIcon, { name: 'left', size: 16 }), disabled: !tabsStore.hasClosableLeft(key) },
    { key: 'close-right', label: '关闭右侧', icon: () => h(AppIcon, { name: 'right', size: 16 }), disabled: !tabsStore.hasClosableRight(key) },
    { key: 'close-all', label: '关闭全部', icon: () => h(AppIcon, { name: 'x-circle', size: 16 }), disabled: !tabsStore.hasClosableOthers() },
  ]
})

/**
 * 切换到指定标签。
 *
 * @param key 标签键
 */
function handleSelect(key: string): void {
  if (key !== tabsStore.activeKey) {
    emit('switch', key)
  }
}

/**
 * 关闭指定标签；关闭的是当前标签时跳转到相邻标签。
 *
 * @param key 标签键
 */
function handleClose(key: string): void {
  const nextKey = tabsStore.closeTab(key)
  if (nextKey) {
    emit('switch', nextKey)
  }
}

/**
 * 打开右键菜单并记录目标标签。
 *
 * @param event 鼠标事件
 * @param key 标签键
 */
function handleContextMenu(event: MouseEvent, key: string): void {
  event.preventDefault()
  contextKey.value = key
  contextX.value = event.clientX
  contextY.value = event.clientY
  contextVisible.value = false
  void nextTick(() => {
    contextVisible.value = true
  })
}

/**
 * 执行右键菜单选择结果。
 *
 * @param key 菜单键
 */
function handleContextSelect(key: string): void {
  contextVisible.value = false
  const target = contextKey.value
  let nextKey = ''
  if (key === 'close') {
    nextKey = tabsStore.closeTab(target)
  } else if (key === 'close-others') {
    nextKey = tabsStore.closeOthers(target)
  } else if (key === 'close-left') {
    nextKey = tabsStore.closeLeft(target)
  } else if (key === 'close-right') {
    nextKey = tabsStore.closeRight(target)
  } else if (key === 'close-all') {
    nextKey = tabsStore.closeAll()
  }
  if (nextKey) {
    emit('switch', nextKey)
  }
}

/**
 * 中键点击标签时关闭，与浏览器标签页的操作习惯一致。
 *
 * @param event 鼠标事件
 * @param key 标签键
 */
function handleAuxClick(event: MouseEvent, key: string): void {
  if (event.button === 1) {
    event.preventDefault()
    handleClose(key)
  }
}

/**
 * 把激活标签滚动到可见区域，标签过多时避免激活项被挤出视口。
 */
function scrollActiveIntoView(): void {
  void nextTick(() => {
    const element = scrollRef.value?.querySelector<HTMLElement>('.tab.is-active')
    element?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  })
}

defineExpose({ scrollActiveIntoView })
</script>

<template>
  <div class="tab-bar">
    <div
      ref="scrollRef"
      class="tab-bar__scroll"
    >
      <div class="tab-bar__list">
        <button
          v-for="tab in tabsStore.tabs"
          :key="tab.key"
          type="button"
          class="tab"
          :class="{ 'is-active': tab.key === tabsStore.activeKey }"
          @click="handleSelect(tab.key)"
          @auxclick="handleAuxClick($event, tab.key)"
          @contextmenu="handleContextMenu($event, tab.key)"
        >
          <span class="tab__title">{{ tab.title }}</span>
          <span
            v-if="tab.key !== PINNED_TAB_KEY"
            class="tab__close"
            role="button"
            :aria-label="`关闭 ${tab.title}`"
            @click.stop="handleClose(tab.key)"
          >
            ×
          </span>
        </button>
      </div>
    </div>

    <NDropdown
      :show="contextVisible"
      :options="contextOptions"
      placement="bottom-start"
      :x="contextX"
      :y="contextY"
      @select="handleContextSelect"
      @clickoutside="contextVisible = false"
    />
  </div>
</template>

<style scoped>
.tab-bar {
  flex: none;
  height: 40px;
  padding: 0 12px;
  background: #f8faff;
  box-shadow: inset 0 -1px 0 #e5eaf3;
}

.tab-bar__scroll {
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}

.tab-bar__list {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  width: max-content;
}

.tab {
  position: relative;
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 6px;
  height: 30px;
  border: 0;
  border-radius: 7px;
  padding: 0 8px 0 10px;
  background: transparent;
  color: #62707e;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
}

.tab:hover {
  background: #edf2fa;
  color: #3f4b56;
}

.tab.is-active {
  background: #fff;
  color: #1d4ed8;
  font-weight: 600;
  box-shadow: 0 2px 9px rgba(36, 78, 142, 0.1);
}

.tab.is-active::after {
  position: absolute;
  right: 9px;
  bottom: -5px;
  left: 9px;
  height: 2px;
  border-radius: 2px;
  background: #2563eb;
  content: '';
}

.tab__close {
  display: grid;
  width: 14px;
  height: 14px;
  place-items: center;
  border-radius: 50%;
  font-size: 13px;
  line-height: 1;
}

.tab__close:hover {
  background: #dbe2f0;
  color: #202938;
}

@media (max-width: 640px) {
  .tab-bar {
    padding: 0 8px;
  }

  .tab__title {
    max-width: 88px;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
