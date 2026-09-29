<script setup lang="ts">
import {
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
  type SelectOption,
} from 'naive-ui'
import { h, onMounted, reactive, ref, type CSSProperties, type VNodeChild } from 'vue'

import { fetchLoginLogs, type LoginLog } from '@/api/audit'
import PageHeader from '@/components/PageHeader.vue'
import { isBusinessCode, resolveErrorMessage } from '@/utils/error-message'

/**
 * 登录日志页。
 *
 * 只读查询登录成功与失败记录：按用户名、结果与时间范围筛选，
 * 失败原因按后端约定的代码映射为中文，traceId 展示前 8 位并可复制完整值。
 */
const message = useMessage()

/**
 * 本页内联渲染使用的等宽样式令牌，颜色与圆角取自主题 calm-tech.ts，集中在此处维护。
 */
const TRACE_STYLE: CSSProperties = {
  padding: '1px 6px',
  border: '1px solid #eef1f6',
  borderRadius: '6px',
  background: '#f7f9fc',
  color: '#3f4b56',
  fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
  fontSize: '12px',
}

/**
 * 登录失败原因的中文说明，与后端写入的分类代码一一对应。
 */
const LOGIN_REASON_TEXT: Record<string, string> = {
  credentials: '凭据错误',
  disabled: '账号已停用',
  rate_limited: '触发限流',
}

/**
 * 当前页的登录日志。
 */
const records = ref<LoginLog[]>([])

/**
 * 列表加载状态。
 */
const loading = ref(false)

/**
 * 行内筛选条件。
 */
const filters = reactive({
  username: '',
  result: null as string | null,
})

/**
 * 时间范围筛选值，未选择时为 null。
 */
const timeRange = ref<number | [number, number] | null>(null)

/**
 * 远端分页状态，页码与每页条数提交给后端。
 */
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
})

/**
 * 登录结果筛选项。
 */
const resultOptions: SelectOption[] = [
  { label: '成功', value: 'success' },
  { label: '失败', value: 'failure' },
]

/**
 * 表格列定义。
 */
const columns: DataTableColumns<LoginLog> = [
  { title: '时间', key: 'createdAt', width: 190, render: (row) => formatTime(row.createdAt) },
  { title: '用户名', key: 'username', minWidth: 150, ellipsis: { tooltip: true }, render: (row) => row.username || '—' },
  { title: '结果', key: 'result', width: 100, render: (row) => renderResult(row.result) },
  {
    title: '失败原因',
    key: 'reason',
    width: 150,
    render: (row) => (row.result === 'success' ? '—' : reasonText(row.reason)),
  },
  {
    title: '来源地址',
    key: 'clientIp',
    width: 170,
    ellipsis: { tooltip: true },
    render: (row) => row.clientIp || '—',
  },
  { title: 'traceId', key: 'traceId', width: 180, render: (row) => renderTraceId(row.traceId) },
]

/**
 * 把接口返回的时间格式化为本地时区文案。
 *
 * @param value 带时区的 ISO 8601 时间，可能为空
 * @returns 本地时间文案；缺值或非法时返回占位符
 */
function formatTime(value: string | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

/**
 * 把时间范围选择器的值转换为带时区的 ISO 8601 字符串。
 *
 * 选择器给出的是本地时区的时间戳，这里统一转换为 UTC 的 ISO 8601，
 * 避免把本地时间误标成 UTC。
 *
 * @param value 起止时间戳（毫秒）；未选择时为 null
 * @returns 起始与结束时间；未选择时返回空对象
 */
function toTimeQuery(value: number | [number, number] | null): { startTime?: string; endTime?: string } {
  if (!Array.isArray(value)) {
    return {}
  }
  const [begin, end] = value
  return { startTime: new Date(begin).toISOString(), endTime: new Date(end).toISOString() }
}

/**
 * 把登录失败原因代码映射为中文说明。
 *
 * @param reason 失败原因代码；成功时为空
 * @returns 中文说明；未知代码按其他原因处理
 */
function reasonText(reason: string | undefined): string {
  if (!reason) {
    return '—'
  }
  return LOGIN_REASON_TEXT[reason] ?? '其他失败原因'
}

/**
 * 渲染登录结果标签。
 *
 * @param result 结果代码：success 成功，failure 失败
 * @returns 结果标签
 */
function renderResult(result: string | undefined): VNodeChild {
  const success = result === 'success'
  return h(
    NTag,
    { size: 'small', bordered: false, type: success ? 'success' : 'error' },
    { default: () => (success ? '成功' : '失败') },
  )
}

/**
 * 复制完整的 traceId。
 *
 * @param traceId 追踪编号
 */
async function copyTraceId(traceId: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(traceId)
    message.success('已复制 traceId')
  } catch {
    message.error('复制失败，请在浏览器中手动选择复制')
  }
}

/**
 * 渲染 traceId：展示前 8 位，悬停可见完整值，并提供复制按钮。
 *
 * @param traceId 追踪编号
 * @returns traceId 单元格内容
 */
