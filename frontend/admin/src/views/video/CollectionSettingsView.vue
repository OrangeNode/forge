<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { NAlert, NButton, NCard, NCheckbox, NCheckboxGroup, NForm, NFormItem, NInputNumber, NSelect, NSpace, NSwitch } from 'naive-ui'
import { videoApi, type SettingsInput } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { fieldLabels } from './video-options'
import './video.css'

const session = useSessionStore()
const { busy, act } = useVideoActions()
const form = reactive<SettingsInput & { missingPolicies: Record<string, string>; maxConcurrencyLimit?: number }>({ matchFields: [], updateFields: [], autoPublish: true, missingPolicies: {}, maxConcurrency: 3 })
const matchKeys = ['name', 'year', 'category', 'director', 'area', 'language', 'actors']
const missingFields = [
  { key: 'name', label: '名称', required: true }, { key: 'category', label: '分类未映射或停用', required: true },
  { key: 'playUrls', label: '播放地址', required: true }, { key: 'year', label: '年份' },
  { key: 'area', label: '地区' }, { key: 'language', label: '语言' }, { key: 'director', label: '导演' },
  { key: 'actors', label: '演员' }, { key: 'posterUrl', label: '封面' }, { key: 'description', label: '简介' },
]
const missingOptions = [{ value: 'PENDING', label: '进入待确认' }, { value: 'SKIP', label: '跳过本条数据' }, { value: 'ALLOW', label: '允许为空入库' }]

/**
 * 读取持久化的全局采集设置。
 */
async function load() {
  await act(async () => {
    Object.assign(form, await videoApi.settings())
    form.missingPolicies = { ...(form.missingPolicies ?? {}) }
  })
}

/**
 * 未显式配置的策略跟随去重条件，保留历史设置语义。
 */
function missingValue(key: string, required?: boolean) {
  return form.missingPolicies[key] ?? (required || form.matchFields.includes(key) ? 'PENDING' : 'ALLOW')
}

/**
 * 名称强制参与匹配，修改只对新运行生效。
 */
async function save() {
  if (busy.value) return
  const fields = Array.from(new Set(['name', ...form.matchFields]))
  await act(() => videoApi.saveSettings({ ...form, matchFields: fields }), '采集设置已保存')
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
        title="视频采集设置"
        description="去重条件、主要来源更新规则与并发采集数"
        icon="settings"
      >
        <template #actions>
          <NButton
            secondary
            type="primary"
            :loading="busy"
            @click="load"
          >
            刷新
          </NButton><NButton
            v-if="session.hasPermission('video:settings:edit')"
            type="primary"
            :loading="busy"
            @click="save"
          >
            保存设置
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NAlert
          type="info"
          :bordered="false"
          class="video-filters"
        >
          去重条件必须全部满足。缺失信息按下方策略处理；允许为空的去重字段缺失时独立新建影片，不自动跨站合并。多候选仍进入待确认。
        </NAlert>
        <NForm
          label-placement="top"
          :disabled="!session.hasPermission('video:settings:edit') || busy"
        >
          <NFormItem label="影片去重条件">
            <NCheckboxGroup v-model:value="form.matchFields">
              <NSpace>
                <NCheckbox
                  v-for="key in matchKeys"
                  :key="key"
                  :value="key"
                  :disabled="key === 'name'"
                >
                  {{ fieldLabels[key] }}
                </NCheckbox>
              </NSpace>
            </NCheckboxGroup>
          </NFormItem>
          <NFormItem label="允许主要来源更新的资料">
            <NCheckboxGroup v-model:value="form.updateFields">
              <NSpace>
                <NCheckbox
                  v-for="(label, key) in fieldLabels"
                  :key="key"
                  :value="key"
                >
                  {{ label }}
                </NCheckbox>
              </NSpace>
            </NCheckboxGroup>
          </NFormItem>
          <NFormItem label="最大并发采集数">
            <NSpace
              vertical
              :size="4"
            >
              <NInputNumber
                v-model:value="form.maxConcurrency"
                :min="1"
                :max="form.maxConcurrencyLimit"
                :precision="0"
                style="width: 160px"
              />
              <span class="video-muted">不同资源站同时采集的最多个数（上限 {{ form.maxConcurrencyLimit }}）；同一资源站始终按页顺序采集，断点续采不受影响。保存后下一次领取任务时生效。</span>
            </NSpace>
          </NFormItem>
          <NFormItem label="新影片自动上架">
            <NSwitch v-model:value="form.autoPublish" />
          </NFormItem>
          <NFormItem
            v-for="field in missingFields"
            :key="field.key"
            :label="field.key === 'category' ? '分类未映射或停用时' : `${field.label}缺失时`"
          >
            <NSelect
              :value="missingValue(field.key, field.required)"
              :options="missingOptions.filter(option => !field.required || option.value !== 'ALLOW')"
              style="max-width: 420px"
              @update:value="form.missingPolicies[field.key] = $event"
            />
          </NFormItem>
        </NForm>
        <NAlert
          type="default"
          :bordered="false"
        >
          多项缺失时优先跳过，其次待确认。名称、有效分类和播放地址是新影片入库必需信息。未显式配置的已关联来源继续保留旧资料和地址，允许为空不会清空旧值。设置用于新运行；待确认页批量采集使用最新设置。
        </NAlert>
      </div>
    </NCard>
  </PageBody>
</template>
