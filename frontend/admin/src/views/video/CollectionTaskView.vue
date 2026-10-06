<script setup lang="ts">
import { h, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NSelect, NSpace, NSwitch, type DataTableColumns, type FormInst } from 'naive-ui'
import { videoApi, type Source, type SourceCategory, type Task, type TaskInput, type CollectInput } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { ranges, videoZone, videoTaskTime, videoIntervalHours } from './video-options'
import './video.css'

const session = useSessionStore()
const router = useRouter()
const { busy, errors, act } = useVideoActions()
const rows = ref<Task[]>([])
const sources = ref<Source[]>([])
const show = ref(false)
const manualShow = ref(false)
const editing = ref<string>()
const formRef = ref<FormInst>()
const form = reactive<TaskInput>({ name: '', sourceId: '', rangeCode: 'DAY', frequency: 'DAILY', intervalHours: videoIntervalHours, weekday: 1, timeOfDay: videoTaskTime, zoneId: videoZone, enabled: false })
const manual = reactive<CollectInput>({ sourceId: '', rangeCode: 'DAY' })
const taskCategories = ref<SourceCategory[]>([])
const manualCategories = ref<SourceCategory[]>([])
const previewUrl = ref('')
const lastRun = ref('')

/**
 * 读取任务和可选来源。
 */
async function load() { await act(async () => { rows.value = await videoApi.tasks(); sources.value = await videoApi.sources() }) }

/**
 * 再次打开手动采集时清除上次运行入口，保留管理员已选择的范围。
 */
function openManual() {
  lastRun.value = ''
  manualShow.value = true
}

/**
 * 打开任务表单，新任务默认停用。
 */
function edit(row?: Task) {
  editing.value = row?.id
  Object.assign(form, { name: row?.name ?? '', sourceId: row?.sourceId ?? '', rangeCode: row?.rangeCode ?? 'DAY',
    upstreamCategoryId: row?.upstreamCategoryId, frequency: row?.frequency ?? 'DAILY', intervalHours: row?.intervalHours ?? videoIntervalHours,
    weekday: row?.weekday ?? 1, timeOfDay: row?.timeOfDay ?? videoTaskTime, zoneId: row?.zoneId ?? videoZone, enabled: row?.enabled ?? false })
  show.value = true
}

/**
 * 提交调度设置，运行频率和采集窗口互不绑定。
 */
async function save() {
  if (busy.value) return
  if (await act(async () => { await formRef.value?.validate(); await videoApi.saveTask(editing.value, form) }, '任务已保存')) {
    show.value = false; await load()
  }
}

/**
 * 生成并显示源站 URL，不执行采集。
 */
async function preview() {
  if (!manual.sourceId || busy.value) return
  await act(async () => { previewUrl.value = (await videoApi.preview(manual)).url })
}

/**
 * 复制本次生成的源站请求链接。
 */
async function copy() { await act(() => navigator.clipboard.writeText(previewUrl.value), '链接已复制') }

/**
 * 提交持久化采集运行，立即返回进度入口。
 */
async function collect() {
  if (!manual.sourceId || busy.value) return
  await act(async () => { const run = await videoApi.collect(manual); lastRun.value = run.id }, '采集已排队')
}

/**
 * 删除没有活动运行的定时任务。
 */
async function remove(id: string) { if (await act(() => videoApi.deleteTask(id), '任务已删除')) await load() }

/**
 * 来源变化时加载分类缓存，不隐式请求资源站。
 */
async function sourceCategories(id: string, target: 'task' | 'manual') {
  await act(async () => {
    const list = id ? await videoApi.sourceCategories(id) : []
    if (target === 'task') taskCategories.value = list
    else manualCategories.value = list
  })
}
watch(() => form.sourceId, id => { void sourceCategories(id, 'task') })
watch(() => manual.sourceId, id => { manual.upstreamCategoryId = undefined; void sourceCategories(id, 'manual') })
watch(() => [manual.sourceId, manual.rangeCode, manual.upstreamCategoryId], () => { previewUrl.value = ''; lastRun.value = '' })

