<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NDatePicker, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NRadioButton, NRadioGroup, NSelect, NSpace, NSwitch, NTag, type DataTableColumns } from 'naive-ui'
import { appConfigApi, windowText, type Announcement } from '@/api/app-config'
import { videoApi } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { API_BASE_URL } from '@/api/request'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const rows = ref<Announcement[]>([])
const show = ref(false)
const editingId = ref<string>()
const form = reactive({
  title: '', contentType: 'TEXT', content: '', actionType: 'NONE', actionValue: null as string | null,
  startAt: null as number | null, endAt: null as number | null, enabled: true, sortNo: 0,
})
const videoSelect = ref<{ label: string; value: string }[]>([])

/**
 * HTML 预览：放进不允许脚本的沙箱框架里显示，站内相对地址按接口地址补全。
 */
const previewDoc = computed(() => {
  const body = form.contentType === 'HTML' ? form.content : escapeHtml(form.content).replace(/\n/g, '<br>')
  return `<!doctype html><html><head><meta charset="utf-8"><base href="${API_BASE_URL || location.origin}/">`
    + '<style>body{font:14px/1.6 system-ui,sans-serif;margin:12px;color:#222}img{max-width:100%}</style></head>'
    + `<body>${body}</body></html>`
})

/**
 * 转义纯文本，用于预览。
 */
function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/**
 * 按片名搜索影片作为“去了解”的跳转目标。
 */
async function searchVideos(keyword: string) {
  if (!keyword) return
  await act(async () => {
    const page = await videoApi.videos({ pageNum: 1, pageSize: 20, keyword })
    videoSelect.value = page.records.map(video => ({ label: `${video.name}${video.year ? `（${video.year}）` : ''}`, value: video.id }))
  })
}

/**
 * 列表中的按钮说明。
 */
function actionText(row: Announcement): string {
  if (row.actionType === 'VIDEO') return `去了解 → 影片：${row.actionName ?? '影片已删除'}`
  if (row.actionType === 'URL') return `去了解 → 链接：${row.actionValue}`
  return '只有“知道了”'
}

/**
 * 读取公告列表。
 */
async function load() {
  await act(async () => { rows.value = await appConfigApi.announcements() })
}

/**
 * 打开新增或编辑表单。
 */
function edit(row?: Announcement) {
  editingId.value = row?.id
  Object.assign(form, {
    title: row?.title ?? '', contentType: row?.contentType ?? 'TEXT', content: row?.content ?? '',
    actionType: row?.actionType ?? 'NONE', actionValue: row?.actionValue ?? null,
    startAt: row?.startAt ? new Date(row.startAt).getTime() : null, endAt: row?.endAt ? new Date(row.endAt).getTime() : null,
    enabled: row?.enabled ?? true, sortNo: row?.sortNo ?? 0,
  })
  videoSelect.value = row?.actionType === 'VIDEO' && row.actionValue ? [{ label: row.actionName ?? row.actionValue, value: row.actionValue }] : []
  show.value = true
}

/**
 * 保存公告，时间按带时区的 ISO 8601 提交。
 */
async function save() {
  if (busy.value) return
  const ok = await act(() => appConfigApi.saveAnnouncement(editingId.value, {
    ...form,
    actionValue: form.actionType === 'NONE' ? undefined : form.actionValue?.trim() || undefined,
    startAt: form.startAt ? new Date(form.startAt).toISOString() : undefined,
    endAt: form.endAt ? new Date(form.endAt).toISOString() : undefined,
  }), '公告已保存')
  if (ok) { show.value = false; await load() }
}

/**
 * 逻辑删除公告。
 */
async function remove(id: string) {
  if (await act(() => appConfigApi.deleteAnnouncement(id), '公告已删除')) await load()
}

