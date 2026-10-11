<script setup lang="ts">
import { NTab, NTabs } from 'naive-ui'
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { collectRouteKeys, MENU_GROUPS } from '@/router/menu-routes'
import { useSessionStore } from '@/stores/session'
import AppAnnouncementView from '@/views/app/AppAnnouncementView.vue'
import AppBannerView from '@/views/app/AppBannerView.vue'
import AppFeatureView from '@/views/app/AppFeatureView.vue'
import AppHotSearchView from '@/views/app/AppHotSearchView.vue'
import AppMarqueeView from '@/views/app/AppMarqueeView.vue'
import AppReleaseView from '@/views/app/AppReleaseView.vue'
import AppSectionView from '@/views/app/AppSectionView.vue'
import AppSplashView from '@/views/app/AppSplashView.vue'

/**
 * App 管理的合并页面：顶部标签页切换组内原有页面，页面本身与权限保持不变。
 *
 * 当前分组取路由名，当前标签取地址参数 tab；只显示当前账号菜单里有的页面，
 * 地址里的标签不存在或没有授权时显示第一个可用标签。
 */
const props = defineProps<{
  /** 合并分组的路由名，对应 MENU_GROUPS 中的 routeName */
  group: string
}>()

/** 组内页面标识到页面组件与标签名的映射。 */
const TAB_VIEWS: Record<string, { label: string; component: Component }> = {
  'app-splash': { label: '开屏设置', component: AppSplashView },
  'app-banner': { label: '首页轮播', component: AppBannerView },
  'app-section': { label: '首页推荐位', component: AppSectionView },
  'app-hot-search': { label: '热搜设置', component: AppHotSearchView },
  'app-announcement': { label: '公告管理', component: AppAnnouncementView },
  'app-marquee': { label: '播放页滚动文字', component: AppMarqueeView },
  'app-feature': { label: '功能开关', component: AppFeatureView },
  'app-release': { label: '版本更新', component: AppReleaseView },
}

const route = useRoute()
const router = useRouter()
const session = useSessionStore()

/** 当前账号可见的标签，按分组配置的顺序排列。 */
const tabs = computed(() => {
  const allowed = collectRouteKeys(session.menus)
  const group = MENU_GROUPS.find((item) => item.routeName === props.group)
  return (group?.tabs ?? []).filter((key) => allowed.has(key) && TAB_VIEWS[key])
})

/** 当前激活的标签标识。 */
const activeTab = computed(() => {
  const requested = typeof route.query.tab === 'string' ? route.query.tab : ''
  return tabs.value.includes(requested) ? requested : (tabs.value[0] ?? '')
})

/**
 * 切换标签：只替换地址参数，刷新或复制地址后仍停在同一标签。
 *
 * @param key 目标标签标识
 */
function handleTabChange(key: string): void {
  void router.replace({ query: { ...route.query, tab: key } })
}
</script>

<template>
  <div class="group-page">
    <NTabs
      class="group-page__tabs"
      type="line"
      :value="activeTab"
      @update:value="handleTabChange"
    >
      <NTab
        v-for="key in tabs"
        :key="key"
        :name="key"
      >
        {{ TAB_VIEWS[key].label }}
      </NTab>
    </NTabs>
    <div class="group-page__body">
      <KeepAlive>
        <component
          :is="TAB_VIEWS[activeTab].component"
          v-if="activeTab"
          :key="activeTab"
        />
      </KeepAlive>
    </div>
  </div>
</template>

<style scoped>
.group-page {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}

.group-page__tabs {
  flex: none;
  padding: 8px 20px 0;
}

.group-page__body {
  flex: 1;
  min-height: 0;
}

@media (max-width: 640px) {
  .group-page__tabs {
    padding: 6px 12px 0;
  }
}
</style>
