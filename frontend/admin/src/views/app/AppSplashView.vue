<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NCard, NDataTable, NDatePicker, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NRadioButton, NRadioGroup, NSelect, NSpace, NSwitch, NTag, type DataTableColumns } from 'naive-ui'
import { appConfigApi, imageSrc, windowText, type Splash } from '@/api/app-config'
import { videoApi } from '@/api/video'
import ImageFilePicker from '@/components/ImageFilePicker.vue'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const rows = ref<Splash[]>([])
const show = ref(false)
const editingId = ref<string>()
const preview = ref('')
const form = reactive({ name: '', imageFileId: '', durationSeconds: 5, skippable: true, startAt: null as number | null, endAt: null as number | null, enabled: true, sortNo: 0, targetType: 'NONE', targetValue: null as string | null })
const videoSelect = ref<{ label: string; value: string }[]>([])

/**
 * 按片名搜索影片作为点击跳转目标。
 */
async function searchVideos(keyword: string) {
  if (!keyword) return
  await act(async () => {
    const page = await videoApi.videos({ pageNum: 1, pageSize: 20, keyword })
    videoSelect.value = page.records.map(video => ({ label: `${video.name}${video.year ? `（${video.year}）` : ''}`, value: video.id }))
  })
}

/**
 * 列表中的跳转说明。
 */
function targetText(row: Splash): string {
  if (row.targetType === 'VIDEO') return `影片：${row.targetName ?? '影片已删除'}`
  if (row.targetType === 'URL') return `链接：${row.targetValue}`
  return '不跳转'
}

/**
 * 读取开屏列表。
 */
async function load() {
  await act(async () => { rows.value = await appConfigApi.splashes() })
}

/**
 * 打开新增或编辑表单。
 */
function edit(row?: Splash) {
  editingId.value = row?.id
  preview.value = imageSrc(row?.imageUrl)
  Object.assign(form, {
    name: row?.name ?? '', imageFileId: row?.imageFileId ?? '', durationSeconds: row?.durationSeconds ?? 5,
    skippable: row?.skippable ?? true, startAt: row?.startAt ? new Date(row.startAt).getTime() : null,
    endAt: row?.endAt ? new Date(row.endAt).getTime() : null, enabled: row?.enabled ?? true, sortNo: row?.sortNo ?? 0,
    targetType: row?.targetType ?? 'NONE', targetValue: row?.targetValue ?? null,
  })
  videoSelect.value = row?.targetType === 'VIDEO' && row.targetValue ? [{ label: row.targetName ?? row.targetValue, value: row.targetValue }] : []
  show.value = true
}

/**
 * 保存开屏，时间按带时区的 ISO 8601 提交。
 */
async function save() {
  if (busy.value) return
  const ok = await act(() => appConfigApi.saveSplash(editingId.value, {
    ...form,
    targetValue: form.targetType === 'NONE' ? undefined : form.targetValue?.trim() || undefined,
    startAt: form.startAt ? new Date(form.startAt).toISOString() : undefined,
    endAt: form.endAt ? new Date(form.endAt).toISOString() : undefined,
  }), '开屏已保存')
  if (ok) { show.value = false; await load() }
}

/**
 * 逻辑删除开屏。
 */
async function remove(id: string) {
  if (await act(() => appConfigApi.deleteSplash(id), '开屏已删除')) await load()
}

