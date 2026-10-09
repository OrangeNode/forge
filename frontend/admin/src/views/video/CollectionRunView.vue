<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { NAlert, NButton, NCard, NDataTable, NDescriptions, NDescriptionsItem, NForm, NFormItem, NModal, NPopconfirm, NSelect, NSpace, NTabPane, NTabs, type DataTableColumns } from 'naive-ui'
import { videoApi, type Run, type Pending, type Category, type Video, type BatchResult } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { categoryOptions, ranges, statusLabels, videoPageSize, videoPageSizeOptions, videoRefreshMillis } from './video-options'
import './video.css'

const session = useSessionStore()
const route = useRoute()
const { busy, act } = useVideoActions()
const tab = ref('runs')
const rows = ref<Run[]>([])
const pendingRows = ref<Pending[]>([])
const page = ref(1)
const pendingPage = ref(1)
const total = ref(0)
const pendingTotal = ref(0)
const pageSize = ref(videoPageSize)
const status = ref<string | null>(null)
const pendingStatus = ref<string | null>('PENDING')
const detail = ref<Run>()
const detailShow = ref(false)
const resolving = ref<Pending>()
const resolveShow = ref(false)
const resolveAction = ref('BIND')
const targetId = ref<string | null>(null)
const categoryId = ref<string | null>(null)
const candidates = ref<Video[]>([])
const categories = ref<Category[]>([])
const categoryChoices = computed(() => categoryOptions(categories.value))
const runSelection = ref<string[]>([])
const pendingSelection = ref<string[]>([])
const batchScope = ref('SELECTED')
const batchAction = ref('COLLECT')
const batchResult = ref<BatchResult>()
const batchResultShow = ref(false)
const batchAllowed = computed(() => session.hasPermission(tab.value === 'runs' ? 'video:run:operate' : 'video:run:resolve'))
const batchCount = computed(() => batchScope.value === 'ALL'
  ? (tab.value === 'runs' ? total.value : pendingTotal.value)
  : (tab.value === 'runs' ? runSelection.value.length : pendingSelection.value.length))
const batchOptions = computed(() => [
  { value: 'COLLECT', label: tab.value === 'runs' ? '重新采集' : '按最新设置采集入库' },
  ...(tab.value === 'runs' ? [{ value: 'STOP', label: '停止采集' }] : []),
  { value: 'DELETE', label: '删除记录' },
])
const batchDescription = computed(() => {
  const scope = batchScope.value === 'ALL' ? '当前状态筛选的全部记录（包含所有分页）' : '已勾选记录'
  const behavior = batchAction.value === 'DELETE'
    ? (tab.value === 'runs' ? '只删除结束的运行历史，影片及待确认数据保留。活动运行会拒绝删除。' : '删除后重采也会跳过这些来源数据；已入库影片保留。')
    : batchAction.value === 'STOP' ? '当前页提交后停止，已采集的数据保留。'
      : (tab.value === 'runs' ? '按原来源与范围新建采集运行，使用最新设置。同站已有活动运行时会拒绝重复排队。' : '使用保存的来源快照及最新设置重新判断，仍缺少必要信息或存在多个候选时保留待确认。')
  return `${scope}，预计 ${batchCount.value} 条。${behavior}`
})

watch([tab, status, pendingStatus], () => {
  runSelection.value = []
  pendingSelection.value = []
  batchAction.value = 'COLLECT'
})

/**
 * 发送明确的部分或全部范围，展示实际成功数和未执行原因。
 */
async function submitBatch() {
  if (busy.value || !batchCount.value) return
  const request = {
    scope: batchScope.value, action: batchAction.value,
    ids: batchScope.value === 'SELECTED' ? (tab.value === 'runs' ? runSelection.value : pendingSelection.value) : undefined,
    status: (tab.value === 'runs' ? status.value : pendingStatus.value) ?? undefined,
  }
  if (await act(async () => {
    batchResult.value = await (tab.value === 'runs' ? videoApi.batchRuns(request) : videoApi.batchPending(request))
    batchResultShow.value = true
  })) {
    runSelection.value = []
    pendingSelection.value = []
    if (tab.value === 'runs' && batchAction.value === 'DELETE') { detailShow.value = false; detail.value = undefined }
    page.value = 1
    pendingPage.value = 1
    await refresh()
  }
}

