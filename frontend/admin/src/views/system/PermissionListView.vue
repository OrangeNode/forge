<script setup lang="ts">
import type { PageQuery } from '@orange-forge/api-client'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NPopconfirm,
  NSpace,
  NText,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
  type PaginationProps,
} from 'naive-ui'
import { computed, h, nextTick, onMounted, reactive, ref, type VNodeChild } from 'vue'

import {
  createPermission,
  deletePermission,
  fetchPermissions,
  updatePermission,
  type PermissionItem,
} from '@/api/system'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { isBusinessCode, readFieldErrors, resolveErrorMessage } from '@/utils/error-message'

/**
 * 每页条数候选，后端允许 1—100。
 */
const PAGE_SIZE_OPTIONS = [10, 20, 50]

/**
 * 列表默认每页条数。
 */
const DEFAULT_PAGE_SIZE = 10

/**
 * 权限代码格式，与后端 PermissionCodeFormat 的 PATTERN 保持一致。
 */
const PERMISSION_CODE_PATTERN = /^[a-z0-9_-]{1,32}:[a-z0-9_-]{1,32}:[a-z0-9_-]{1,32}$/

/**
 * 权限代码格式说明，用于表单提示。
 */
const PERMISSION_CODE_DESCRIPTION = '权限代码格式为 模块:资源:动作，只允许小写字母、数字、下划线与连字符'

/**
 * 新增与编辑表单校验规则，字段名与后端请求字段保持一致。
 */
const FORM_RULES: FormRules = {
  code: [
    { required: true, message: '请输入权限代码', trigger: ['blur', 'input'] },
    { pattern: PERMISSION_CODE_PATTERN, message: PERMISSION_CODE_DESCRIPTION, trigger: ['blur', 'input'] },
  ],
  name: [
    { required: true, message: '请输入权限名称', trigger: ['blur', 'input'] },
    { max: 64, message: '权限名称不能超过 64 个字符', trigger: ['blur', 'input'] },
  ],
  description: [{ max: 255, message: '权限说明不能超过 255 个字符', trigger: ['blur', 'input'] }],
}

const session = useSessionStore()
const message = useMessage()

/**
 * 当前页的权限记录。
 */
const rows = ref<PermissionItem[]>([])

/**
 * 列表加载状态。
 */
const loading = ref(false)

/**
 * 当前页码与每页条数。
 */
const pageNum = ref(1)
const pageSize = ref(DEFAULT_PAGE_SIZE)

/**
 * 满足筛选条件的记录总数。
 */
const total = ref(0)

/**
 * 筛选条件：权限代码与权限名称关键字，空值表示不筛选。
 */
const filters = reactive<{ code: string | null; name: string | null }>({
  code: null,
  name: null,
})

/**
 * 是否允许查看权限列表。
 */
const canView = computed(() => session.hasPermission('system:permission:view'))

/**
 * 是否允许新增权限。
 */
const canCreate = computed(() => session.hasPermission('system:permission:create'))

/**
 * 是否允许修改权限名称与说明。
 */
const canUpdate = computed(() => session.hasPermission('system:permission:update'))

/**
 * 是否允许删除权限。
 */
const canDelete = computed(() => session.hasPermission('system:permission:delete'))

/**
 * 表格分页配置，分页由后端完成。
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
 * 新增与编辑弹窗状态。
 */
const formVisible = ref(false)
const editingId = ref<string | null>(null)
const submitting = ref(false)
const formRef = ref<FormInst | null>(null)
const formErrors = ref<Record<string, string>>({})

/**
 * 新增与编辑表单模型。
 */
const form = reactive({ code: '', name: '', description: '' })

/**
 * 弹窗标题，按新增或编辑切换。
 */
const formTitle = computed(() => (editingId.value === null ? '新增权限' : '编辑权限'))

/**
 * 是否处于新增状态，权限代码只在创建时可填写。
 */
const isCreating = computed(() => editingId.value === null)

