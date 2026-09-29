<script setup lang="ts">
import type { PageQuery } from '@orange-forge/api-client'
import {
  NButton,
  NCard,
  NCheckbox,
  NCheckboxGroup,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NPopconfirm,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NText,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
  type PaginationProps,
} from 'naive-ui'
import { computed, h, nextTick, onMounted, reactive, ref, type VNodeChild } from 'vue'

import {
  assignAdminRoles,
  changeAdminStatus,
  createAdmin,
  fetchAdminDetail,
  fetchAdmins,
  fetchRoleOptions,
  resetAdminPassword,
  updateAdmin,
  type AdminDetail,
  type RoleOption,
} from '@/api/system'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { isBusinessCode, readFieldErrors, resolveErrorMessage } from '@/utils/error-message'

/**
 * 账号状态代码，与后端 AdminAccountStatus 的稳定取值一致。
 */
type AdminStatus = 'enabled' | 'disabled'

/**
 * 每页条数候选，后端允许 1—100。
 */
const PAGE_SIZE_OPTIONS = [10, 20, 50]

/**
 * 列表默认每页条数。
 */
const DEFAULT_PAGE_SIZE = 10

/**
 * 状态筛选选项，取值与后端账号状态代码一致。
 */
const STATUS_FILTER_OPTIONS = [
  { label: '已启用', value: 'enabled' },
  { label: '已停用', value: 'disabled' },
]

/**
 * 时间展示格式化器，按浏览器本地时区渲染后端返回的 ISO 8601 时间。
 */
const TIME_FORMATTER = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

/**
 * 新增与编辑表单校验规则，字段名与后端请求字段保持一致。
 */
const FORM_RULES: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: ['blur', 'input'] },
    { min: 4, max: 32, message: '用户名长度需为 4—32 个字符', trigger: ['blur', 'input'] },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: ['blur', 'input'] },
    { max: 64, message: '显示名称不能超过 64 个字符', trigger: ['blur', 'input'] },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: ['blur', 'input'] },
    { min: 8, max: 64, message: '密码长度需为 8—64 个字符', trigger: ['blur', 'input'] },
  ],
}

/**
 * 重置密码表单校验规则。
 */
const PASSWORD_RULES: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: ['blur', 'input'] },
    { min: 8, max: 64, message: '密码长度需为 8—64 个字符', trigger: ['blur', 'input'] },
  ],
}

const session = useSessionStore()
const message = useMessage()

/**
 * 当前页的管理员记录。
 */
const rows = ref<AdminDetail[]>([])

/**
 * 列表加载状态。
 */
const loading = ref(false)

/**
 * 当前页码。
 */
const pageNum = ref(1)

/**
 * 当前每页条数。
 */
const pageSize = ref(DEFAULT_PAGE_SIZE)

/**
 * 满足筛选条件的记录总数。
 */
const total = ref(0)

/**
 * 筛选条件：用户名关键字与账号状态，空值表示不筛选。
 */
const filters = reactive<{ username: string | null; status: string | null }>({
  username: null,
  status: null,
})

/**
 * 是否允许查看管理员列表，用于提示无权限时的按钮可用性。
 */
const canView = computed(() => session.hasPermission('system:admin:view'))

/**
 * 是否允许新增管理员。
 */
const canCreate = computed(() => session.hasPermission('system:admin:create'))

/**
 * 是否允许修改管理员显示名称。
 */
const canUpdate = computed(() => session.hasPermission('system:admin:update'))

/**
 * 是否允许启用或停用管理员。
 */
const canChangeStatus = computed(() => session.hasPermission('system:admin:status'))

/**
 * 是否允许重置管理员密码。
 */
const canResetPassword = computed(() => session.hasPermission('system:admin:password'))

/**
 * 是否允许分配管理员角色。
 */
const canAssignRole = computed(() => session.hasPermission('system:admin:role'))

/**
 * 表格分页配置，分页由后端完成，前端只同步页码与条数。
 */
const pagination = computed<PaginationProps>(() => ({
  page: pageNum.value,
  pageSize: pageSize.value,
  itemCount: total.value,
  showSizePicker: true,
  pageSizes: PAGE_SIZE_OPTIONS,
  prefix: () => `共 ${total.value} 条`,
}))

