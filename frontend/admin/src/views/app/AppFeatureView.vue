<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NForm, NFormItem, NInput, NInputNumber, NSwitch } from 'naive-ui'
import { appConfigApi } from '@/api/app-config'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const form = reactive({ vipRequired: true, registrationOpen: true, maxDevices: 2, serviceText: '', noticeText: '' })
const updatedAt = ref('')

/**
 * 读取功能开关。
 */
async function load() {
  await act(async () => {
    const value = await appConfigApi.features()
    Object.assign(form, { ...value, serviceText: value.serviceText ?? '', noticeText: value.noticeText ?? '' })
    updatedAt.value = value.updatedAt ? new Date(value.updatedAt).toLocaleString() : ''
  })
}

/**
 * 保存功能开关。
 */
async function save() {
  if (busy.value) return
  if (await act(() => appConfigApi.saveFeatures(form), '功能开关已保存')) await load()
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
        title="功能开关"
        description="观看权限、注册、同时登录台数与客服公告"
        icon="shield"
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
            v-if="session.hasPermission('app:feature:edit')"
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
          VIP 开关与注册开关在 App 下一次请求时生效；同时登录台数在用户下一次登录时生效，超出时踢出最早登录的设备。{{ updatedAt ? `最近修改：${updatedAt}` : '' }}
        </NAlert>
        <NForm
          label-placement="top"
          :disabled="!session.hasPermission('app:feature:edit')"
          style="max-width: 640px"
        >
          <NFormItem label="VIP 开关（开：只有 VIP 能观看；关：登录即可观看）">
            <NSwitch v-model:value="form.vipRequired" />
          </NFormItem>
          <NFormItem label="开放注册">
            <NSwitch v-model:value="form.registrationOpen" />
          </NFormItem>
          <NFormItem
            label="同一账号同时登录台数（1 到 20）"
            :feedback="errors.maxDevices"
            :validation-status="errors.maxDevices ? 'error' : undefined"
          >
            <NInputNumber
              v-model:value="form.maxDevices"
              :min="1"
              :max="20"
            />
          </NFormItem>
          <NFormItem label="客服文字">
            <NInput
              v-model:value="form.serviceText"
              type="textarea"
              maxlength="512"
              show-count
              placeholder="例如：客服邮箱 / 联系方式"
            />
          </NFormItem>
          <NFormItem label="公告文字">
            <NInput
              v-model:value="form.noticeText"
              type="textarea"
              maxlength="1024"
              show-count
            />
          </NFormItem>
        </NForm>
      </div>
    </NCard>
  </PageBody>
</template>