/**
 * 按当前筛选条件与分页读取权限列表。
 *
 * 失败时提示一次并清空表格，页面保持可用状态。
 */
async function loadPermissions(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchPermissions(buildQuery())
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
function buildQuery(): PageQuery & { code?: string; name?: string } {
  const code = (filters.code ?? '').trim()
  const name = (filters.name ?? '').trim()
  return {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    code: code === '' ? undefined : code,
    name: name === '' ? undefined : name,
  }
}

/**
 * 按当前筛选条件从第一页重新查询。
 */
function handleSearch(): void {
  pageNum.value = 1
  void loadPermissions()
}

/**
 * 清空筛选条件并重新查询。
 */
function handleReset(): void {
  filters.code = null
  filters.name = null
  pageNum.value = 1
  void loadPermissions()
}

/**
 * 手动刷新当前页列表。
 */
function handleRefresh(): void {
  void loadPermissions()
}

/**
 * 切换页码后重新查询。
 *
 * @param page 目标页码
 */
function handlePageChange(page: number): void {
  pageNum.value = page
  void loadPermissions()
}

/**
 * 切换每页条数后回到第一页重新查询。
 *
 * @param size 目标每页条数
 */
function handlePageSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void loadPermissions()
}

/**
 * 执行表单本地校验。
 *
 * @returns 校验通过时返回 true，失败提示已由表单项展示
 */