/**
 * 新增与编辑弹窗可见状态。
 */
const formVisible = ref(false)

/**
 * 当前编辑的管理员 ID，为空表示新增。
 */
const editingId = ref<string | null>(null)

/**
 * 新增与编辑表单的提交状态，用于阻止重复提交。
 */
const submitting = ref(false)

/**
 * 新增与编辑表单实例。
 */
const formRef = ref<FormInst | null>(null)

/**
 * 后端返回的字段级错误，按字段名展示在对应表单项。
 */
const formErrors = ref<Record<string, string>>({})

/**
 * 新增与编辑表单模型。
 */
const form = reactive({ username: '', displayName: '', password: '' })

/**
 * 重置密码弹窗可见状态。
 */
const passwordVisible = ref(false)

/**
 * 重置密码的目标管理员 ID。
 */
const passwordTargetId = ref<string | null>(null)

/**
 * 重置密码的目标管理员用户名，仅用于弹窗说明。
 */
const passwordTargetName = ref('')

/**
 * 重置密码的提交状态。
 */
const passwordSubmitting = ref(false)

/**
 * 重置密码表单实例。
 */
const passwordFormRef = ref<FormInst | null>(null)

/**
 * 重置密码的字段级错误。
 */
const passwordErrors = ref<Record<string, string>>({})

/**
 * 重置密码表单模型。
 */
const passwordForm = reactive({ newPassword: '' })

/**
 * 角色分配弹窗可见状态。
 */
const roleVisible = ref(false)

/**
 * 角色分配的目标管理员 ID。
 */
const roleTargetId = ref<string | null>(null)

/**
 * 角色分配的目标管理员用户名，仅用于弹窗说明。
 */
const roleTargetName = ref('')

/**
 * 角色分配弹窗的加载与提交状态。
 */
const roleLoading = ref(false)
const roleSubmitting = ref(false)

/**
 * 可选角色列表与当前勾选的角色 ID。
 */
const roleOptions = ref<RoleOption[]>([])
const selectedRoleIds = ref<Array<string | number>>([])

/**
 * 弹窗标题，按新增或编辑切换。
 */
const formTitle = computed(() => (editingId.value === null ? '新增管理员' : '编辑管理员'))

/**
 * 是否处于新增状态，用于控制用户名与密码字段的显示。
 */
const isCreating = computed(() => editingId.value === null)

/**
 * 可分配的角色选项，过滤缺少标识的异常数据。
 */
const assignableRoles = computed(() => roleOptions.value.filter((role: RoleOption) => Boolean(role.id)))

/**
 * 按当前筛选条件与分页读取管理员列表。
 *
 * 失败时提示一次并清空表格，页面保持可用状态，不做自动重试。
 */
async function loadAdmins(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchAdmins(buildQuery())
    rows.value = result.records ?? []
    total.value = result.total ?? 0
  } catch (error) {
    rows.value = []
    total.value = 0
    message.error(resolveErrorMessage(error))
  } finally {
    loading.value = false
  }
}

/**
 * 组装分页与筛选查询参数，空筛选条件不下发。
 *
 * @returns 接口所需的分页与筛选参数
 */
function buildQuery(): PageQuery & { username?: string; status?: string } {
  const username = (filters.username ?? '').trim()
  const status = filters.status
  return {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    username: username === '' ? undefined : username,
    status: status === null || status === '' ? undefined : status,
  }
}

/**
 * 按当前筛选条件从第一页重新查询。
 */
function handleSearch(): void {
  pageNum.value = 1
  void loadAdmins()
}

/**
 * 清空筛选条件并重新查询。
 */
function handleReset(): void {
  filters.username = null
  filters.status = null
  pageNum.value = 1
  void loadAdmins()
}

/**
 * 手动刷新当前页列表。
 */
function handleRefresh(): void {
  void loadAdmins()
}

/**
 * 切换页码后重新查询。
 *
 * @param page 目标页码
 */
function handlePageChange(page: number): void {
  pageNum.value = page
  void loadAdmins()
}

/**
 * 切换每页条数后回到第一页重新查询。
 *
 * @param size 目标每页条数
 */
function handlePageSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void loadAdmins()
}

/**
 * 执行指定表单的本地校验。
 *
 * @param instance 表单实例，尚未渲染时为 null
 * @returns 校验通过时返回 true，失败提示已由表单项展示
 */
async function validateForm(instance: FormInst | null): Promise<boolean> {
  if (!instance) {
    return false
  }
  try {
    await instance.validate()
    return true
  } catch {
    return false
  }
}

/**
 * 处理提交失败：code=400 的字段错误写入表单展示，其余情况提示一次。
 *
 * @param error 捕获到的未知异常
 * @param errors 字段错误目标对象，直接写入以驱动表单项提示
 */
function applySubmitError(error: unknown, errors: Record<string, string>): void {
  if (isBusinessCode(error, 400)) {
    const fieldErrors = readFieldErrors(error)
    if (Object.keys(fieldErrors).length > 0) {
      for (const [field, text] of Object.entries(fieldErrors)) {
        errors[field] = text
      }
      return
    }
  }
  message.error(resolveErrorMessage(error))
}

/**
 * 读取行的主键，缺失时提示一次并返回 null，避免用空 ID 发起请求。
 *
 * @param row 表格行数据
 * @returns 主键字符串，缺失时为 null
 */
function readRowId(row: AdminDetail): string | null {
  if (!row.id) {
    message.warning('该记录缺少标识，无法执行该操作')
    return null
  }
  return row.id
}

/**
 * 把表单校验状态重置为初始状态，弹窗内容渲染后调用。
 *
 * @param instance 表单实例
 */
function resetValidation(instance: FormInst | null): void {
  void nextTick(() => {
    instance?.restoreValidation()
  })
}

/**
 * 打开新增弹窗并重置表单与历史错误。
 */
function openCreate(): void {
  editingId.value = null
  form.username = ''
  form.displayName = ''
  form.password = ''
  formErrors.value = {}
  formVisible.value = true
  resetValidation(formRef.value)
}

/**
 * 打开编辑弹窗，回填当前行的显示名称。
 *
 * @param row 表格行数据
 */
function openEdit(row: AdminDetail): void {
  const id = readRowId(row)
  if (!id) {
    return
  }
  editingId.value = id
  form.username = row.username ?? ''
  form.displayName = row.displayName ?? ''
  form.password = ''
  formErrors.value = {}
  formVisible.value = true
  resetValidation(formRef.value)
}

/**
 * 保存新增或编辑结果。
 *
 * 提交前先做本地校验；后端返回 code=400 时把字段错误落到对应表单项，
 * 其他失败只提示一次，成功后就地刷新列表。
 */
async function handleSubmit(): Promise<void> {
  if (!(await validateForm(formRef.value))) {
    return
  }
  formErrors.value = {}
  submitting.value = true
  try {
    if (editingId.value === null) {
      await createAdmin({
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        password: form.password,
      })
      message.success('管理员已创建')
      pageNum.value = 1
    } else {
      await updateAdmin(editingId.value, { displayName: form.displayName.trim() })
      message.success('管理员已更新')
    }
    formVisible.value = false
    await loadAdmins()
  } catch (error) {
    applySubmitError(error, formErrors.value)
  } finally {
    submitting.value = false
  }
}

/**
 * 切换账号启用状态，成功后刷新当前页。
 *
 * @param row 表格行数据
 * @param status 目标状态代码
 */
async function handleStatusChange(row: AdminDetail, status: AdminStatus): Promise<void> {
  const id = readRowId(row)
  if (!id) {
    return
  }
  try {
    await changeAdminStatus(id, { status })
    message.success(status === 'enabled' ? '账号已启用' : '账号已停用')
    await loadAdmins()
  } catch (error) {
    message.error(resolveErrorMessage(error))
  }
}

/**
 * 打开重置密码弹窗。
 *
 * @param row 表格行数据
 */
function openPasswordReset(row: AdminDetail): void {
  const id = readRowId(row)
  if (!id) {
    return
  }
  passwordTargetId.value = id
  passwordTargetName.value = row.username ?? ''
  passwordForm.newPassword = ''
  passwordErrors.value = {}
  passwordVisible.value = true
  resetValidation(passwordFormRef.value)
}