const columns: DataTableColumns<Splash> = [
  { title: '封面', key: 'imageUrl', width: 120, render: row => h('img', { src: imageSrc(row.imageUrl), alt: row.name, style: 'width:96px;height:54px;object-fit:cover;border-radius:6px' }) },
  { title: '名称', key: 'name', minWidth: 140 },
  { title: '停留', key: 'durationSeconds', width: 80, render: row => `${row.durationSeconds} 秒` },
  { title: '可跳过', key: 'skippable', width: 80, render: row => row.skippable ? '是' : '否' },
  { title: '点击跳转', key: 'targetType', minWidth: 200, ellipsis: { tooltip: true }, render: row => targetText(row) },
  { title: '生效时间', key: 'startAt', minWidth: 260, render: row => windowText(row.startAt, row.endAt) },
  { title: '顺序', key: 'sortNo', width: 70 },
  { title: '状态', key: 'active', width: 100, render: row => h(NTag, { type: row.active ? 'success' : 'default', bordered: false }, () => row.active ? '生效中' : (row.enabled ? '不在时段' : '停用')) },
  { title: '操作', key: 'actions', width: 150, render: row => h(NSpace, {}, () => [
    session.hasPermission('app:splash:edit') ? h(NButton, { size: 'small', onClick: () => edit(row) }, () => '编辑') : null,
    session.hasPermission('app:splash:delete') ? h(NPopconfirm, { onPositiveClick: () => remove(row.id) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '删除'), default: () => '确认删除该开屏？' }) : null,
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
        title="开屏设置"
        description="App 启动时展示的开屏广告；可设置点击跳转影片或外部链接，多条同时生效时取顺序最小的一条"
        icon="file"
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
            v-if="session.hasPermission('app:splash:edit')"
            type="primary"
            @click="edit()"
          >
            新增开屏
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NDataTable
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :scroll-x="1080"
        />
      </div>
    </NCard>
    <NModal
      v-model:show="show"
      preset="card"
      :title="editingId ? '编辑开屏' : '新增开屏'"
      class="video-modal"
    >
      <NForm label-placement="top">
        <div class="video-form-grid">
          <NFormItem
            label="名称"
            :feedback="errors.name"
            :validation-status="errors.name ? 'error' : undefined"
          >
            <NInput
              v-model:value="form.name"
              maxlength="64"
            />
          </NFormItem>
          <NFormItem label="顺序（越小越优先）">
            <NInputNumber
              v-model:value="form.sortNo"
              :min="0"
            />
          </NFormItem>
        </div>
        <NFormItem
          label="封面图"
          :feedback="errors.imageFileId"
          :validation-status="errors.imageFileId ? 'error' : undefined"
        >
          <ImageFilePicker
            v-model="form.imageFileId"
            :preview-src="preview"
          />
        </NFormItem>
        <NFormItem label="点击跳转">
          <NRadioGroup
            v-model:value="form.targetType"
            @update:value="form.targetValue = null"
          >
            <NRadioButton value="NONE">
              不跳转
            </NRadioButton>
            <NRadioButton value="VIDEO">
              影片
            </NRadioButton>
            <NRadioButton value="URL">
              外部链接（广告）
            </NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem
          v-if="form.targetType === 'VIDEO'"
          label="影片（输入片名搜索）"
        >
          <NSelect
            v-model:value="form.targetValue"
            filterable
            remote
            clearable
            :options="videoSelect"
            placeholder="输入片名搜索"
            @search="searchVideos"
          />
        </NFormItem>
        <NFormItem
          v-if="form.targetType === 'URL'"
          label="外部链接（http:// 或 https:// 开头，在浏览器打开）"
          :feedback="errors.targetValue"
          :validation-status="errors.targetValue ? 'error' : undefined"
        >
          <NInput
            v-model:value="form.targetValue"
            maxlength="512"
            placeholder="https://"
          />
        </NFormItem>
        <div class="video-form-grid">
          <NFormItem
            label="停留秒数（1 到 30）"
            :feedback="errors.durationSeconds"
            :validation-status="errors.durationSeconds ? 'error' : undefined"
          >
            <NInputNumber
              v-model:value="form.durationSeconds"
              :min="1"
              :max="30"
            />
          </NFormItem>
          <NFormItem label="可跳过">
            <NSwitch v-model:value="form.skippable" />
          </NFormItem>
          <NFormItem label="开始时间（空表示立即）">
            <NDatePicker
              v-model:value="form.startAt"
              type="datetime"
              clearable
            />
          </NFormItem>
          <NFormItem label="结束时间（空表示长期）">
            <NDatePicker
              v-model:value="form.endAt"
              type="datetime"
              clearable
            />
          </NFormItem>
        </div>
        <NFormItem label="启用">
          <NSwitch v-model:value="form.enabled" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="show = false">
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="busy"
            :disabled="!form.imageFileId || !form.name || (form.targetType !== 'NONE' && !form.targetValue)"
            @click="save"
          >
            保存
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