const columns: DataTableColumns<Announcement> = [
  { title: '标题', key: 'title', minWidth: 160 },
  { title: '格式', key: 'contentType', width: 80, render: row => row.contentType === 'HTML' ? 'HTML' : '纯文本' },
  { title: '按钮', key: 'actionType', minWidth: 200, ellipsis: { tooltip: true }, render: row => actionText(row) },
  { title: '生效时间', key: 'startAt', minWidth: 260, render: row => windowText(row.startAt, row.endAt) },
  { title: '优先级', key: 'sortNo', width: 80 },
  { title: '版本', key: 'revision', width: 70 },
  { title: '状态', key: 'active', width: 100, render: row => h(NTag, { type: row.active ? 'success' : 'default', bordered: false }, () => row.active ? '生效中' : (row.enabled ? '不在时段' : '停用')) },
  { title: '操作', key: 'actions', width: 150, render: row => h(NSpace, {}, () => [
    session.hasPermission('app:announcement:edit') ? h(NButton, { size: 'small', onClick: () => edit(row) }, () => '编辑') : null,
    session.hasPermission('app:announcement:delete') ? h(NPopconfirm, { onPositiveClick: () => remove(row.id) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '删除'), default: () => '确认删除该公告？' }) : null,
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
        title="公告管理"
        description="App 进入首页后弹出的公告；同一条公告每个用户只弹一次，修改标题、内容或按钮后会再弹一次"
        icon="message"
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
            v-if="session.hasPermission('app:announcement:edit')"
            type="primary"
            @click="edit()"
          >
            新增公告
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NAlert
          type="info"
          :bordered="false"
          class="video-filters"
        >
          多条同时生效时，每次进入首页只弹一条：按优先级（数字越小越优先）取用户还没看过的第一条。只改生效时间、开关或优先级不会让看过的用户再看一次。
        </NAlert>
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
      :title="editingId ? '编辑公告' : '新增公告'"
      class="video-modal"
    >
      <NForm label-placement="top">
        <div class="video-form-grid">
          <NFormItem
            label="标题"
            :feedback="errors.title"
            :validation-status="errors.title ? 'error' : undefined"
          >
            <NInput
              v-model:value="form.title"
              maxlength="64"
            />
          </NFormItem>
          <NFormItem label="优先级（越小越优先）">
            <NInputNumber
              v-model:value="form.sortNo"
              :min="0"
            />
          </NFormItem>
        </div>
        <NFormItem label="内容格式">
          <NRadioGroup v-model:value="form.contentType">
            <NRadioButton value="TEXT">
              纯文本
            </NRadioButton>
            <NRadioButton value="HTML">
              HTML
            </NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem
          :label="form.contentType === 'HTML'
            ? 'HTML 内容（只保留段落、换行、加粗、斜体、下划线、标题、列表、引用、分隔线、font 颜色、图片与链接；脚本和样式会被去掉。图片用 http(s) 完整地址或文件管理的外链）'
            : '内容（按原样显示，保留换行）'"
          :feedback="errors.content"
          :validation-status="errors.content ? 'error' : undefined"
        >
          <NInput
            v-model:value="form.content"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 16 }"
            maxlength="20000"
            show-count
            :placeholder="form.contentType === 'HTML' ? '<p>国庆期间<b>正常更新</b></p>' : ''"
          />
        </NFormItem>
        <NFormItem
          v-if="form.content"
          label="预览（App 中的样式会略有不同）"
        >
          <iframe
            :srcdoc="previewDoc"
            sandbox=""
            title="公告预览"
            style="width: 100%; height: 220px; border: 1px solid var(--n-border-color, #ddd); border-radius: 6px; background: #fff"
          />
        </NFormItem>
        <NFormItem label="按钮（“知道了”始终显示）">
          <NRadioGroup
            v-model:value="form.actionType"
            @update:value="form.actionValue = null"
          >
            <NRadioButton value="NONE">
              只有“知道了”
            </NRadioButton>
            <NRadioButton value="VIDEO">
              “去了解”跳影片
            </NRadioButton>
            <NRadioButton value="URL">
              “去了解”打开链接
            </NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem
          v-if="form.actionType === 'VIDEO'"
          label="影片（输入片名搜索）"
        >
          <NSelect
            v-model:value="form.actionValue"
            filterable
            remote
            clearable
            :options="videoSelect"
            placeholder="输入片名搜索"
            @search="searchVideos"
          />
        </NFormItem>
        <NFormItem
          v-if="form.actionType === 'URL'"
          label="外部链接（http:// 或 https:// 开头，手机用浏览器打开；电视打不开时只显示“知道了”）"
          :feedback="errors.actionValue"
          :validation-status="errors.actionValue ? 'error' : undefined"
        >
          <NInput
            v-model:value="form.actionValue"
            maxlength="512"
            placeholder="https://"
          />
        </NFormItem>
        <div class="video-form-grid">
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
            :disabled="!form.title || !form.content.trim() || (form.actionType !== 'NONE' && !form.actionValue)"
            @click="save"
          >
            保存
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