/**
 * 提交重置密码；成功后该账号此前签发的令牌全部失效。
 */
async function handlePasswordSubmit(): Promise<void> {
  const id = passwordTargetId.value
  if (!id) {
    return
  }
  if (!(await validateForm(passwordFormRef.value))) {
    return
  }
  passwordErrors.value = {}
  passwordSubmitting.value = true
  try {
    await resetAdminPassword(id, { newPassword: passwordForm.newPassword })
    message.success('密码已重置，该账号原有登录状态已失效')
    passwordVisible.value = false
  } catch (error) {
    applySubmitError(error, passwordErrors.value)
  } finally {
    passwordSubmitting.value = false
  }
}

/**
 * 打开角色分配弹窗，读取该管理员已分配的角色与可选角色列表。
 *
 * @param row 表格行数据
 */
async function openRoleAssign(row: AdminDetail): Promise<void> {
  const id = readRowId(row)
  if (!id) {
    return
  }
  roleTargetId.value = id
  roleTargetName.value = row.username ?? ''
  roleOptions.value = []
  selectedRoleIds.value = []
  roleVisible.value = true
  roleLoading.value = true
  try {
    const [detail, options] = await Promise.all([fetchAdminDetail(id), fetchRoleOptions()])
    selectedRoleIds.value = toIdList(readRoleIds(detail.roles))
    roleOptions.value = options
  } catch (error) {
    roleVisible.value = false
    message.error(resolveErrorMessage(error))
  } finally {
    roleLoading.value = false
  }
}

/**
 * 保存管理员角色分配，全量替换语义，成功后刷新列表。
 */
async function handleRoleSubmit(): Promise<void> {
  const id = roleTargetId.value
  if (!id) {
    return
  }
  roleSubmitting.value = true
  try {
    await assignAdminRoles(id, { roleIds: toIdList(selectedRoleIds.value) })
    message.success('角色分配已保存')
    roleVisible.value = false
    await loadAdmins()
  } catch (error) {
    message.error(resolveErrorMessage(error))
  } finally {
    roleSubmitting.value = false
  }
}

/**
 * 把复选框组的选中值收窄为字符串 ID 列表。
 *
 * @param values 复选框组选中值
 * @returns 字符串 ID 列表，保持选择顺序
 */
function toIdList(values: Array<string | number>): string[] {
  return values.map((value) => String(value))
}

/**
 * 格式化后端时间字段。
 *
 * @param value ISO 8601 时间字符串
 * @returns 本地时区文案，空值或非法值返回占位符
 */
function formatTime(value: string | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : TIME_FORMATTER.format(date)
}

/**
 * 渲染账号状态标签。
 *
 * @param status 账号状态代码
 * @returns 状态标签节点
 */
function renderStatus(status: string | undefined): VNodeChild {
  if (status === 'disabled') {
    return h(NTag, { size: 'small', bordered: false }, { default: () => '已停用' })
  }
  return h(NTag, { size: 'small', bordered: false, type: 'success' }, { default: () => '已启用' })
}

/**
 * 渲染角色标签组。
 *
 * @param roles 角色摘要列表
 * @returns 标签组节点，未分配角色时展示占位文案
 */
function renderRoles(roles: RoleOption[] | undefined): VNodeChild {
  if (!roles || roles.length === 0) {
    return h(NText, { depth: 3 }, { default: () => '未分配' })
  }
  return h(NSpace, { size: 4 }, {
    default: () =>
      roles.map((role) =>
        h(
          NTag,
          { key: role.id ?? role.code, size: 'small', bordered: false, type: 'info' },
          { default: () => role.name ?? role.code ?? '未命名角色' },
        ),
      ),
  })
}

/**
 * 读取角色摘要中的角色 ID。
 *
 * @param roles 角色摘要列表
 * @returns 角色 ID 列表，保持后端返回顺序
 */
function readRoleIds(roles: RoleOption[] | undefined): string[] {
  const ids: string[] = []
  for (const role of roles ?? []) {
    if (role.id) {
      ids.push(role.id)
    }
  }
  return ids
}

