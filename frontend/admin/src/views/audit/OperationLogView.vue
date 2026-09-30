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
  type SelectGroupOption,
  type SelectOption,
} from 'naive-ui'
import { h, onMounted, reactive, ref, type CSSProperties, type VNodeChild } from 'vue'

import { fetchOperationLogs, type OperationLog } from '@/api/audit'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import { isBusinessCode, resolveErrorMessage } from '@/utils/error-message'

/**
 * 操作日志页。
 *
 * 只读查询审计记录：按操作者名称、动作代码、结果码与时间范围筛选，
 * 结果码 0 用绿色标签、非 0 用红色标签，traceId 展示前 8 位并可复制完整值。
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
 * 当前页的操作日志。
 */
const records = ref<OperationLog[]>([])

/**
 * 列表加载状态。
 */
const loading = ref(false)

/**
 * 行内筛选条件。
 */
const filters = reactive({
  operatorName: '',
  action: '',
  resultCode: null as string | null,
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
 * 结果码筛选项。
 *
 * 接口按单个结果码精确匹配，因此列出协议中定义的取值，不做“任意非 0”的聚合筛选；
 * 需要聚合筛选时要先扩展查询参数。
 */
const resultCodeOptions: Array<SelectGroupOption | SelectOption> = [
  {
    type: 'group',
    label: '成功',
    key: 'success',
    children: [{ label: '0 · 成功', value: '0' }],
  },
  {
    type: 'group',
    label: '失败',
    key: 'failure',
    children: [
      { label: '400 · 参数错误', value: '400' },
      { label: '401 · 未认证', value: '401' },
      { label: '403 · 无权限', value: '403' },
      { label: '404 · 资源不存在', value: '404' },
      { label: '409 · 状态冲突', value: '409' },
      { label: '413 · 超出大小限制', value: '413' },
      { label: '415 · 媒体类型不支持', value: '415' },
      { label: '429 · 触发限流', value: '429' },
      { label: '500 · 内部错误', value: '500' },
      { label: '503 · 依赖不可用', value: '503' },
    ],
  },
]

/**
 * 表格列定义。
 */
const columns: DataTableColumns<OperationLog> = [
  { title: '时间', key: 'createdAt', width: 190, render: (row) => formatTime(row.createdAt) },
  {
    title: '操作者',
    key: 'operatorName',
    width: 150,
    ellipsis: { tooltip: true },
    render: (row) => row.operatorName || '系统',
  },
  { title: '动作', key: 'action', minWidth: 200, ellipsis: { tooltip: true }, render: (row) => row.action || '—' },
  { title: '对象', key: 'resource', minWidth: 200, ellipsis: { tooltip: true }, render: (row) => resourceSummary(row) },
  { title: '结果码', key: 'resultCode', width: 110, render: (row) => renderResultCode(row.resultCode) },
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
 * 拼接审计对象的展示文案。
 *
 * @param record 操作日志记录
 * @returns 形如 admin / 12 的文案；没有对象信息时返回占位符
 */
function resourceSummary(record: OperationLog): string {
  const resourceType = record.resourceType ?? ''
  const resourceId = record.resourceId ?? ''
  if (!resourceType && !resourceId) {
    return '—'
  }
  if (!resourceId) {
    return resourceType
  }
  return `${resourceType || '对象'} / ${resourceId}`
}

/**
 * 结果码标签类型：0 为成功，其余为失败。
 *
 * @param code 业务结果码
 * @returns Naive UI 标签类型
 */
function resultTagType(code: number): 'success' | 'error' {
  return code === 0 ? 'success' : 'error'
}

/**
 * 渲染结果码标签。
 *
 * @param code 业务结果码
 * @returns 结果码标签；缺失时返回占位符
 */
function renderResultCode(code: number | undefined): VNodeChild {
  if (typeof code !== 'number') {
    return '—'
  }
  return h(NTag, { size: 'small', bordered: false, type: resultTagType(code) }, { default: () => String(code) })
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
 * 分页查询操作日志。
 */
async function loadRecords(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchOperationLogs({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      operatorName: filters.operatorName.trim() || undefined,
      action: filters.action.trim() || undefined,
      resultCode: filters.resultCode ?? undefined,
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
 * 处理结果码下拉的变化。
 *
 * @param value 下拉选中的值，字符串与数组都视为未选择
 */
function handleResultCodeChange(value: string | number | Array<string | number> | null): void {
  filters.resultCode = typeof value === 'number' ? String(value) : typeof value === 'string' ? value : null
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
  filters.operatorName = ''
  filters.action = ''
  filters.resultCode = null
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
 * @param record 操作日志记录
 * @returns 行键
 */
function rowKey(record: OperationLog): string {
  return record.id ?? ''
}

onMounted(() => {
  void loadRecords()
})
</script>

<template>
  <PageBody>
    <NCard
      class="log-card"
      :bordered="false"
    >
      <PageHeader
        title="操作日志"
        description="查看后台操作记录、执行结果与追踪信息"
        icon="clipboard"
      >
        <template #actions>
          <NButton
            class="action-button"
            secondary
            type="primary"
            :loading="loading"
            @click="loadRecords"
          >
            <template #icon>
              <AppIcon
                name="refresh"
                :size="16"
              />
            </template>刷新列表
          </NButton>
        </template>
      </PageHeader>
      <NForm
        inline
        label-placement="left"
        label-align="left"
        :show-feedback="false"
        class="filters"
        @submit.prevent
      >
        <NFormItem label="操作者">
          <NInput
            v-model:value="filters.operatorName"
            clearable
            class="filters__input"
            placeholder="按操作者名称模糊匹配"
            @keyup.enter="handleSearch"
          />
        </NFormItem>
        <NFormItem label="动作">
          <NInput
            v-model:value="filters.action"
            clearable
            class="filters__input"
            placeholder="例如 system:admin:create"
            @keyup.enter="handleSearch"
          />
        </NFormItem>
        <NFormItem label="结果码">
          <NSelect
            class="filters__select"
            clearable
            placeholder="全部结果"
            :value="filters.resultCode"
            :options="resultCodeOptions"
            @update:value="handleResultCodeChange"
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
          :scroll-x="1200"
          flex-height
          class="log-table"
          @update:page="handlePageChange"
          @update:page-size="handlePageSizeChange"
        >
          <template #empty>
            暂无操作日志
          </template>
        </NDataTable>
      </div>
    </NCard>
  </PageBody>
</template>

<style scoped>
.page-body {
  --of-line: #eef1f6;
  overflow: hidden;
}

.log-card {
  height: 100%;
  min-height: 0;
}

.log-card :deep(.n-card-content) {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.filters {
  flex: none;
  margin-bottom: 4px;
}

.filters__input {
  width: 200px;
}

.filters__select {
  width: 180px;
}

.table-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-top: 1px solid var(--of-line);
  padding-top: 12px;
}

.log-table {
  flex: 1;
  min-height: 0;
}

@media (max-width: 640px) {
  .filters {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }

  .filters :deep(.n-form-item) {
    display: grid;
    width: 100%;
    grid-template-columns: 68px minmax(0, 1fr);
    margin: 0;
  }

  .filters :deep(.n-form-item-label) {
    grid-column: 1;
    padding: 0 8px 0 0;
  }

  .filters :deep(.n-form-item-blank) {
    grid-column: 2;
    min-width: 0;
  }

  .filters :deep(.n-form-item:last-child .n-form-item-blank) {
    grid-column: 2;
  }

  .filters :deep(.n-date-picker) {
    width: 100%;
  }

  .filters__input,
  .filters__select {
    width: 100%;
  }
}
</style>