/**
 * 清空全部待确认状态记录，服务端逐条复核状态，已确认和已忽略记录保留。
 */
async function clearPending() {
  if (busy.value) return
  if (await act(async () => {
    batchResult.value = await videoApi.batchPending({ scope: 'ALL', action: 'DELETE', status: 'PENDING' })
    batchResultShow.value = true
  })) {
    pendingSelection.value = []
    pendingPage.value = 1
    await loadPending()
  }
}
let timer: ReturnType<typeof setInterval> | undefined
const canStop = (row: Run) => ['QUEUED', 'RUNNING'].includes(row.status)
const canResume = (row: Run) => ['FAILED', 'STOPPED', 'INTERRUPTED'].includes(row.status)

/**
 * 读取运行分页和当前打开运行的持久化进度。
 */
async function loadRuns() {
  await act(async () => {
    const result = await videoApi.runs({ pageNum: page.value, pageSize: pageSize.value, status: status.value ?? undefined })
    rows.value = result.records
    total.value = result.total
    if (detail.value && detailShow.value) detail.value = await videoApi.run(detail.value.id)
  })
}

/**
 * 读取待确认或已处理数据。
 */
async function loadPending() {
  await act(async () => {
    const result = await videoApi.pending({ pageNum: pendingPage.value, pageSize: pageSize.value, status: pendingStatus.value ?? undefined })
    pendingRows.value = result.records
    pendingTotal.value = result.total
  })
}

/**
 * 刷新当前记录页签。
 */
async function refresh() { if (tab.value === 'runs') await loadRuns(); else await loadPending() }

/**
 * 打开运行进度和明确的错误原因。
 */
async function open(row: Run) {
  if (await act(async () => { detail.value = await videoApi.run(row.id) })) detailShow.value = true
}

/**
 * 停止后等待当前页完成提交，不丢弃已采数据。
 */
async function stop(row: Run) { if (await act(() => videoApi.stop(row.id), '停止请求已提交')) await loadRuns() }

/**
 * 从上次提交的页面继续运行。
 */
async function resume(row: Run) { if (await act(() => videoApi.resume(row.id), '采集已重新排队')) await loadRuns() }

/**
 * 打开待确认处理表单，默认优先选择明确候选。
 */
function resolve(row: Pending) {
  resolving.value = row
  candidates.value = row.candidates
  targetId.value = row.candidates[0]?.id ?? null
  categoryId.value = null
  resolveAction.value = row.candidates.length ? 'BIND' : 'CREATE'
  resolveShow.value = true
}

/**
 * 通过影片搜索辅助人工确认，不按名称自动合并。
 */
async function searchVideos(keyword: string) {
  await act(async () => { candidates.value = (await videoApi.videos({ pageNum: 1, pageSize: videoPageSize, keyword })).records })
}

/**
 * 绑定、新建或忽略数据，后端再次校验重复处理和来源唯一性。
 */
async function submitResolve() {
  if (!resolving.value || busy.value) return
  const id = resolving.value.id
  if (await act(() => videoApi.resolve(id, { action: resolveAction.value, videoId: targetId.value ?? undefined, categoryId: categoryId.value ?? undefined }), '采集数据已处理')) {
    resolveShow.value = false; await loadPending()
  }
}

