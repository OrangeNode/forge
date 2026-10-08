<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NCard, NDataTable, NInput, NModal, NSelect, NSpace, NTag, type DataTableColumns } from 'naive-ui'
import { appConfigApi, type Feedback } from '@/api/app-config'
import { formatTime } from '@/api/member'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { videoPageSize, videoPageSizeOptions } from '@/views/video/video-options'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, act } = useVideoActions()
const rows = ref<Feedback[]>([])
const filters = reactive<{ status: string | null; type: string | null }>({ status: 'PENDING', type: null })
const pagination = reactive({ page: 1, pageSize: videoPageSize, itemCount: 0, showSizePicker: true, pageSizes: videoPageSizeOptions })
const statusOptions = [{ label: '待处理', value: 'PENDING' }, { label: '已处理', value: 'RESOLVED' }]
const typeOptions = [{ label: '播放报错', value: 'PLAYBACK' }, { label: '意见反馈', value: 'GENERAL' }]
const handling = ref<Feedback | null>(null)
const remark = ref('')

/**
 * 按筛选条件读取当前页。
 */
async function load() {
  await act(async () => {
    const page = await appConfigApi.feedbacks({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      status: filters.status ?? undefined,
      type: filters.type ?? undefined,
    })
    rows.value = page.records
    pagination.itemCount = page.total
  })
}

/**
 * 筛选条件变化后回到第一页。
 */
function search() {
  pagination.page = 1
  void load()
}

/**
 * 打开处理对话框。
 */
function openHandle(row: Feedback) {
  handling.value = row
  remark.value = ''
}

/**
 * 标记已处理并刷新列表。
 */
async function resolve() {
  const row = handling.value
  if (!row) return
  if (await act(() => appConfigApi.resolveFeedback(row.id, remark.value.trim()), '已标记为已处理')) {
    handling.value = null
    await load()
  }
}

/**
 * 播放报错的影片、线路与集数合成一行说明。
 */
function target(row: Feedback): string {
  if (!row.videoName) return '—'
  return [row.videoName, row.lineName, row.episodeName].filter(Boolean).join(' · ')
}

const columns: DataTableColumns<Feedback> = [
  { title: '类型', key: 'type', width: 100, render: row => h(NTag, { type: row.type === 'PLAYBACK' ? 'warning' : 'info', bordered: false }, () => row.type === 'PLAYBACK' ? '播放报错' : '意见反馈') },
  { title: '内容', key: 'content', minWidth: 220, ellipsis: { tooltip: true } },
  { title: '影片 / 线路 / 集数', key: 'target', minWidth: 220, ellipsis: { tooltip: true }, render: target },
  { title: '用户名', key: 'memberUsername', width: 160, ellipsis: { tooltip: true } },
  { title: '设备', key: 'deviceName', width: 140, ellipsis: { tooltip: true }, render: row => row.deviceName || '—' },
  { title: '提交时间', key: 'createdAt', width: 170, render: row => formatTime(row.createdAt) },
  { title: '状态', key: 'status', width: 180, ellipsis: { tooltip: true }, render: row => row.status === 'RESOLVED'
    ? `已处理${row.handleRemark ? `：${row.handleRemark}` : ''}`
    : h(NTag, { type: 'error', bordered: false }, () => '待处理') },
  { title: '操作', key: 'actions', width: 90, fixed: 'right', render: row => row.status === 'PENDING' && session.hasPermission('app:feedback:handle')
    ? h(NButton, { size: 'small', type: 'primary', quaternary: true, onClick: () => openHandle(row) }, () => '处理')
    : '—' },
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
        title="反馈处理"
        description="App 用户提交的意见反馈与播放器内的“这集播不了”报错；播放报错带影片、线路与集数"
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
        </template>
      </PageHeader>
      <div class="video-content">
        <NSpace class="video-filters">
          <NSelect
            v-model:value="filters.status"
            :options="statusOptions"
            placeholder="处理状态"
            clearable
            style="width: 130px"
            @update:value="search"
          />
          <NSelect
            v-model:value="filters.type"
            :options="typeOptions"
            placeholder="反馈类型"
            clearable
            style="width: 130px"
            @update:value="search"
          />
        </NSpace>
        <NDataTable
          remote
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :pagination="pagination"
          :scroll-x="1300"
          @update:page="page => { pagination.page = page; load() }"
          @update:page-size="size => { pagination.pageSize = size; pagination.page = 1; load() }"
        />
      </div>
    </NCard>
    <NModal
      :show="handling !== null"
      preset="dialog"
      title="标记已处理"
      positive-text="确认"
      negative-text="取消"
      :loading="busy"
      @positive-click="resolve"
      @negative-click="handling = null"
      @close="handling = null"
    >
      <p v-if="handling">
        {{ handling.content }}
      </p>
      <NInput
        v-model:value="remark"
        type="textarea"
        placeholder="处理备注（可选），如“已停用该线路”"
        :maxlength="255"
        show-count
      />
    </NModal>
  </PageBody>
</template>