const frequencies = [{ label: '每隔若干小时', value: 'INTERVAL' }, { label: '每天指定时间', value: 'DAILY' }, { label: '每周指定时间', value: 'WEEKLY' }]
const weekdays = ['一', '二', '三', '四', '五', '六', '日'].map((label, index) => ({ label: '星期' + label, value: index + 1 }))
const columns: DataTableColumns<Task> = [
  { title: '任务', key: 'name', minWidth: 140 },
  { title: '资源站', key: 'sourceId', width: 150, render: row => sources.value.find(s => s.id === row.sourceId)?.name ?? row.sourceId },
  { title: '采集范围', key: 'rangeCode', width: 150, render: row => ranges.find(r => r.value === row.rangeCode)?.label ?? row.rangeCode },
  { title: '执行频率', key: 'frequency', width: 150, render: row => row.frequency === 'INTERVAL' ? '每 ' + row.intervalHours + ' 小时' : (row.frequency === 'WEEKLY' ? '星期' + row.weekday + ' ' : '每天 ') + row.timeOfDay },
  { title: '状态', key: 'enabled', width: 90, render: row => row.enabled ? '启用' : '停用' },
  { title: '下一次执行', key: 'nextRunAt', minWidth: 180, render: row => row.enabled && row.nextRunAt ? new Date(row.nextRunAt).toLocaleString() : '—' },
  { title: '操作', key: 'actions', width: 160, render: row => h(NSpace, {}, () => [
    session.hasPermission('video:task:edit') ? h(NButton, { size: 'small', onClick: () => edit(row) }, () => '编辑') : null,
    session.hasPermission('video:task:delete') ? h(NPopconfirm, { onPositiveClick: () => remove(row.id) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '删除'), default: () => '确认删除任务？' }) : null,
  ]) },
]
onMounted(load)
</script>
<template>
  <PageBody>
    <NCard
      class="video-panel"
      :bordered="false"
    >
      <PageHeader
        title="采集任务"
        description="手动采集与内部定时，日/周/全部范围独立设置"
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
            v-if="session.hasPermission('video:task:collect')"
            @click="openManual"
          >
            手动采集
          </NButton>
          <NButton
            v-if="session.hasPermission('video:task:edit')"
            type="primary"
            @click="edit()"
          >
            新增定时任务
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NDataTable
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :scroll-x="1000"
        />
      </div>
    </NCard>
    <NModal
      v-model:show="show"
      preset="card"
      :title="editing ? '编辑定时任务' : '新增定时任务'"
      class="video-modal"
    >
      <NAlert
        type="info"
        :bordered="false"
        class="video-filters"
      >
        新任务默认停用。先确认分类映射，再编辑并启用；采集不会通过外部匿名链接触发。
      </NAlert>
      <NForm
        ref="formRef"
        :model="form"
        label-placement="top"
        :rules="{ name: { required: true, message: '请输入任务名称', trigger: 'blur' }, sourceId: { required: true, message: '请选择资源站', trigger: 'change' } }"
      >
        <div class="video-form-grid">
          <NFormItem
            label="任务名称"
            path="name"
            :feedback="errors.name"
          >
            <NInput v-model:value="form.name" />
          </NFormItem>
          <NFormItem
            label="资源站"
            path="sourceId"
          >
            <NSelect
              :value="form.sourceId || null"
              placeholder="选择资源站"
              :options="sources.map(s => ({ label: s.name, value: s.id, disabled: !s.enabled }))"
              @update:value="form.sourceId = $event ?? ''"
            />
          </NFormItem>
          <NFormItem label="采集范围">
            <NSelect
              v-model:value="form.rangeCode"
              :options="ranges"
            />
          </NFormItem>
          <NFormItem label="源分类（留空采集全部分类）">
            <NSelect
              v-model:value="form.upstreamCategoryId"
              clearable
              filterable
              :options="taskCategories.map(c => ({ label: c.name, value: c.upstreamId }))"
            />
          </NFormItem>
          <NFormItem label="执行频率">
            <NSelect
              v-model:value="form.frequency"
              :options="frequencies"
            />
          </NFormItem>
          <NFormItem
            v-if="form.frequency === 'INTERVAL'"
            label="间隔小时"
          >
            <NInputNumber
              :value="form.intervalHours"
              :min="1"
              :max="8760"
              @update:value="form.intervalHours = $event ?? undefined"
            />
          </NFormItem>
          <NFormItem
            v-if="form.frequency === 'WEEKLY'"
            label="星期"
          >
            <NSelect
              v-model:value="form.weekday"
              :options="weekdays"
            />
          </NFormItem>
          <NFormItem
            label="执行时间（HH:mm）"
            :feedback="errors.timeOfDay"
          >
            <NInput v-model:value="form.timeOfDay" />
          </NFormItem>
          <NFormItem label="时区">
            <NInput v-model:value="form.zoneId" />
          </NFormItem>
          <NFormItem label="启用">
            <NSwitch
              v-model:value="form.enabled"
              :disabled="!editing"
            />
          </NFormItem>
        </div>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="show = false">
            取消
          </NButton><NButton
            type="primary"
            :loading="busy"
            @click="save"
          >
            保存任务
          </NButton>
        </NSpace>
      </template>
    </NModal>
    <NModal
      v-model:show="manualShow"
      preset="card"
      title="手动采集"
      class="video-modal"
    >
      <NForm label-placement="top">
        <NFormItem label="资源站">
          <NSelect
            :value="manual.sourceId || null"
            placeholder="选择资源站"
            :options="sources.map(s => ({ label: s.name, value: s.id, disabled: !s.enabled }))"
            @update:value="manual.sourceId = $event ?? ''"
          />
        </NFormItem>
        <NFormItem label="采集范围">
          <NSelect
            v-model:value="manual.rangeCode"
            :options="ranges"
          />
        </NFormItem>
        <NFormItem label="源分类（留空为全部分类）">
          <NSelect
            v-model:value="manual.upstreamCategoryId"
            clearable
            filterable
            :options="manualCategories.map(c => ({ label: c.name, value: c.upstreamId }))"
          />
        </NFormItem>
      </NForm>
      <NSpace class="video-filters">
        <NButton
          :disabled="!manual.sourceId"
          :loading="busy"
          @click="preview"
        >
          生成采集链接
        </NButton><NButton
          :disabled="!previewUrl"
          @click="copy"
        >
          复制链接
        </NButton>
      </NSpace>
      <NInput
        v-if="previewUrl"
        :value="previewUrl"
        readonly
        type="textarea"
        :rows="3"
        class="video-address"
      />
      <NAlert
        v-if="lastRun"
        type="success"
        :bordered="false"
        style="margin-top: 16px"
      >
        运行 {{ lastRun }} 已排队。<NButton
          text
          type="primary"
          @click="router.push({ name: 'video-run', query: { runId: lastRun } }); manualShow = false"
        >
          查看进度
        </NButton>
      </NAlert>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="manualShow = false">
            关闭
          </NButton><NPopconfirm @positive-click="collect">
            <template #trigger>
              <NButton
                type="primary"
                :loading="busy"
                :disabled="!manual.sourceId || !!lastRun"
              >
                开始采集
              </NButton>
            </template>确认采集所选范围？全量任务可能需要较长时间。
          </NPopconfirm>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
