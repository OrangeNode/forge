<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NCard, NDataTable, NInput, NPopconfirm, NSelect, NSpace, NTag, type DataTableColumns } from 'naive-ui'
import { formatTime, memberApi, offlineReasonLabels, type MemberDevice } from '@/api/member'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { videoPageSize, videoPageSizeOptions } from '@/views/video/video-options'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, act } = useVideoActions()
const rows = ref<MemberDevice[]>([])
const filters = reactive<{ username: string; online: string | null }>({ username: '', online: 'true' })
const pagination = reactive({ page: 1, pageSize: videoPageSize, itemCount: 0, showSizePicker: true, pageSizes: videoPageSizeOptions })
const onlineOptions = [{ label: '在线', value: 'true' }, { label: '离线', value: 'false' }]

/**
 * 按筛选条件读取当前页。
 */
async function load() {
  await act(async () => {
    const page = await memberApi.devices({ pageNum: pagination.page, pageSize: pagination.pageSize, username: filters.username || undefined, online: filters.online ?? undefined })
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
 * 强制设备下线，令牌立即失效。
 */
async function offline(row: MemberDevice) {
  if (await act(() => memberApi.offlineDevice(row.id), '设备已下线')) await load()
}

const columns: DataTableColumns<MemberDevice> = [
  { title: '用户名', key: 'memberUsername', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '设备名称', key: 'deviceName', width: 150, ellipsis: { tooltip: true } },
  { title: '设备编号', key: 'deviceCode', width: 180, ellipsis: { tooltip: true } },
  { title: 'IP', key: 'clientIp', width: 130 },
  { title: '状态', key: 'online', width: 140, render: row => row.online ? h(NTag, { type: 'success', bordered: false }, () => '在线') : (offlineReasonLabels[row.offlineReason] ?? '离线') },
  { title: '登录时间', key: 'loginAt', width: 170, render: row => formatTime(row.loginAt) },
  { title: '最近活跃', key: 'lastActiveAt', width: 170, render: row => formatTime(row.lastActiveAt) },
  { title: '操作', key: 'actions', width: 90, fixed: 'right', render: row => row.online && session.hasPermission('member:device:offline')
    ? h(NPopconfirm, { onPositiveClick: () => offline(row) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '下线'), default: () => `确认强制「${row.deviceName}」下线？` })
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
        title="登录设备"
        description="普通用户在各设备上的登录状态；超过同时登录台数时最早登录的设备会被踢出"
        icon="login"
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
          <NInput
            v-model:value="filters.username"
            placeholder="搜索用户名"
            clearable
            style="width: 220px"
            @keyup.enter="search"
            @clear="search"
          />
          <NSelect
            v-model:value="filters.online"
            :options="onlineOptions"
            placeholder="在线状态"
            clearable
            style="width: 130px"
            @update:value="search"
          />
          <NButton
            type="primary"
            @click="search"
          >
            查询
          </NButton>
        </NSpace>
        <NDataTable
          remote
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :pagination="pagination"
          :scroll-x="1220"
          @update:page="page => { pagination.page = page; load() }"
          @update:page-size="size => { pagination.pageSize = size; pagination.page = 1; load() }"
        />
      </div>
    </NCard>
  </PageBody>
</template>