/**
 * 渲染行内操作按钮，按权限决定是否展示。
 *
 * 停用属于危险操作，先经过气泡确认；启用可直接执行。
 *
 * @param row 表格行数据
 * @returns 操作按钮组节点
 */
function renderActions(row: AdminDetail): VNodeChild {
  const actions: VNodeChild[] = []
  if (canUpdate.value) {
    actions.push(
      h(
        NButton,
        { size: 'small', quaternary: true, type: 'primary', onClick: () => openEdit(row) },
        { default: () => '编辑' },
      ),
    )
  }
  if (canChangeStatus.value) {
    if (row.status === 'disabled') {
      actions.push(
        h(
          NButton,
          { size: 'small', quaternary: true, onClick: () => void handleStatusChange(row, 'enabled') },
          { default: () => '启用' },
        ),
      )
    } else {
      actions.push(
        h(
          NPopconfirm,
          { onPositiveClick: () => void handleStatusChange(row, 'disabled') },
          {
            trigger: () =>
              h(NButton, { size: 'small', quaternary: true, type: 'warning' }, { default: () => '停用' }),
            default: () => '停用后该账号无法登录，已签发的令牌立即失效，确认停用？',
          },
        ),
      )
    }
  }
  if (canResetPassword.value) {
    actions.push(
      h(
        NButton,
        { size: 'small', quaternary: true, onClick: () => openPasswordReset(row) },
        { default: () => '重置密码' },
      ),
    )
  }
  if (canAssignRole.value) {
    actions.push(
      h(
        NButton,
        { size: 'small', quaternary: true, onClick: () => void openRoleAssign(row) },
        { default: () => '分配角色' },
      ),
    )
  }
  if (actions.length === 0) {
    return h(NText, { depth: 3 }, { default: () => '无可用操作' })
  }
  return h(NSpace, { size: 6, wrap: false }, { default: () => actions })
}

/**
 * 表格行键，使用后端返回的字符串 ID。
 *
 * @param row 表格行数据
 * @returns 行键字符串
 */
function rowKey(row: AdminDetail): string {
  return row.id ?? ''
}

/**
 * 管理员列表列定义，操作列按当前权限动态生成。
 */
const columns = computed<DataTableColumns<AdminDetail>>(() => [
  { title: '用户名', key: 'username', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '显示名称', key: 'displayName', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '角色', key: 'roles', minWidth: 180, render: (row) => renderRoles(row.roles) },
  { title: '状态', key: 'status', width: 100, render: (row) => renderStatus(row.status) },
  { title: '最近登录', key: 'lastLoginAt', width: 170, render: (row) => formatTime(row.lastLoginAt) },
  { title: '创建时间', key: 'createdAt', width: 170, render: (row) => formatTime(row.createdAt) },
  { title: '操作', key: 'actions', width: 300, render: (row) => renderActions(row) },
])

onMounted(() => {
  if (!canView.value) {
    return
  }
  void loadAdmins()
})
</script>