function renderTraceId(traceId: string | undefined): VNodeChild {
  if (!traceId) {
    return '—'
  }
  return h('span', { style: { display: 'inline-flex', alignItems: 'center', gap: '6px' } }, [
    h('code', { style: TRACE_STYLE, title: traceId }, traceId.slice(0, 8)),
    h(
      NButton,
      { size: 'tiny', quaternary: true, type: 'primary', onClick: () => void copyTraceId(traceId) },
      { default: () => '复制' },
    ),
  ])
}

/**
 * 统一提示失败信息。
 *
 * 401 已由请求工厂清理会话并跳转登录页，这里不再重复提示；
 * 其余失败只在这一处提示，避免同一次失败出现多处反馈。
 *
 * @param error 捕获到的未知异常
 */
function notifyFailure(error: unknown): void {
  if (isBusinessCode(error, 401)) {
    return
  }
  message.error(resolveErrorMessage(error))
}

/**
 * 分页查询登录日志。
 */
async function loadRecords(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchLoginLogs({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      username: filters.username.trim() || undefined,
      result: filters.result ?? undefined,
      ...toTimeQuery(timeRange.value),
    })
    records.value = result.records
    pagination.itemCount = result.total
  } catch (error) {
    records.value = []
    pagination.itemCount = 0
    notifyFailure(error)
  } finally {
    loading.value = false
  }
}

/**
 * 处理登录结果下拉的变化。
 *
 * @param value 下拉选中的值，只接受 success 与 failure
 */
function handleResultChange(value: string | number | Array<string | number> | null): void {
  filters.result = value === 'success' || value === 'failure' ? value : null
}

/**
 * 处理时间范围的变化。
 *
 * @param value 起止时间戳；清空时为 null
 */
function handleTimeRangeChange(value: number | [number, number] | null): void {
  timeRange.value = value
}

/**
 * 应用筛选条件并回到第一页。
 */
function handleSearch(): void {
  pagination.page = 1
  void loadRecords()
}

/**
 * 重置筛选条件并重新查询。
 */
function handleReset(): void {
  filters.username = ''
  filters.result = null
  timeRange.value = null
  pagination.page = 1
  void loadRecords()
}

/**
 * 处理页码变化。
 *
 * @param page 目标页码
 */
function handlePageChange(page: number): void {
  pagination.page = page
  void loadRecords()
}

/**
 * 处理每页条数变化，并回到第一页。
 *
 * @param pageSize 目标每页条数
 */
function handlePageSizeChange(pageSize: number): void {
  pagination.pageSize = pageSize
  pagination.page = 1
  void loadRecords()
}

/**
 * 表格行键：审计记录 ID。
 *
 * @param record 登录日志记录
 * @returns 行键
 */
function rowKey(record: LoginLog): string {
  return record.id ?? ''
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <div class="log-view">
    <PageHeader
      title="登录日志"
      description="登录成功与失败记录；失败原因按凭据错误、账号停用与触发限流分类展示"
    />

    <NCard :bordered="true">
      <NForm
        inline
        :show-feedback="false"
        class="filters"
        @submit.prevent
      >
        <NFormItem label="用户名">
          <NInput
            v-model:value="filters.username"
            clearable
            class="filters__input"
            placeholder="按用户名模糊匹配"
            @keyup.enter="handleSearch"
          />
        </NFormItem>
        <NFormItem label="结果">
          <NSelect
            class="filters__select"
            clearable
            placeholder="全部结果"
            :value="filters.result"
            :options="resultOptions"
            @update:value="handleResultChange"
          />
        </NFormItem>
        <NFormItem label="时间范围">
          <NDatePicker
            type="datetimerange"
            clearable
            :actions="['clear', 'confirm']"
            :value="timeRange"
            @update:value="handleTimeRangeChange"
          />
        </NFormItem>
        <NFormItem :show-label="false">
          <NSpace :size="8">
            <NButton
              type="primary"
              :loading="loading"
              @click="handleSearch"
            >
              查询
            </NButton>
            <NButton
              quaternary
              @click="handleReset"
            >
              重置
            </NButton>
          </NSpace>
        </NFormItem>
      </NForm>

      <div class="table-wrap">
        <NDataTable
          remote
          size="small"
          :bordered="false"
          :columns="columns"
          :data="records"
          :loading="loading"
          :pagination="pagination"
          :row-key="rowKey"
          :scroll-x="1080"
          @update:page="handlePageChange"
          @update:page-size="handlePageSizeChange"
        >
          <template #empty>
            暂无登录日志
          </template>
        </NDataTable>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.log-view {
  --of-line: #eef1f6;
}

.filters {
  margin-bottom: 4px;
}

.filters__input {
  width: 200px;
}

.filters__select {
  width: 160px;
}

.table-wrap {
  overflow-x: auto;
  border-top: 1px solid var(--of-line);
  padding-top: 12px;
}

@media (max-width: 640px) {
  .filters__input,
  .filters__select {
    width: 100%;
  }
}
</style>
