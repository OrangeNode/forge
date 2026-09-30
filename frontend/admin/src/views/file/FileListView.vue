<script setup lang="ts">
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NPopconfirm,
  NSpace,
  NSpin,
  NUpload,
  useMessage,
  type DataTableColumns,
  type UploadCustomRequestOptions,
} from 'naive-ui'
import { computed, h, onMounted, reactive, ref, type VNodeChild } from 'vue'

import {
  deleteFile,
  fetchDefaultStorageConfig,
  fetchFiles,
  uploadFile,
  type FileRecord,
  type StorageConfig,
} from '@/api/files'
import AppIcon from '@/components/AppIcon.vue'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { downloadFile } from '@/utils/download'
import { isBusinessCode, resolveErrorMessage } from '@/utils/error-message'

/**
 * 文件列表页。
 *
 * 只使用后端真实接口：分页查询、上传到当前默认存储方案、按文件 ID 下载与逻辑删除。
 * 上传前按默认存储方案校验扩展名与大小；读取不到方案时不阻止上传，
 * 由页面提示说明最终以后端校验为准。
 */
const session = useSessionStore()
const message = useMessage()

/**
 * 当前页的文件记录。
 */
const records = ref<FileRecord[]>([])

/**
 * 列表加载状态。
 */
const loading = ref(false)

/**
 * 行内筛选条件。
 */
const filters = reactive({
  originalName: '',
})

/**
 * 远端分页状态，页码与每页条数提交给后端，不在前端切片。
 */
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
})

/**
 * 当前默认存储方案，用于上传前的前端校验。
 */
const defaultStorage = ref<StorageConfig | null>(null)

/**
 * 上传中状态，同时用于禁用上传入口，避免重复提交。
 */
const uploading = ref(false)

/**
 * 正在上传的文件名，只用于加载态文案。
 */
const uploadingName = ref('')

/**
 * 正在下载的文件 ID，空串表示当前没有下载任务。
 */
const downloadingId = ref('')

/**
 * 正在删除的文件 ID，空串表示当前没有删除任务。
 */
const deletingId = ref('')

/**
 * 是否显示上传入口，需要 file:record:upload 权限。
 */
const canUpload = computed(() => session.hasPermission('file:record:upload'))

/**
 * 默认存储方案允许的扩展名；读取不到方案时为空数组，表示不在前端限制扩展名。
 */
const allowedExtensions = computed(() => toExtensionList(defaultStorage.value?.allowedExtensions))

/**
 * 默认存储方案的单文件大小上限（字节）；读取不到时为 undefined。
 */
const maxUploadBytes = computed(() => readMaxFileSize(defaultStorage.value))

/**
 * 上传入口的 accept 属性，在文件选择阶段先过滤明显不符合的扩展名。
 */
const acceptAttribute = computed(() => allowedExtensions.value.map((extension) => `.${extension}`).join(','))

/**
 * 上传限制提示，说明当前生效的扩展名与大小上限。
 */
const uploadHint = computed(() => {
  const extensionText =
    allowedExtensions.value.length > 0 ? `允许扩展名：${allowedExtensions.value.join('、')}` : '扩展名不限'
  const sizeText =
    maxUploadBytes.value === undefined
      ? '未取得默认存储方案的大小上限，最终以后端校验为准'
      : `单文件上限：${formatSize(maxUploadBytes.value)}`
  return `${extensionText} · ${sizeText}`
})

/**
 * 表格列定义。
 *
 * 操作列按权限显示：下载需要 file:record:download，删除需要 file:record:delete；
 * 两项权限都没有时不渲染操作列。
 */
const columns = computed<DataTableColumns<FileRecord>>(() => {
  const list: DataTableColumns<FileRecord> = [
    { title: '文件名', key: 'originalName', minWidth: 220, ellipsis: { tooltip: true } },
    { title: '大小', key: 'sizeBytes', width: 110, render: (row) => formatSize(row.sizeBytes) },
    {
      title: '内容类型',
      key: 'contentType',
      width: 170,
      ellipsis: { tooltip: true },
      render: (row) => row.contentType || '—',
    },
    {
      title: '上传者',
      key: 'uploaderName',
      width: 140,
      ellipsis: { tooltip: true },
      render: (row) => row.uploaderName || '—',
    },
    { title: '上传时间', key: 'createdAt', width: 190, render: (row) => formatTime(row.createdAt) },
  ]
  if (session.hasPermission('file:record:download') || session.hasPermission('file:record:delete')) {
    list.push({ title: '操作', key: 'actions', width: 170, fixed: 'right', render: (row) => renderActions(row) })
  }
  return list
})