async function validateForm(): Promise<boolean> {
  const instance = formRef.value
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
 */
function applySubmitError(error: unknown): void {
  if (isBusinessCode(error, 400)) {
    const fieldErrors = readFieldErrors(error)
    if (Object.keys(fieldErrors).length > 0) {
      for (const [field, text] of Object.entries(fieldErrors)) {
        formErrors.value[field] = text
      }
      return
    }
  }
  message.error(resolveErrorMessage(error))
}

/**
 * 读取行的主键，缺失时提示一次并返回 null。
 *
 * @param row 表格行数据
 * @returns 主键字符串，缺失时为 null
 */
function readRowId(row: PermissionItem): string | null {
  if (!row.id) {
    message.warning('该记录缺少标识，无法执行该操作')
    return null
  }
  return row.id
}

/**
 * 重置表单校验状态，弹窗内容渲染后调用。
 */
function resetValidation(): void {
  void nextTick(() => {
    formRef.value?.restoreValidation()
  })
}

/**
 * 打开新增弹窗并重置表单。
 */
function openCreate(): void {
  editingId.value = null
  form.code = ''
  form.name = ''
  form.description = ''
  formErrors.value = {}
  formVisible.value = true
  resetValidation()
}

/**
 * 打开编辑弹窗并回填当前行；权限代码创建后不可修改。
 *
 * @param row 表格行数据
 */
function openEdit(row: PermissionItem): void {
  const id = readRowId(row)
  if (!id) {
    return
  }
  editingId.value = id
  form.code = row.code ?? ''
  form.name = row.name ?? ''
  form.description = row.description ?? ''
  formErrors.value = {}
  formVisible.value = true
  resetValidation()
}

/**
 * 保存新增或修改结果，成功后刷新列表。
 *
 * 修改只提交名称与说明：权限代码是授权关系与后端注解的引用键，改动会让既有授权指向另一种语义。
 */
async function handleSubmit(): Promise<void> {
  if (!(await validateForm())) {
    return
  }
  formErrors.value = {}
  submitting.value = true
  try {
    const code = form.code.trim()
    const name = form.name.trim()
    const description = form.description.trim()
    if (editingId.value === null) {
      await createPermission({ code, name, description })
      message.success('权限已创建')
      pageNum.value = 1
    } else {
      await updatePermission(editingId.value, { name, description })
      message.success('权限已更新')
    }
    formVisible.value = false
    await loadPermissions()
  } catch (error) {
    applySubmitError(error)
  } finally {
    submitting.value = false
  }
}

/**
 * 删除权限；仍被角色引用时后端返回 409，这里只提示一次。
 *
 * @param row 表格行数据
 */
async function handleDelete(row: PermissionItem): Promise<void> {
  const id = readRowId(row)
  if (!id) {
    return
  }
  try {
    await deletePermission(id)
    message.success('权限已删除')
    if (rows.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await loadPermissions()
  } catch (error) {
    message.error(resolveErrorMessage(error))
  }
}

/**
 * 读取权限代码中的模块前缀。
 *
 * @param code 权限代码
 * @returns 模块前缀，缺失时返回占位符
 */
function readModule(code: string | undefined): string {
  const module = (code ?? '').split(':')[0]
  return module || '—'
}

/**
 * 渲染行内操作按钮，按权限决定是否展示。
 *
 * @param row 表格行数据
 * @returns 操作按钮组节点
 */
function renderActions(row: PermissionItem): VNodeChild {
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
  if (canDelete.value) {
    actions.push(
      h(
        NPopconfirm,
        { onPositiveClick: () => void handleDelete(row) },
        {
          trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
          default: () => '删除后引用该权限的角色授权一并解除，确认删除？',
        },
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
function rowKey(row: PermissionItem): string {
  return row.id ?? ''
}

/**
 * 权限列表列定义，操作列按当前权限动态生成。
 */
const columns = computed<DataTableColumns<PermissionItem>>(() => [
  { title: '权限代码', key: 'code', minWidth: 200, ellipsis: { tooltip: true } },
  { title: '模块', key: 'module', width: 120, render: (row) => readModule(row.code) },
  { title: '权限名称', key: 'name', minWidth: 160, ellipsis: { tooltip: true } },
  {
    title: '说明',
    key: 'description',
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (row) => row.description || '—',
  },
  { title: '操作', key: 'actions', width: 170, render: (row) => renderActions(row) },
])

onMounted(() => {
  if (!canView.value) {
    return
  }
  void loadPermissions()
})
</script>

<template>
  <div class="permission-list">
    <PageHeader
      title="权限管理"
      description="接口权限代码的维护，权限代码创建后不可修改"
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
          新增权限
        </NButton>
      </template>
    </PageHeader>

    <NCard>
      <div class="filter-bar">
        <NInput
          v-model:value="filters.code"
          class="filter-bar__item"
          clearable
          placeholder="按权限代码搜索"
          @keyup.enter="handleSearch"
        />
        <NInput
          v-model:value="filters.name"
          class="filter-bar__item"
          clearable
          placeholder="按权限名称搜索"
          @keyup.enter="handleSearch"
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
        :scroll-x="900"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />

      <p
        v-if="!canView"
        class="form-hint"
      >
        当前账号没有权限查看权限列表，请联系管理员授权后再试；按钮显示不替代后端授权。
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
        {{ PERMISSION_CODE_DESCRIPTION }}。权限代码创建后不可修改，避免既有授权指向另一种语义。
      </p>
      <NForm
        ref="formRef"
        :model="form"
        :rules="FORM_RULES"
        label-placement="top"
      >
        <NFormItem
          label="权限代码"
          path="code"
          :validation-status="formErrors.code ? 'error' : undefined"
          :feedback="formErrors.code"
        >
          <NInput
            v-model:value="form.code"
            :disabled="!isCreating"
            placeholder="例如 system:role:update"
          />
        </NFormItem>
        <NFormItem
          label="权限名称"
          path="name"
          :validation-status="formErrors.name ? 'error' : undefined"
          :feedback="formErrors.name"
        >
          <NInput
            v-model:value="form.name"
            clearable
            placeholder="不超过 64 个字符"
          />
        </NFormItem>
        <NFormItem
          label="权限说明"
          path="description"
          :validation-status="formErrors.description ? 'error' : undefined"
          :feedback="formErrors.description"
        >
          <NInput
            v-model:value="form.description"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="不超过 255 个字符，可为空"
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