<template>
  <div class="admin-list">
    <PageHeader
      title="管理员账号"
      description="账号的查询、新增、启停、密码重置与角色分配"
    >
      <template #actions>
        <NButton
          secondary
          :disabled="!canView"
          :loading="loading"
          @click="handleRefresh"
        >
          刷新
        </NButton>
        <NButton
          v-if="canCreate"
          type="primary"
          @click="openCreate"
        >
          新增管理员
        </NButton>
      </template>
    </PageHeader>

    <NCard>
      <div class="filter-bar">
        <NInput
          v-model:value="filters.username"
          class="filter-bar__item"
          clearable
          placeholder="按用户名搜索"
          @keyup.enter="handleSearch"
        />
        <NSelect
          v-model:value="filters.status"
          class="filter-bar__item"
          clearable
          :options="STATUS_FILTER_OPTIONS"
          placeholder="账号状态"
        />
        <NButton
          type="primary"
          secondary
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
      </div>

      <NDataTable
        remote
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :row-key="rowKey"
        :scroll-x="1220"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />

      <p
        v-if="!canView"
        class="form-hint"
      >
        当前账号没有管理员查看权限，请联系管理员授权后再试；按钮显示不替代后端授权。
      </p>
    </NCard>

    <NModal
      v-model:show="formVisible"
      preset="card"
      :title="formTitle"
      :mask-closable="false"
      :style="{ width: 'min(560px, 92vw)' }"
    >
      <p class="form-hint">
        用户名是登录凭据，创建后不可修改；状态、密码与角色分别由独立操作处理。
      </p>
      <NForm
        ref="formRef"
        :model="form"
        :rules="FORM_RULES"
        label-placement="top"
      >
        <NFormItem
          v-if="isCreating"
          label="用户名"
          path="username"
          :validation-status="formErrors.username ? 'error' : undefined"
          :feedback="formErrors.username"
        >
          <NInput
            v-model:value="form.username"
            clearable
            placeholder="4—32 个字符，登录不区分大小写"
          />
        </NFormItem>
        <NFormItem
          v-if="isCreating"
          label="初始密码"
          path="password"
          :validation-status="formErrors.password ? 'error' : undefined"
          :feedback="formErrors.password"
        >
          <NInput
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="8—64 个字符"
          />
        </NFormItem>
        <NFormItem
          label="显示名称"
          path="displayName"
          :validation-status="formErrors.displayName ? 'error' : undefined"
          :feedback="formErrors.displayName"
        >
          <NInput
            v-model:value="form.displayName"
            clearable
            placeholder="不超过 64 个字符"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <div class="modal-actions">
          <NButton
            :disabled="submitting"
            @click="formVisible = false"
          >
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="submitting"
            @click="handleSubmit"
          >
            保存
          </NButton>
        </div>
      </template>
    </NModal>

    <NModal
      v-model:show="passwordVisible"
      preset="card"
      title="重置密码"
      :mask-closable="false"
      :style="{ width: 'min(480px, 92vw)' }"
    >
      <p class="form-hint">
        将重置账号 {{ passwordTargetName || '—' }} 的密码，保存后该账号已签发的令牌立即失效。
      </p>
      <NForm
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="PASSWORD_RULES"
        label-placement="top"
      >
        <NFormItem
          label="新密码"
          path="newPassword"
          :validation-status="passwordErrors.newPassword ? 'error' : undefined"
          :feedback="passwordErrors.newPassword"
        >
          <NInput
            v-model:value="passwordForm.newPassword"
            type="password"
            show-password-on="click"
            placeholder="8—64 个字符"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <div class="modal-actions">
          <NButton
            :disabled="passwordSubmitting"
            @click="passwordVisible = false"
          >
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="passwordSubmitting"
            @click="handlePasswordSubmit"
          >
            确认重置
          </NButton>
        </div>
      </template>
    </NModal>

    <NModal
      v-model:show="roleVisible"
      preset="card"
      title="分配角色"
      :mask-closable="false"
      :style="{ width: 'min(560px, 92vw)' }"
    >
      <NSpin :show="roleLoading">
        <p class="form-hint">
          角色为全量替换：保存后账号 {{ roleTargetName || '—' }} 只保留本次勾选的角色，未勾选的角色会被解除。
        </p>
        <div class="role-box">
          <NCheckboxGroup v-model:value="selectedRoleIds">
            <NSpace vertical>
              <NCheckbox
                v-for="role in assignableRoles"
                :key="role.id"
                :value="role.id"
                :label="role.name ?? role.code ?? '未命名角色'"
              />
            </NSpace>
          </NCheckboxGroup>
          <NText
            v-if="assignableRoles.length === 0 && !roleLoading"
            depth="3"
          >
            暂无可分配角色，请先在角色管理中创建角色
          </NText>
        </div>
      </NSpin>
      <template #footer>
        <div class="modal-actions">
          <NButton
            :disabled="roleSubmitting"
            @click="roleVisible = false"
          >
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="roleSubmitting"
            @click="handleRoleSubmit"
          >
            保存角色
          </NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.filter-bar__item {
  width: 220px;
}

.form-hint {
  margin: 0 0 12px;
  color: #8290a2;
  font-size: 12px;
  line-height: 1.7;
}

.role-box {
  max-height: 320px;
  overflow: auto;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 640px) {
  .filter-bar__item {
    width: 100%;
  }
}
</style>