/**
 * 读取默认存储方案允许的扩展名。
 *
 * 后端可能以数组或逗号分隔的字符串返回，这里统一成小写、不带点号的扩展名列表。
 *
 * @param value 响应中的扩展名字段
 * @returns 扩展名列表；无法解析时为空数组（表示不限制）
 */
function toExtensionList(value: string[] | string | undefined): string[] {
  if (!value) {
    return []
  }
  const raw = Array.isArray(value) ? value : value.split(',')
  return raw
    .map((item) => item.trim().replace(/^\./, '').toLowerCase())
    .filter((item) => item.length > 0)
}

/**
 * 读取默认存储方案的单文件大小上限。
 *
 * @param config 默认存储方案；尚未设置时为 null
 * @returns 字节数；缺少或非法时返回 undefined，此时前端不做大小拦截
 */
function readMaxFileSize(config: StorageConfig | null): number | undefined {
  const value = config?.maxFileSize
  return typeof value === 'number' && value > 0 ? value : undefined
}

/**
 * 把字节数转换为人类可读的大小文案。
 *
 * @param bytes 字节数，缺失或非法时返回占位符
 * @returns 形如 512 B、1.5 KB、2 MB、1.2 GB 的文案
 */
function formatSize(bytes: number | undefined): string {
  if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes < 0) {
    return '—'
  }
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const units = ['KB', 'MB', 'GB', 'TB']
  let value = bytes / 1024
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  const rounded = value >= 100 ? Math.round(value) : Math.round(value * 10) / 10
  return `${rounded} ${units[unitIndex]}`
}

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
 * 分页查询文件记录。
 */
