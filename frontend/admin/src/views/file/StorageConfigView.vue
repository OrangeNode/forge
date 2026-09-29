<script setup lang="ts">
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NPopconfirm,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
  type SelectOption,
} from 'naive-ui'
import { computed, h, nextTick, onMounted, reactive, ref, type VNodeChild } from 'vue'

import {
  createStorageConfig,
  deleteStorageConfig,
  fetchStorageConfigs,
  setDefaultStorageConfig,
  testStorageConfig,
  updateStorageConfig,
  type StorageConfig,
  type StorageConfigCreatePayload,
  type StorageConfigUpdatePayload,
} from '@/api/files'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { isBusinessCode, readFieldErrors, resolveErrorMessage } from '@/utils/error-message'

/**
 * 存储配置页。
 *
 * 表格展示全部存储方案版本（含历史版本），提供新建、修改、连接检测、切换默认与删除。
 * 修改只提交目标与限制字段；方案编码与存储类型在修改时不可变更；
 * 凭据在编辑时留空表示不修改，后端也不会回显明文。
 */
const session = useSessionStore()
const message = useMessage()

/**
 * 存储方案表单模型。
 */
interface StorageFormModel {
  /** 方案编码，同编码下版本递增 */
  code: string
  /** 方案名称 */
  name: string
  /** 存储类型：local 本地文件系统，s3 兼容对象存储 */
  provider: 'local' | 's3'
  /** 本地存储相对目录（provider=local） */
  baseDir: string
  /** 对象存储访问地址（provider=s3） */
  endpoint: string
  /** 对象存储区域（provider=s3） */
  region: string
  /** 对象存储桶名称（provider=s3） */
  bucket: string
  /** 是否使用 path-style 访问（provider=s3） */
  pathStyle: boolean
  /** 单文件大小上限（MB），提交时换算为字节 */
  maxFileSizeMb: number | null
  /** 允许的扩展名，用逗号或空格分隔的文本 */
  allowedExtensionsText: string
  /** 访问凭据，留空表示不修改 */
  accessKey: string
  /** 访问密钥，留空表示不修改 */
  secretKey: string
}

/**
 * 服务端字段校验错误可能出现的表单字段名。
 */
const FORM_FIELD_PATHS = [
  'code',
  'name',
  'provider',
  'baseDir',
  'endpoint',
  'region',
  'bucket',
  'pathStyle',
  'maxFileSize',
  'allowedExtensions',
  'accessKey',
  'secretKey',
]

/**
 * 全部存储方案版本。
 */
const configs = ref<StorageConfig[]>([])

/**
 * 列表加载状态。
 */
const loading = ref(false)

/**
 * 行内筛选的关键字，匹配方案编码与名称。
 */
const keywordFilter = ref('')

/**
 * 行内筛选的存储类型，null 表示全部。
 */
const providerFilter = ref<string | null>(null)

/**
 * 本地分页状态：接口一次返回全部版本，分页在表格内完成。
 */
const pagination = reactive({
  page: 1,
  pageSize: 10,
  showSizePicker: true,
  pageSizes: [10, 20, 50],
})

/**
 * 正在检测连接或切换默认的存储方案 ID，空串表示没有进行中的操作。
 */
const testingId = ref('')
const defaultingId = ref('')

/**
 * 正在删除的存储方案 ID，空串表示没有进行中的删除。
 */
const deletingId = ref('')

/**
 * 新建或修改弹窗的显示状态。
 */
const modalVisible = ref(false)

/**
 * 是否正在提交表单，用于按钮防重复提交。
 */
const submitting = ref(false)

/**
 * 正在修改的存储方案 ID，空串表示新建。
 */
const editingId = ref('')

/**
 * 正在修改的存储方案版本号，只用于展示。
 */
const editingVersion = ref<number | undefined>(undefined)

/**
 * 表单实例，用于提交前触发校验。
 */
const formRef = ref<FormInst | null>(null)

