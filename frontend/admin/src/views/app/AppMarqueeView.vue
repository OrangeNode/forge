<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { NAlert, NButton, NCard, NColorPicker, NForm, NFormItem, NInput, NSwitch } from 'naive-ui'
import { appConfigApi } from '@/api/app-config'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import '@/views/video/video.css'

/** 颜色色板：醒目的红、橙、黄与白，播放页是深色底。 */
const SWATCHES = ['#FF3B30', '#FF6A00', '#FFC107', '#FFFFFF']

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const form = reactive({ enabled: false, text: '', color: SWATCHES[0] })

/**
 * 读取滚动文字设置。
 */
async function load() {
  await act(async () => {
    const value = await appConfigApi.marquee()
    Object.assign(form, { enabled: value.enabled ?? false, text: value.text ?? '', color: value.color ?? SWATCHES[0] })
  })
}

/**
 * 保存滚动文字设置。
 */
async function save() {
  if (busy.value) return
  if (await act(() => appConfigApi.saveMarquee({ ...form, color: form.color.slice(0, 7).toUpperCase() }), '滚动文字已保存')) await load()
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
        title="播放页滚动文字"
        description="显示在手机与电视播放页线路选择上方，循环滚动"
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
            v-if="session.hasPermission('app:marquee:edit')"
            type="primary"
            :loading="busy"
            :disabled="form.enabled && !form.text.trim()"
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
          保存后，App 下一次打开播放页即显示新内容；关闭开关后不显示。
        </NAlert>
        <NForm
          label-placement="top"
          :disabled="!session.hasPermission('app:marquee:edit')"
          style="max-width: 640px"
        >
          <NFormItem label="启用">
            <NSwitch v-model:value="form.enabled" />
          </NFormItem>
          <NFormItem
            label="滚动文字"
            :feedback="errors.text"
            :validation-status="errors.text ? 'error' : undefined"
          >
            <NInput
              v-model:value="form.text"
              maxlength="200"
              show-count
            />
          </NFormItem>
          <NFormItem
            label="文字颜色"
            :feedback="errors.color"
            :validation-status="errors.color ? 'error' : undefined"
          >
            <NColorPicker
              v-model:value="form.color"
              :modes="['hex']"
              :show-alpha="false"
              :swatches="SWATCHES"
              style="max-width: 200px"
            />
          </NFormItem>
          <NFormItem
            v-if="form.text.trim()"
            label="预览"
          >
            <div class="marquee-preview">
              <span :style="{ color: form.color }">{{ form.text }}</span>
            </div>
          </NFormItem>
        </NForm>
      </div>
    </NCard>
  </PageBody>
</template>

<style scoped>
.marquee-preview {
  width: 100%;
  overflow: hidden;
  white-space: nowrap;
  background: #111;
  border-radius: 6px;
  padding: 8px 0;
}

.marquee-preview span {
  display: inline-block;
  padding-left: 100%;
  font-size: 14px;
  animation: marquee-scroll 12s linear infinite;
}

@keyframes marquee-scroll {
  from { transform: translateX(0); }
  to { transform: translateX(-100%); }
}
</style>
