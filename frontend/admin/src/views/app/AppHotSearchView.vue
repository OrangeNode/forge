<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { NAlert, NButton, NCard, NDynamicTags, NForm, NFormItem } from 'naive-ui'
import { appConfigApi } from '@/api/app-config'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, act } = useVideoActions()
const form = reactive({ hotWords: [] as string[], blockedWords: [] as string[] })

/**
 * 读取热搜设置。
 */
async function load() {
  await act(async () => Object.assign(form, await appConfigApi.hotSearch()))
}

/**
 * 保存热搜设置，后端去空白去重后回写。
 */
async function save() {
  if (busy.value) return
  await act(async () => Object.assign(form, await appConfigApi.saveHotSearch(form)), '热搜设置已保存')
}
onMounted(load)
</script>

<template>
  <PageBody>
    <NCard
      class="video-panel"
      :bordered="false"
    >
      <PageHeader
        title="热搜设置"
        description="App 搜索页展示的热搜词与屏蔽词"
        icon="activity"
      >
        <template #actions>
          <NButton
            secondary
            type="primary"
            :loading="busy"
            @click="load"
          >
            刷新
          </NButton>
          <NButton
            v-if="session.hasPermission('app:hotsearch:edit')"
            type="primary"
            :loading="busy"
            @click="save"
          >
            保存
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NAlert
          type="info"
          :bordered="false"
          class="video-filters"
        >
          热搜词按填写顺序展示；包含任一屏蔽词（不区分大小写）的热搜词不会返回给 App。修改后 App 下一次请求即生效。
        </NAlert>
        <NForm label-placement="top">
          <NFormItem label="手动热搜词（最多 50 个，每个不超过 32 字）">
            <NDynamicTags
              v-model:value="form.hotWords"
              :max="50"
              :disabled="!session.hasPermission('app:hotsearch:edit')"
            />
          </NFormItem>
          <NFormItem label="屏蔽词（最多 200 个）">
            <NDynamicTags
              v-model:value="form.blockedWords"
              :max="200"
              type="error"
              :disabled="!session.hasPermission('app:hotsearch:edit')"
            />
          </NFormItem>
        </NForm>
      </div>
    </NCard>
  </PageBody>
</template>