/**
 * 服务端返回的字段校验错误，按字段名展示在对应表单项上。
 */
const serverFieldErrors = ref<Record<string, string>>({})

/**
 * 表单模型。
 */
const form = reactive<StorageFormModel>(createEmptyForm())

/**
 * 存储类型下拉选项。
 */
const providerOptions: SelectOption[] = [
  { label: '本地文件系统', value: 'local' },
  { label: 'S3 兼容对象存储', value: 's3' },
]

/**
 * 筛选后的存储方案；筛选只在已加载的全部版本上进行。
 */
const filteredConfigs = computed(() => {
  const keyword = keywordFilter.value.trim().toLowerCase()
  return configs.value.filter((config: StorageConfig) => {
    const matchesKeyword =
      keyword.length === 0 ||
      (config.code ?? '').toLowerCase().includes(keyword) ||
      (config.name ?? '').toLowerCase().includes(keyword)
    const matchesProvider = providerFilter.value === null || config.provider === providerFilter.value
    return matchesKeyword && matchesProvider
  })
})

/**
 * 表单校验规则：本地与 S3 的目标字段按当前存储类型条件必填。
 */
const rules = computed<FormRules>(() => {
  const base: FormRules = {
    code: { required: true, message: '请输入方案编码', trigger: ['input', 'blur'] },
    name: { required: true, message: '请输入方案名称', trigger: ['input', 'blur'] },
    maxFileSizeMb: {
      required: true,
      type: 'number',
      message: '请输入单文件大小上限（MB）',
      trigger: ['change', 'blur'],
    },
  }
  if (form.provider === 'local') {
    base.baseDir = { required: true, message: '请输入相对目录（必须位于环境根目录内）', trigger: ['input', 'blur'] }
  } else {
    base.endpoint = { required: true, message: '请输入访问地址，例如 http://minio:9000', trigger: ['input', 'blur'] }
    base.bucket = { required: true, message: '请输入桶名称', trigger: ['input', 'blur'] }
  }
  return base
})

/**
 * 表格列定义。
 *
 * 操作列按权限显示：检测 file:storage:test、切换默认 file:storage:default、
 * 修改 file:storage:update、删除 file:storage:delete。
 */
const columns = computed<DataTableColumns<StorageConfig>>(() => {
  const list: DataTableColumns<StorageConfig> = [
    { title: '方案编码', key: 'code', width: 170, ellipsis: { tooltip: true } },
    { title: '版本', key: 'version', width: 80, render: (row) => `v${row.version ?? 1}` },
    { title: '名称', key: 'name', minWidth: 150, ellipsis: { tooltip: true } },
    { title: '存储类型', key: 'provider', width: 110, render: (row) => providerLabel(row.provider) },
    {
      title: '存储目标',
      key: 'target',
      minWidth: 240,
      ellipsis: { tooltip: true },
      render: (row) => targetSummary(row),
    },
    { title: '大小上限', key: 'maxFileSize', width: 110, render: (row) => formatSize(row.maxFileSize) },
    {
      title: '允许扩展名',
      key: 'allowedExtensions',
      width: 180,
      ellipsis: { tooltip: true },
      render: (row) => extensionsSummary(row.allowedExtensions),
    },
    { title: '状态', key: 'status', width: 180, render: (row) => renderStatus(row) },
    { title: '创建时间', key: 'createdAt', width: 190, render: (row) => formatTime(row.createdAt) },
  ]
  const canOperate =
    session.hasPermission('file:storage:test') ||
    session.hasPermission('file:storage:default') ||
    session.hasPermission('file:storage:update') ||
    session.hasPermission('file:storage:delete')
  if (canOperate) {
    list.push({ title: '操作', key: 'actions', width: 260, fixed: 'right', render: (row) => renderActions(row) })
  }
  return list
})

/**
 * 创建空的表单模型。
 *
 * path-style 默认开启，与数据库默认值一致，MinIO 等兼容服务需要该模式。
 *
 * @returns 空的表单模型
 */
