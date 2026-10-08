<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NModal, NPopconfirm, NSelect, NSpace, NSwitch, NTag, type DataTableColumns, type FormInst } from 'naive-ui'
import { videoApi, type Source, type SourceCategory, type Task, type TaskInput, type CollectInput } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { cronPresets, ranges, videoDefaultCron } from './video-options'
import './video.css'

const session = useSessionStore()
const router = useRouter()
const { busy, errors, act } = useVideoActions()
const rows = ref<Task[]>([])
const sources = ref<Source[]>([])
const sourceOptions = computed(() => sources.value.map(s => ({ label: s.enabled ? s.name : `${s.name}（已停用）`, value: s.id, disabled: !s.enabled })))
const show = ref(false)
const manualShow = ref(false)
const editing = ref<string>()
const formRef = ref<FormInst>()
const form = reactive<TaskInput>({ name: '', sourceId: '', rangeCode: 'DAY', cronExpression: videoDefaultCron, enabled: false })
const manual = reactive<CollectInput>({ sourceId: '', rangeCode: 'DAY' })
const taskCategories = ref<SourceCategory[]>([])
const manualCategories = ref<SourceCategory[]>([])
const previewUrl = ref('')
const lastRun = ref('')
const presetValue = computed(() => cronPresets.find(p => p.value === form.cronExpression.trim().replace(/\s+/g, ' '))?.value ?? null)

/**
 * 读取任务和可选来源。
 */
async function load() { await act(async () => { rows.value = await videoApi.tasks(); sources.value = await videoApi.sources() }) }

/**
 * 打开手动采集弹窗，清除上次运行入口，默认选中第一个可用资源站。
 */
function openManual() {
  lastRun.value = ''
  previewUrl.value = ''
  if (!manual.sourceId) manual.sourceId = sources.value.find(s => s.enabled)?.id ?? ''
  manualShow.value = true
}

/**
 * 打开任务表单；新任务默认停用，可在保存前直接打开。
 */
function edit(row?: Task) {
  editing.value = row?.id
  Object.assign(form, { name: row?.name ?? '', sourceId: row?.sourceId ?? '', rangeCode: row?.rangeCode ?? 'DAY',
    upstreamCategoryId: row?.upstreamCategoryId, cronExpression: row?.cronExpression ?? videoDefaultCron, enabled: row?.enabled ?? false })
  show.value = true
}

/**
 * 提交任务；cron 与启用条件（来源启用、已配置分类映射）由后端校验。
 */
async function save() {
  if (busy.value) return
  if (await act(async () => { await formRef.value?.validate(); await videoApi.saveTask(editing.value, form) }, '任务已保存')) {
    show.value = false; await load()
  }
}

/**
 * 在列表中直接启停任务。
 */
async function toggle(row: Task, enabled: boolean) {
  if (busy.value) return
  if (await act(() => videoApi.saveTask(row.id, { name: row.name, sourceId: row.sourceId, rangeCode: row.rangeCode,
    upstreamCategoryId: row.upstreamCategoryId, cronExpression: row.cronExpression, enabled }), enabled ? '任务已启用' : '任务已停用')) await load()
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
 * 提交持久化采集运行，立即返回进度入口；弹窗本身即确认步骤，不再叠加气泡确认。
 */
async function collect() {
  if (!manual.sourceId || busy.value) return
  await act(async () => { const run = await videoApi.collect(manual); lastRun.value = run.id }, '采集已排队')
}

/**
 * 跳转到采集记录查看本次运行。
 */
function viewRun() {
  manualShow.value = false
  void router.push({ name: 'video-run', query: { runId: lastRun.value } })
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

const columns: DataTableColumns<Task> = [
  { title: '任务', key: 'name', minWidth: 140 },
  { title: '资源站', key: 'sourceId', width: 150, render: row => sources.value.find(s => s.id === row.sourceId)?.name ?? row.sourceId },
  { title: '采集范围', key: 'rangeCode', width: 140, render: row => ranges.find(r => r.value === row.rangeCode)?.label ?? row.rangeCode },
  { title: '执行计划（cron）', key: 'cronExpression', width: 200, render: row => h('span', {}, [
    h('code', {}, row.cronExpression),
    h('span', { class: 'video-muted' }, cronPresets.find(p => p.value === row.cronExpression) ? ' · ' + cronPresets.find(p => p.value === row.cronExpression)?.label : ''),
  ]) },
  { title: '状态', key: 'enabled', width: 90, render: row => session.hasPermission('video:task:edit')
    ? h(NSwitch, { value: row.enabled, size: 'small', disabled: busy.value, onUpdateValue: (value: boolean) => toggle(row, value) })
    : h(NTag, { type: row.enabled ? 'success' : 'default', bordered: false }, () => row.enabled ? '启用' : '停用') },
  { title: '下一次执行', key: 'nextRunAt', minWidth: 170, render: row => row.enabled && row.nextRunAt ? new Date(row.nextRunAt).toLocaleString() : '—' },
  { title: '操作', key: 'actions', width: 150, render: row => h(NSpace, {}, () => [
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
        description="手动采集与 cron 定时采集，采集范围与执行时间独立设置"
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
        执行时间使用标准五段 cron：分 时 日 月 周，按服务器统一调度时区解释。启用前需先在资源站管理中配置分类映射。
      </NAlert>
      <NForm
        ref="formRef"
        :model="form"
        label-placement="top"
        :rules="{
          name: { required: true, message: '请输入任务名称', trigger: 'blur' },
          sourceId: { required: true, message: '请选择资源站', trigger: 'change' },
          cronExpression: { required: true, message: '请输入 cron 表达式', trigger: 'blur' },
        }"
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
              :options="sourceOptions"
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
          <NFormItem
            label="cron 表达式（分 时 日 月 周）"
            path="cronExpression"
            :feedback="errors.cronExpression"
            :validation-status="errors.cronExpression ? 'error' : undefined"
          >
            <NInput
              v-model:value="form.cronExpression"
              placeholder="0 2 * * *"
              class="video-address"
            />
          </NFormItem>
          <NFormItem label="常用预设">
            <NSelect
              :value="presetValue"
              :options="cronPresets"
              placeholder="选择预设填入 cron"
              @update:value="form.cronExpression = $event ?? form.cronExpression"
            />
          </NFormItem>
          <NFormItem label="启用">
            <NSwitch v-model:value="form.enabled" />
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
            :options="sourceOptions"
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
      <NAlert
        v-if="manual.rangeCode === 'ALL' && !lastRun"
        type="warning"
        :bordered="false"
        class="video-filters"
      >
        全量采集会逐页读取资源站全部影片，可能需要较长时间；运行中可在采集记录里停止，已提交的数据保留。
      </NAlert>
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
        运行 {{ lastRun }} 已排队，采集在后台执行。<NButton
          text
          type="primary"
          @click="viewRun"
        >
          查看进度
        </NButton>
      </NAlert>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="manualShow = false">
            关闭
          </NButton><NButton
            type="primary"
            :loading="busy"
            :disabled="!manual.sourceId || !!lastRun"
            @click="collect"
          >
            开始采集
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