async function loadRecords(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchFiles({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
      originalName: filters.originalName.trim() || undefined,
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
 * 读取当前默认存储方案，用于上传前的前端校验。
 *
 * 需要 file:storage:view 权限：没有权限或读取失败时不做前端拦截，
 * 由页面提示说明最终以后端校验为准，不因此阻止上传。
 */
async function loadDefaultStorage(): Promise<void> {
  if (!session.hasPermission('file:storage:view')) {
    defaultStorage.value = null
    return
  }
  try {
    defaultStorage.value = await fetchDefaultStorageConfig()
  } catch {
    defaultStorage.value = null
  }
}

/**
 * 校验所选文件是否满足默认存储方案的大小与扩展名限制。
 *
 * @param file 浏览器选择的文件
 * @returns 校验失败时返回中文原因；通过时返回 null
 */
function validateFile(file: File): string | null {
  const extensions = allowedExtensions.value
  if (extensions.length > 0) {
    const dotIndex = file.name.lastIndexOf('.')
    const extension = dotIndex >= 0 ? file.name.slice(dotIndex + 1).toLowerCase() : ''
    if (!extensions.includes(extension)) {
      return `仅允许上传 ${extensions.join('、')} 格式的文件`
    }
  }
  const limit = maxUploadBytes.value
  if (limit !== undefined && file.size > limit) {
    return `文件大小超过上限 ${formatSize(limit)}，当前文件 ${formatSize(file.size)}`
  }
  return null
}

/**
 * 处理 NUpload 的自定义上传请求。
 *
 * 先做前端校验，再调用 uploadFile 上传到当前默认存储方案；
 * 上传中禁用上传入口，成功后刷新列表，失败只提示一次。
 *
 * @param options NUpload 提供的自定义请求参数
 */
async function handleUpload(options: UploadCustomRequestOptions): Promise<void> {
  const file = options.file.file
  if (!file) {
    options.onError()
    message.error('未能读取所选文件，请重新选择')
    return
  }
  const invalidReason = validateFile(file)
  if (invalidReason) {
    options.onError()
    message.error(invalidReason)
    return
  }
  if (uploading.value) {
    options.onError()
    return
  }
  uploading.value = true
  uploadingName.value = file.name
  try {
    await uploadFile(file)
    options.onFinish()
    message.success(`已上传 ${file.name}`)
    pagination.page = 1
    await loadRecords()
  } catch (error) {
    options.onError()
    notifyFailure(error)
  } finally {
    uploading.value = false
    uploadingName.value = ''
  }
}

/**
 * 下载文件。
 *
 * 下载封装会识别“HTTP 200 + JSON 错误体”，因此业务失败与传输失败都走同一处提示。
 *
 * @param record 文件记录
 */
async function handleDownload(record: FileRecord): Promise<void> {
  const id = record.id
  if (!id) {
    message.error('该文件记录缺少 ID，无法下载')
    return
  }
  if (downloadingId.value) {
    return
  }
  downloadingId.value = id
  try {
    await downloadFile(id, record.originalName || 'download')
    message.success('已开始下载')
  } catch (error) {
    notifyFailure(error)
  } finally {
    downloadingId.value = ''
  }
}

/**
 * 删除文件记录并清理对象。
 *
 * @param record 文件记录
 */
async function handleDelete(record: FileRecord): Promise<void> {
  const id = record.id
  if (!id) {
    message.error('该文件记录缺少 ID，无法删除')
    return
  }
  if (deletingId.value) {
    return
  }
  deletingId.value = id
  try {
    await deleteFile(id)
    message.success('已删除文件记录')
    // 删除的是当前页最后一条时回退一页，避免停留在空页
    if (records.value.length === 1 && pagination.page > 1) {
      pagination.page -= 1
    }
    await loadRecords()
  } catch (error) {
    notifyFailure(error)
  } finally {
    deletingId.value = ''
  }
}

/**
 * 渲染操作列按钮。
 *
 * @param record 文件记录
 * @returns 操作列内容
 */
function renderActions(record: FileRecord): VNodeChild {
  const buttons: VNodeChild[] = []
  if (session.hasPermission('file:record:download')) {
    buttons.push(
      h(
        NButton,
        {
          size: 'small',
          quaternary: true,
          type: 'primary',
          loading: downloadingId.value === record.id,
          onClick: () => void handleDownload(record),
        },
        { default: () => '下载' },
      ),
    )
  }
  if (session.hasPermission('file:record:delete')) {
    buttons.push(
      h(
        NPopconfirm,
        {
          positiveText: '删除',
          negativeText: '取消',
          onPositiveClick: () => {
            void handleDelete(record)
          },
        },
        {
          trigger: () =>
            h(
              NButton,
              { size: 'small', quaternary: true, type: 'error', loading: deletingId.value === record.id },
              { default: () => '删除' },
            ),
          default: () => '删除后该文件将不可下载，确认删除？',
        },
      ),
    )
  }
  return h(NSpace, { size: 4, align: 'center' }, { default: () => buttons })
}

/**
 * 表格行键，优先使用文件 ID。
 *
 * @param record 文件记录
 * @returns 行键
 */
function rowKey(record: FileRecord): string {
  return record.id ?? ''
}

/**
 * 按筛选条件重新查询第一页。
 */
function handleSearch(): void {
  pagination.page = 1
  void loadRecords()
}

/**
 * 重置筛选条件并重新查询。
 */
function handleReset(): void {
  filters.originalName = ''
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

onMounted(() => {
  void loadDefaultStorage()
  void loadRecords()
})
</script>

<template>
  <PageBody>
    <NCard :bordered="false">
      <PageHeader
        title="文件资源"
        description="上传、查询与管理存储文件"
        icon="file"
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
          <NUpload
            v-if="canUpload"
            :accept="acceptAttribute || undefined"
            :custom-request="handleUpload"
            :disabled="uploading"
            :show-file-list="false"
          >
            <NButton
              class="action-button"
              type="primary"
              :loading="uploading"
              :disabled="uploading"
            >
              <template #icon>
                <AppIcon
                  name="plus"
                  :size="16"
                />
              </template>{{ uploading ? '上传中…' : '上传文件' }}
            </NButton>
          </NUpload>
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
        <NFormItem label="文件名">
          <NInput
            v-model:value="filters.originalName"
            clearable
            class="filters__input"
            placeholder="按原始文件名模糊匹配"
            @keyup.enter="handleSearch"
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

      <p class="hint">
        {{ uploadHint }}
      </p>

      <div
        v-if="uploading"
        class="upload-state"
      >
        <NSpin size="small" />
        <span>正在上传 {{ uploadingName }}，请勿关闭页面</span>
      </div>

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
          :scroll-x="1000"
          @update:page="handlePageChange"
          @update:page-size="handlePageSizeChange"
        >
          <template #empty>
            暂无文件记录
          </template>
        </NDataTable>
      </div>
    </NCard>
  </PageBody>
</template>

<style scoped>
.page-body {
  --of-text: #17212b;
  --of-muted: #8290a2;
  --of-line: #eef1f6;
  --of-accent: #f26722;
  --of-surface: #f7f9fc;
}

.filters {
  margin-bottom: 4px;
}

.filters__input {
  width: 220px;
}

.hint {
  margin: 0 0 12px;
  color: var(--of-muted);
  font-size: 12px;
}

.upload-state {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  border: 1px solid var(--of-line);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--of-surface);
  color: var(--of-text);
  font-size: 12px;
}

.upload-state span {
  color: var(--of-accent);
}

.table-wrap {
  overflow-x: auto;
}

@media (max-width: 640px) {
  .filters__input {
    width: 100%;
  }
}
</style>