function createEmptyForm(): StorageFormModel {
  return {
    code: '',
    name: '',
    provider: 'local',
    baseDir: '',
    endpoint: '',
    region: '',
    bucket: '',
    pathStyle: true,
    maxFileSizeMb: null,
    allowedExtensionsText: '',
    accessKey: '',
    secretKey: '',
  }
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
 * 把字节数换算为 MB，用于表单回显。
 *
 * @param bytes 字节数
 * @returns MB 数值，保留两位小数；缺失时返回 null
 */
function bytesToMb(bytes: number | undefined): number | null {
  if (typeof bytes !== 'number' || !Number.isFinite(bytes) || bytes <= 0) {
    return null
  }
  return Math.round((bytes / (1024 * 1024)) * 100) / 100
}

/**
 * 把表单中的 MB 数值换算为字节。
 *
 * 表单校验已保证数值有效，这里仍做一次防御性判断，非法值返回 0 并交由后端拒绝。
 *
 * @param megabytes MB 数值
 * @returns 字节数
 */
function mbToBytes(megabytes: number | null): number {
  if (typeof megabytes !== 'number' || !Number.isFinite(megabytes) || megabytes <= 0) {
    return 0
  }
  return Math.round(megabytes * 1024 * 1024)
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
 * 解析允许的扩展名。
 *
 * 后端可能以数组或逗号分隔的字符串返回，这里统一成小写、不带点号的列表。
 *
 * @param value 响应中的扩展名字段
 * @returns 扩展名列表；无法解析时为空数组（表示不限制）
 */
function toExtensionList(value: string[] | string | undefined): string[] {
  if (!value) {
    return []
  }
  const raw = Array.isArray(value) ? value : value.split(',')
  return parseExtensions(raw.join(' '))
}

/**
 * 把文本解析为扩展名列表。
 *
 * 支持逗号、中文逗号、分号与空白分隔，统一去掉点号并转小写、去重。
 *
 * @param text 扩展名文本
 * @returns 扩展名列表
 */
function parseExtensions(text: string): string[] {
  const parts = text.split(/[\s,，;；]+/)
  const result: string[] = []
  for (const part of parts) {
    const extension = part.trim().replace(/^\./, '').toLowerCase()
    if (extension.length > 0 && !result.includes(extension)) {
      result.push(extension)
    }
  }
  return result
}

/**
 * 把扩展名列表拼接为接口要求的逗号文本。
 *
 * 接口的入参是逗号分隔字符串，出参是字符串数组，转换只在本文件完成，
 * 页面表单内部统一用列表表达，避免两处各写一套解析规则。
 *
 * @param extensions 扩展名列表
 * @returns 逗号分隔文本；列表为空时返回 undefined 表示不限制
 */
function joinExtensions(extensions: string[]): string | undefined {
  return extensions.length > 0 ? extensions.join(',') : undefined
}

/**
 * 存储类型的中文标签。
 *
 * @param provider 存储类型代码
 * @returns 中文标签
 */
function providerLabel(provider: string | undefined): string {
  if (provider === 's3') {
    return 'S3 兼容'
  }
  return provider === 'local' ? '本地' : '—'
}

/**
 * 存储目标摘要：本地显示相对目录，S3 显示地址、桶与区域。
 *
 * @param config 存储方案
 * @returns 目标摘要文案
 */
function targetSummary(config: StorageConfig): string {
  if (config.provider === 's3') {
    const endpoint = config.endpoint || '未配置地址'
    const bucket = config.bucket || '未配置桶'
    const region = config.region ? ` · ${config.region}` : ''
    return `${endpoint} / ${bucket}${region}`
  }
  return config.baseDir ? `相对目录 ${config.baseDir}` : '未配置相对目录'
}

/**
 * 允许扩展名的展示文案。
 *
 * @param value 响应中的扩展名字段
 * @returns 扩展名文案；为空时表示不限制
 */
function extensionsSummary(value: string[] | string | undefined): string {
  const list = toExtensionList(value)
  return list.length > 0 ? list.join('、') : '不限制'
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
 * 读取全部存储方案版本。
 */
async function loadConfigs(): Promise<void> {
  loading.value = true
  try {
    configs.value = await fetchStorageConfigs()
  } catch (error) {
    configs.value = []
    notifyFailure(error)
  } finally {
    loading.value = false
  }
}

/**
 * 处理存储类型下拉的变化。
 *
 * @param value 下拉选中的值
 */
function handleProviderChange(value: string | number | Array<string | number> | null): void {
  form.provider = value === 's3' ? 's3' : 'local'
}

/**
 * 处理存储类型筛选项的变化，空值表示全部。
 *
 * @param value 下拉选中的值
 */
function handleProviderFilterChange(value: string | number | Array<string | number> | null): void {
  providerFilter.value = value === null || value === '' ? null : String(value)
  pagination.page = 1
}

/**
 * 应用筛选条件，回到第一页。
 */
function handleSearch(): void {
  pagination.page = 1
}

/**
 * 重置筛选条件。
 */
function handleReset(): void {
  keywordFilter.value = ''
  providerFilter.value = null
  pagination.page = 1
}

/**
 * 打开新建弹窗。
 */
function openCreateModal(): void {
  Object.assign(form, createEmptyForm())
  editingId.value = ''
  editingVersion.value = undefined
  serverFieldErrors.value = {}
  modalVisible.value = true
  void nextTick(() => formRef.value?.restoreValidation())
}

/**
 * 打开修改弹窗。
 *
 * 凭据不在响应中回显，编辑时留空表示保持原有密文。
 *
 * @param config 要修改的存储方案
 */
function openEditModal(config: StorageConfig): void {
  Object.assign(form, createEmptyForm())
  editingId.value = config.id ?? ''
  editingVersion.value = config.version
  form.code = config.code ?? ''
  form.name = config.name ?? ''
  form.provider = config.provider === 's3' ? 's3' : 'local'
  form.baseDir = config.baseDir ?? ''
  form.endpoint = config.endpoint ?? ''
  form.region = config.region ?? ''
  form.bucket = config.bucket ?? ''
  form.pathStyle = config.pathStyle !== false
  form.maxFileSizeMb = bytesToMb(config.maxFileSize)
  form.allowedExtensionsText = toExtensionList(config.allowedExtensions).join(', ')
  form.accessKey = ''
  form.secretKey = ''
  serverFieldErrors.value = {}
  modalVisible.value = true
  void nextTick(() => formRef.value?.restoreValidation())
}

/**
 * 读取服务端字段校验错误。
 *
 * @param path 表单字段名
 * @returns 该字段的错误提示；没有错误时返回 undefined
 */
function fieldFeedback(path: string): string | undefined {
  return serverFieldErrors.value[path]
}

/**
 * 读取服务端字段校验状态。
 *
 * @param path 表单字段名
 * @returns 该字段有错误时返回 error，否则返回 undefined
 */
function fieldStatus(path: string): 'error' | undefined {
  return serverFieldErrors.value[path] ? 'error' : undefined
}

/**
 * 组装提交给后端的存储目标字段。
 *
 * 本地方案只提交相对目录，S3 方案只提交地址、区域、桶与访问方式；
 * 凭据留空时不提交，避免用空值覆盖已保存的密文。
 *
 * @param payload 目标入参对象
 */
function fillTargetFields(payload: StorageConfigCreatePayload | StorageConfigUpdatePayload): void {
  if (form.provider === 'local') {
    payload.baseDir = form.baseDir.trim()
    return
  }
  payload.endpoint = form.endpoint.trim()
  payload.region = form.region.trim()
  payload.bucket = form.bucket.trim()
  payload.pathStyle = form.pathStyle
  const accessKey = form.accessKey.trim()
  const secretKey = form.secretKey.trim()
  if (accessKey) {
    payload.accessKey = accessKey
  }
  if (secretKey) {
    payload.secretKey = secretKey
  }
}

/**
 * 组装新建存储方案的入参。
 *
 * @param allowedExtensions 解析后的扩展名列表
 * @returns 新建入参
 */
function buildCreatePayload(allowedExtensions: string[]): StorageConfigCreatePayload {
  const payload: StorageConfigCreatePayload = {
    code: form.code.trim(),
    name: form.name.trim(),
    provider: form.provider,
    maxFileSize: mbToBytes(form.maxFileSizeMb),
    allowedExtensions: joinExtensions(allowedExtensions),
  }
  fillTargetFields(payload)
  return payload
}

/**
 * 组装修改存储方案的入参。
 *
 * 方案编码与存储类型不可修改，因此不提交这两个字段。
 *
 * @param allowedExtensions 解析后的扩展名列表
 * @returns 修改入参
 */
function buildUpdatePayload(allowedExtensions: string[]): StorageConfigUpdatePayload {
  const payload: StorageConfigUpdatePayload = {
    name: form.name.trim(),
    provider: form.provider,
    maxFileSize: mbToBytes(form.maxFileSizeMb),
    allowedExtensions: joinExtensions(allowedExtensions),
  }
  fillTargetFields(payload)
  return payload
}

/**
 * 提交新建或修改。
 *
 * 提交前先做表单校验，提交中禁用按钮避免重复提交；
 * 字段错误按 data.fieldErrors 展示在对应表单项上，其余失败只提示一次。
 */
async function handleSubmit(): Promise<void> {
  if (submitting.value) {
    return
  }
  serverFieldErrors.value = {}
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const allowedExtensions = parseExtensions(form.allowedExtensionsText)
  submitting.value = true
  try {
    if (editingId.value) {
      await updateStorageConfig(editingId.value, buildUpdatePayload(allowedExtensions))
      message.success('存储方案已修改')
    } else {
      await createStorageConfig(buildCreatePayload(allowedExtensions))
      message.success('存储方案已创建')
    }
    modalVisible.value = false
    await loadConfigs()
  } catch (error) {
    const fieldErrors = readFieldErrors(error)
    const matched = Object.keys(fieldErrors).filter((key) => FORM_FIELD_PATHS.includes(key))
    if (matched.length > 0) {
      const matchedErrors: Record<string, string> = {}
      for (const key of matched) {
        matchedErrors[key] = fieldErrors[key]
      }
      serverFieldErrors.value = matchedErrors
      return
    }
    if (isBusinessCode(error, 409)) {
      message.error(
        editingId.value
          ? '该存储方案已被文件引用或仍为默认方案，无法修改'
          : '方案编码与版本已存在，请调整方案编码后重试',
      )
      return
    }
    notifyFailure(error)
  } finally {
    submitting.value = false
  }
}

/**
 * 检测存储方案连通性。
 *
 * @param config 存储方案
 */
async function handleTest(config: StorageConfig): Promise<void> {
  const id = config.id
  if (!id) {
    message.error('该存储方案缺少 ID，无法检测连接')
    return
  }
  if (testingId.value) {
    return
  }
  testingId.value = id
  try {
    const result = await testStorageConfig(id)
    const latency = typeof result.elapsedMillis === 'number' ? `（耗时 ${result.elapsedMillis} ms）` : ''
    const text = `${result.message || '连接检测完成'}${latency}`
    if (result.available) {
      message.success(text)
    } else {
      message.warning(text)
    }
  } catch (error) {
    notifyFailure(error)
  } finally {
    testingId.value = ''
  }
}

/**
 * 切换默认存储方案，只影响新上传。
 *
 * @param config 存储方案
 */
async function handleSetDefault(config: StorageConfig): Promise<void> {
  const id = config.id
  if (!id) {
    message.error('该存储方案缺少 ID，无法切换默认')
    return
  }
  if (defaultingId.value) {
    return
  }
  defaultingId.value = id
  try {
    await setDefaultStorageConfig(id)
    message.success('已切换默认存储方案，只影响新上传')
    await loadConfigs()
  } catch (error) {
    notifyFailure(error)
  } finally {
    defaultingId.value = ''
  }
}

/**
 * 删除未被文件引用的存储方案。
 *
 * @param config 存储方案
 */
async function handleDelete(config: StorageConfig): Promise<void> {
  const id = config.id
  if (!id) {
    message.error('该存储方案缺少 ID，无法删除')
    return
  }
  if (deletingId.value) {
    return
  }
  deletingId.value = id
  try {
    await deleteStorageConfig(id)
    message.success('已删除存储方案')
    await loadConfigs()
  } catch (error) {
    if (isBusinessCode(error, 409)) {
      message.error('该存储方案已被文件引用或仍为默认方案，无法删除')
      return
    }
    notifyFailure(error)
  } finally {
    deletingId.value = ''
  }
}

/**
 * 渲染状态列：是否为默认方案、对象存储凭据是否已配置。
 *
 * 接口不返回“是否已被文件引用”，该信息由删除操作的结果体现（被引用时返回 409）。
 *
 * @param config 存储方案
 * @returns 状态标签
 */
function renderStatus(config: StorageConfig): VNodeChild {
  const tags: VNodeChild[] = []
  if (config.defaultConfig === true) {
    tags.push(h(NTag, { size: 'small', bordered: false, type: 'success' }, { default: () => '默认' }))
  }
  if (config.provider === 's3') {
    const credentialText = config.credentialConfigured === true ? '凭据已配置' : '凭据缺失'
    const credentialType = config.credentialConfigured === true ? 'info' : 'error'
    tags.push(h(NTag, { size: 'small', bordered: false, type: credentialType }, { default: () => credentialText }))
  }
  if (tags.length === 0) {
    tags.push(h(NTag, { size: 'small', bordered: false }, { default: () => '普通方案' }))
  }
  return h(NSpace, { size: 4, align: 'center' }, { default: () => tags })
}

/**
 * 渲染操作列按钮。
 *
 * @param config 存储方案
 * @returns 操作列内容
 */
function renderActions(config: StorageConfig): VNodeChild {
  const buttons: VNodeChild[] = []
  if (session.hasPermission('file:storage:test')) {
    buttons.push(
      h(
        NButton,
        {
          size: 'small',
          quaternary: true,
          type: 'primary',
          loading: testingId.value === config.id,
          onClick: () => void handleTest(config),
        },
        { default: () => '连接检测' },
      ),
    )
  }
  if (session.hasPermission('file:storage:default')) {
    buttons.push(
      h(
        NPopconfirm,
        {
          positiveText: '切换',
          negativeText: '取消',
          onPositiveClick: () => {
            void handleSetDefault(config)
          },
        },
        {
          trigger: () =>
            h(
              NButton,
              {
                size: 'small',
                quaternary: true,
                disabled: config.defaultConfig === true,
                loading: defaultingId.value === config.id,
              },
              { default: () => '设为默认' },
            ),
          default: () => '切换默认只影响新上传，历史文件仍按上传时的版本读取，确认切换？',
        },
      ),
    )
  }
  if (session.hasPermission('file:storage:update')) {
    buttons.push(
      h(
        NButton,
        { size: 'small', quaternary: true, onClick: () => openEditModal(config) },
        { default: () => '修改' },
      ),
    )
  }
  if (session.hasPermission('file:storage:delete')) {
    buttons.push(
      h(
        NPopconfirm,
        {
          positiveText: '删除',
          negativeText: '取消',
          onPositiveClick: () => {
            void handleDelete(config)
          },
        },
        {
          trigger: () =>
            h(
              NButton,
              { size: 'small', quaternary: true, type: 'error', loading: deletingId.value === config.id },
              { default: () => '删除' },
            ),
          default: () => '被文件引用或仍为默认的方案无法删除，确认删除该版本？',
        },
      ),
    )
  }
  return h(NSpace, { size: 4, align: 'center' }, { default: () => buttons })
}

/**
 * 表格行键，优先使用存储方案 ID。
 *
 * @param config 存储方案
 * @returns 行键
 */
function configRowKey(config: StorageConfig): string {
  return config.id ?? `${config.code ?? ''}-${config.version ?? ''}`
}

onMounted(() => {
  void loadConfigs()
})
</script>

<template>
  <div class="storage-view">
    <PageHeader
      title="存储配置"
      description="存储方案按版本管理：修改目标生成新版本，切换默认只影响新上传，被引用的方案不可删除"
    >
      <template #actions>
        <NButton
          v-if="session.hasPermission('file:storage:create')"
          type="primary"
          @click="openCreateModal"
        >
          新建方案
        </NButton>
      </template>
    </PageHeader>

    <NCard :bordered="true">
      <NForm
        inline
        :show-feedback="false"
        class="filters"
        @submit.prevent
      >
        <NFormItem label="关键字">
          <NInput
            v-model:value="keywordFilter"
            clearable
            class="filters__input"
            placeholder="按方案编码或名称匹配"
            @keyup.enter="handleSearch"
          />
        </NFormItem>
        <NFormItem label="存储类型">
          <NSelect
            class="filters__select"
            :value="providerFilter"
            clearable
            placeholder="全部"
            :options="providerOptions"
            @update:value="handleProviderFilterChange"
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
          size="small"
          :bordered="false"
          :columns="columns"
          :data="filteredConfigs"
          :loading="loading"
          :pagination="pagination"
          :row-key="configRowKey"
          :scroll-x="1620"
        >
          <template #empty>
            暂无存储方案
          </template>
        </NDataTable>
      </div>
    </NCard>

    <NModal
      v-model:show="modalVisible"
      preset="card"
      :title="editingId ? '修改存储方案' : '新建存储方案'"
      :mask-closable="false"
      :style="{ width: '640px', maxWidth: '94vw' }"
    >
      <NForm
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="top"
        @submit.prevent
      >
        <p
          v-if="editingId"
          class="modal-hint"
        >
          正在修改 v{{ editingVersion ?? '' }}：方案编码与存储类型不可修改，保存后按新目标生效，历史文件仍按原版本读取。
        </p>

        <NFormItem
          label="方案编码"
          path="code"
          :validation-status="fieldStatus('code')"
          :feedback="fieldFeedback('code')"
        >
          <NInput
            v-model:value="form.code"
            :disabled="Boolean(editingId)"
            placeholder="例如 default-local"
          />
        </NFormItem>

        <NFormItem
          label="方案名称"
          path="name"
          :validation-status="fieldStatus('name')"
          :feedback="fieldFeedback('name')"
        >
          <NInput
            v-model:value="form.name"
            placeholder="例如 本地默认存储"
          />
        </NFormItem>

        <NFormItem
          label="存储类型"
          path="provider"
          :validation-status="fieldStatus('provider')"
          :feedback="fieldFeedback('provider')"
        >
          <NSelect
            :value="form.provider"
            :disabled="Boolean(editingId)"
            :options="providerOptions"
            @update:value="handleProviderChange"
          />
        </NFormItem>

        <template v-if="form.provider === 'local'">
          <NFormItem
            label="相对目录"
            path="baseDir"
            :validation-status="fieldStatus('baseDir')"
            :feedback="fieldFeedback('baseDir')"
          >
            <NInput
              v-model:value="form.baseDir"
              placeholder="例如 uploads，必须位于环境根目录内"
            />
          </NFormItem>
        </template>

        <template v-else>
          <NFormItem
            label="访问地址"
            path="endpoint"
            :validation-status="fieldStatus('endpoint')"
            :feedback="fieldFeedback('endpoint')"
          >
            <NInput
              v-model:value="form.endpoint"
              placeholder="例如 http://minio:9000"
            />
          </NFormItem>

          <NFormItem
            label="区域"
            path="region"
            :validation-status="fieldStatus('region')"
            :feedback="fieldFeedback('region')"
          >
            <NInput
              v-model:value="form.region"
              placeholder="例如 us-east-1，可留空"
            />
          </NFormItem>

          <NFormItem
            label="桶名称"
            path="bucket"
            :validation-status="fieldStatus('bucket')"
            :feedback="fieldFeedback('bucket')"
          >
            <NInput
              v-model:value="form.bucket"
              placeholder="例如 forge-files"
            />
          </NFormItem>

          <NFormItem
            label="Path-Style 访问"
            path="pathStyle"
            :validation-status="fieldStatus('pathStyle')"
            :feedback="fieldFeedback('pathStyle')"
          >
            <NSwitch v-model:value="form.pathStyle" />
            <span class="modal-note">MinIO 等兼容服务通常需要开启</span>
          </NFormItem>

          <NFormItem
            label="Access Key"
            path="accessKey"
            :validation-status="fieldStatus('accessKey')"
            :feedback="fieldFeedback('accessKey')"
          >
            <NInput
              v-model:value="form.accessKey"
              :placeholder="editingId ? '留空表示不修改；接口不回显凭据' : 'S3 访问凭据，可留空由环境提供'"
            />
          </NFormItem>

          <NFormItem
            label="Secret Key"
            path="secretKey"
            :validation-status="fieldStatus('secretKey')"
            :feedback="fieldFeedback('secretKey')"
          >
            <NInput
              v-model:value="form.secretKey"
              type="password"
              show-password-on="click"
              :placeholder="editingId ? '留空表示不修改；接口不回显密文' : 'S3 访问密钥，可留空由环境提供'"
            />
          </NFormItem>
        </template>

        <NFormItem
          label="单文件大小上限（MB）"
          path="maxFileSizeMb"
          :validation-status="fieldStatus('maxFileSize')"
          :feedback="fieldFeedback('maxFileSize')"
        >
          <NInputNumber
            v-model:value="form.maxFileSizeMb"
            class="modal-number"
            :min="1"
            :precision="2"
            placeholder="不得高于服务端 multipart 硬上限"
          />
        </NFormItem>

        <NFormItem
          label="允许扩展名"
          path="allowedExtensionsText"
          :validation-status="fieldStatus('allowedExtensions')"
          :feedback="fieldFeedback('allowedExtensions')"
        >
          <NInput
            v-model:value="form.allowedExtensionsText"
            placeholder="用逗号分隔，例如 png,jpg,pdf；留空表示不限制"
          />
        </NFormItem>
      </NForm>

      <div class="modal-actions">
        <NButton @click="modalVisible = false">
          取消
        </NButton>
        <NButton
          type="primary"
          :loading="submitting"
          :disabled="submitting"
          @click="handleSubmit"
        >
          {{ editingId ? '保存修改' : '创建方案' }}
        </NButton>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.storage-view {
  --of-muted: #8290a2;
  --of-line: #eef1f6;
  --of-surface: #f7f9fc;
}

.filters {
  margin-bottom: 4px;
}

.filters__input {
  width: 220px;
}

.filters__select {
  width: 180px;
}

.table-wrap {
  overflow-x: auto;
}

.modal-hint {
  margin: 0 0 14px;
  border: 1px solid var(--of-line);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--of-surface);
  color: var(--of-muted);
  font-size: 12px;
  line-height: 1.7;
}

.modal-note {
  margin-left: 10px;
  color: var(--of-muted);
  font-size: 12px;
}

.modal-number {
  width: 100%;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
  padding-top: 14px;
  border-top: 1px solid var(--of-line);
}

@media (max-width: 640px) {
  .filters__input,
  .filters__select {
    width: 100%;
  }
}
</style>