const columns: DataTableColumns<Run> = [
  { type: 'selection', disabled: () => !session.hasPermission('video:run:operate') },
  { title: '运行编号', key: 'id', width: 100 },
  { title: '来源编号', key: 'sourceId', width: 100 },
  { title: '范围', key: 'rangeCode', width: 130, render: row => row.upstreamIds
    ? `搜索勾选 ${row.upstreamIds.split(',').length} 部`
    : ranges.find(r => r.value === row.rangeCode)?.label ?? row.rangeCode },
  { title: '状态', key: 'status', width: 110, render: row => statusLabels[row.status] ?? row.status },
  { title: '页进度', key: 'nextPage', width: 110, render: row => Math.max(0, row.nextPage - 1) + ' / ' + row.pageCount },
  { title: '处理 / 新增 / 更新 / 存疑 / 跳过', key: 'processed', minWidth: 250, render: row => [row.processed, row.inserted, row.updated, row.pending, row.skipped].join(' / ') },
  { title: '开始时间', key: 'startedAt', width: 180, render: row => row.startedAt ? new Date(row.startedAt).toLocaleString() : '等待执行' },
  { title: '操作', key: 'actions', width: 220, render: row => h(NSpace, {}, () => [
    h(NButton, { size: 'small', onClick: () => open(row) }, () => '详情'),
    session.hasPermission('video:run:operate') && canStop(row) ? h(NPopconfirm, { onPositiveClick: () => stop(row) }, { trigger: () => h(NButton, { size: 'small' }, () => '停止'), default: () => '当前页提交后停止，已采数据保留。' }) : null,
    session.hasPermission('video:run:operate') && canResume(row) ? h(NButton, { size: 'small', onClick: () => resume(row) }, () => '继续') : null,
  ]) },
]
const pendingColumns: DataTableColumns<Pending> = [
  { type: 'selection', disabled: () => !session.hasPermission('video:run:resolve') },
  { title: '影片名称', key: 'name', minWidth: 160, render: row => row.name || '名称缺失' },
  { title: '来源 / 原编号', key: 'upstreamId', width: 160, render: row => row.sourceId + ' / ' + row.upstreamId },
  { title: '原因', key: 'reason', minWidth: 250 },
  { title: '状态', key: 'status', width: 100, render: row => statusLabels[row.status] ?? row.status },
  { title: '操作', key: 'actions', width: 100, render: row => row.status === 'PENDING' && session.hasPermission('video:run:resolve')
    ? h(NButton, { size: 'small', onClick: () => resolve(row) }, () => '处理') : '—' },
]
onMounted(async () => {
  await loadRuns()
  if (session.hasPermission("video:run:resolve")) await act(async () => { categories.value = await videoApi.categories() })
  if (typeof route.query.runId === 'string') {
    const id = route.query.runId
    if (await act(async () => { detail.value = await videoApi.run(id) })) detailShow.value = true
  }
  timer = setInterval(() => {
    if (document.visibilityState !== 'visible' || busy.value) return
    const active = ['QUEUED', 'RUNNING', 'STOP_REQUESTED']
    if (tab.value === 'runs' && (rows.value.some(row => active.includes(row.status)) || (detail.value && active.includes(detail.value.status)))) void loadRuns()
  }, videoRefreshMillis)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
<template>
  <PageBody>
    <NCard
      class="video-panel"
      :bordered="false"
    >
      <PageHeader
        title="采集记录"
        description="运行进度、失败原因与待确认影片"
        icon="clipboard"
      >
        <template #actions>
          <NButton
            secondary
            type="primary"
            :loading="busy"
            @click="refresh"
          >
            刷新
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NSpace
          v-if="batchAllowed"
          class="video-filters"
          align="center"
        >
          <NSelect
            v-model:value="batchScope"
            :options="[{ value: 'SELECTED', label: '操作勾选部分' }, { value: 'ALL', label: '操作筛选后的全部' }]"
            style="width: 190px"
            :disabled="busy"
          />
          <NSelect
            v-model:value="batchAction"
            :options="batchOptions"
            style="width: 210px"
            :disabled="busy"
          />
          <NPopconfirm
            :disabled="busy || !batchCount"
            @positive-click="submitBatch"
          >
            <template #trigger>
              <NButton
                :type="batchAction === 'DELETE' ? 'error' : 'primary'"
                :disabled="!batchCount"
                :loading="busy"
              >
                批量执行（{{ batchCount }}）
              </NButton>
            </template>
            {{ batchDescription }}
          </NPopconfirm>
          <span class="video-muted">全部范围包含所有分页；执行前确认操作范围。</span>
        </NSpace>
        <NTabs
          v-model:value="tab"
          @update:value="refresh"
        >
          <NTabPane
            name="runs"
            tab="运行记录"
          >
            <NSpace class="video-filters">
              <NSelect
                v-model:value="status"
                :options="Object.entries(statusLabels).filter(([key]) => !['PENDING', 'RESOLVED', 'IGNORED'].includes(key)).map(([value, label]) => ({ value, label }))"
                placeholder="运行状态"
                clearable
                style="width: 180px"
              /><NButton @click="page = 1; loadRuns()">
                查询
              </NButton>
            </NSpace>
            <NDataTable
              v-model:checked-row-keys="runSelection"
              :columns="columns"
              :data="rows"
              :loading="busy"
              :row-key="row => row.id"
              :scroll-x="1200"
              remote
              :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: videoPageSizeOptions }"
              @update:page="page = $event; loadRuns()"
              @update:page-size="pageSize = $event; page = 1; loadRuns()"
            />
          </NTabPane>
          <NTabPane
            name="pending"
            tab="待确认数据"
          >
            <NSpace class="video-filters">
              <NSelect
                v-model:value="pendingStatus"
                :options="['PENDING', 'RESOLVED', 'IGNORED'].map(value => ({ value, label: statusLabels[value] }))"
                placeholder="处理状态"
                clearable
                style="width: 180px"
              /><NButton @click="pendingPage = 1; loadPending()">
                查询
              </NButton>
              <NPopconfirm
                v-if="session.hasPermission('video:run:resolve')"
                :disabled="busy"
                @positive-click="clearPending"
              >
                <template #trigger>
                  <NButton
                    type="error"
                    secondary
                    :loading="busy"
                  >
                    清空待确认
                  </NButton>
                </template>
                将删除全部“待确认”状态的记录（包含所有分页，不受当前筛选影响），已确认和已忽略记录保留。删除后重采也会跳过这些来源数据，已入库影片不受影响。确定清空？
              </NPopconfirm>
            </NSpace>
            <NDataTable
              v-model:checked-row-keys="pendingSelection"
              :columns="pendingColumns"
              :data="pendingRows"
              :loading="busy"
              :row-key="row => row.id"
              :scroll-x="850"
              remote
              :pagination="{ page: pendingPage, pageSize, itemCount: pendingTotal }"
              @update:page="pendingPage = $event; loadPending()"
            />
          </NTabPane>
        </NTabs>
      </div>
    </NCard>
    <NModal
      v-model:show="batchResultShow"
      preset="card"
      title="批量操作结果"
      class="video-modal"
    >
      <NAlert
        v-if="batchResult"
        :type="batchResult.failures.length ? 'warning' : 'success'"
        :bordered="false"
        class="video-filters"
      >
        共 {{ batchResult.total }} 条，成功 {{ batchResult.succeeded }} 条，未执行 {{ batchResult.failures.length }} 条。
      </NAlert>
      <NDataTable
        v-if="batchResult?.failures.length"
        :columns="[{ title: '记录编号', key: 'id', width: 100 }, { title: '未执行原因', key: 'message' }]"
        :data="batchResult.failures"
        :max-height="360"
      />
    </NModal>
    <NModal
      v-model:show="detailShow"
      preset="card"
      title="采集运行详情"
      class="video-modal"
    >
      <NDescriptions
        v-if="detail"
        bordered
        :column="2"
      >
        <NDescriptionsItem label="运行编号">
          {{ detail.id }}
        </NDescriptionsItem>
        <NDescriptionsItem label="状态">
          {{ statusLabels[detail.status] }}
        </NDescriptionsItem>
        <NDescriptionsItem label="已提交页数">
          {{ detail.nextPage - 1 }} / {{ detail.pageCount }}
        </NDescriptionsItem>
        <NDescriptionsItem label="处理数量">
          {{ detail.processed }}
        </NDescriptionsItem>
        <NDescriptionsItem label="新增 / 更新">
          {{ detail.inserted }} / {{ detail.updated }}
        </NDescriptionsItem>
        <NDescriptionsItem label="存疑 / 跳过">
          {{ detail.pending }} / {{ detail.skipped }}
        </NDescriptionsItem>
      </NDescriptions>
      <NAlert
        v-if="detail?.errorMessage"
        type="error"
        :bordered="false"
        style="margin-top: 16px"
      >
        {{ detail.errorMessage }}
      </NAlert>
      <NAlert
        v-if="detail?.warning"
        type="warning"
        :bordered="false"
        style="margin-top: 16px"
      >
        {{ detail.warning }}
      </NAlert>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="detailShow = false">
            关闭
          </NButton><NButton
            v-if="detail && canStop(detail) && session.hasPermission('video:run:operate')"
            :loading="busy"
            @click="stop(detail)"
          >
            停止
          </NButton><NButton
            v-if="detail && canResume(detail) && session.hasPermission('video:run:operate')"
            type="primary"
            :loading="busy"
            @click="resume(detail)"
          >
            断点继续
          </NButton>
        </NSpace>
      </template>
    </NModal>
    <NModal
      v-model:show="resolveShow"
      preset="card"
      title="确认采集影片"
      class="video-modal"
    >
      <NAlert
        type="warning"
        :bordered="false"
        class="video-filters"
      >
        {{ resolving?.name }}：{{ resolving?.reason }}
      </NAlert>
      <NDescriptions
        v-if="resolving"
        :column="2"
        label-placement="top"
        class="video-filters"
      >
        <NDescriptionsItem label="名称">
          {{ resolving.incoming.name }}
        </NDescriptionsItem>
        <NDescriptionsItem label="年份">
          {{ resolving.incoming.year ?? '未知' }}
        </NDescriptionsItem>
        <NDescriptionsItem label="地区 / 语言">
          {{ resolving.incoming.area ?? '未知' }} / {{ resolving.incoming.language ?? '未知' }}
        </NDescriptionsItem>
        <NDescriptionsItem label="源分类">
          {{ resolving.incoming.upstreamCategoryId ?? '未知' }}
        </NDescriptionsItem>
        <NDescriptionsItem label="导演">
          {{ resolving.incoming.director ?? '未知' }}
        </NDescriptionsItem>
        <NDescriptionsItem label="演员">
          {{ resolving.incoming.actors ?? '未知' }}
        </NDescriptionsItem>
        <NDescriptionsItem label="线路 / 分集地址">
          {{ resolving.incoming.lineCount }} / {{ resolving.incoming.episodeCount }}
        </NDescriptionsItem>
        <NDescriptionsItem label="更新说明">
          {{ resolving.incoming.remarks ?? '未知' }}
        </NDescriptionsItem>
        <NDescriptionsItem
          label="简介"
          :span="2"
        >
          {{ resolving.incoming.description ?? '未提供' }}
        </NDescriptionsItem>
      </NDescriptions>
      <NForm label-placement="top">
        <NFormItem label="处理方式">
          <NSelect
            v-model:value="resolveAction"
            :options="[{ label: '绑定已有影片', value: 'BIND' }, { label: '新建影片', value: 'CREATE' }, { label: '忽略本来源影片', value: 'IGNORE' }]"
          />
        </NFormItem>
        <NFormItem
          v-if="resolveAction === 'BIND'"
          label="目标影片"
        >
          <NSelect
            v-model:value="targetId"
            remote
            filterable
            :options="candidates.map(v => ({ label: v.name + ' · ' + (v.year ?? '年份未知') + ' · ID ' + v.id, value: v.id }))"
            placeholder="输入片名搜索，确认后选择"
            @search="searchVideos"
          />
        </NFormItem>
        <NFormItem
          v-if="resolveAction === 'CREATE'"
          label="本地分类"
        >
          <NSelect
            v-model:value="categoryId"
            :options="categoryChoices"
            filterable
            placeholder="选择有效分类"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="resolveShow = false">
            取消
          </NButton><NButton
            type="primary"
            :loading="busy"
            :disabled="resolveAction === 'BIND' ? !targetId : resolveAction === 'CREATE' ? !categoryId : false"
            @click="submitResolve"
          >
            确认处理
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
