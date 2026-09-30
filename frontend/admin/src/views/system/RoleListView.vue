<script setup lang="ts">
import type { PageQuery } from '@orange-forge/api-client'
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
  NSpace,
  NSpin,
  NTag,
  NText,
  NTree,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
  type PaginationProps,
  type TreeOption,
} from 'naive-ui'
import { computed, h, nextTick, onMounted, reactive, ref, type VNodeChild } from 'vue'

import {
  createRole,
  deleteRole,
  fetchMenuTree,
  fetchRoleDetail,
  fetchRoles,
  grantRole,
  updateRole,
  type MenuNode,
  type RoleDetail,
} from '@/api/system'
import AppIcon from '@/components/AppIcon.vue'
import PageBody from '@/components/PageBody.vue'
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
 * 新增与编辑表单校验规则，字段名与后端请求字段保持一致。
 */
const FORM_RULES: FormRules = {
  code: [
    { required: true, message: '请输入角色代码', trigger: ['blur', 'input'] },
    { max: 64, message: '角色代码不能超过 64 个字符', trigger: ['blur', 'input'] },
  ],
  name: [
    { required: true, message: '请输入角色名称', trigger: ['blur', 'input'] },
    { max: 64, message: '角色名称不能超过 64 个字符', trigger: ['blur', 'input'] },
  ],
  description: [{ max: 255, message: '角色说明不能超过 255 个字符', trigger: ['blur', 'input'] }],
  sortNo: [{ type: 'number', required: true, message: '请输入展示顺序', trigger: ['blur', 'change'] }],
}

/**
 * 角色授权树上的权限摘要：该节点会授予哪些接口权限。
 */
interface MenuPermissionSummary {
  /**
   * 菜单节点 ID。
   */
  menuId: string
  /**
   * 节点声明的权限标识。
   */
  codes: string[]
}

const session = useSessionStore()
const message = useMessage()

/**
 * 当前页的角色记录。
 */
const rows = ref<RoleDetail[]>([])

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
 * 筛选条件：角色名称关键字，空值表示不筛选。
 */
const filters = reactive<{ name: string | null }>({ name: null })

/**
 * 是否允许查看角色列表。
 */
const canView = computed(() => session.hasPermission('system:role:view'))

/**
 * 是否允许新增角色。
 */
const canCreate = computed(() => session.hasPermission('system:role:create'))

/**
 * 是否允许修改角色。
 */
const canUpdate = computed(() => session.hasPermission('system:role:update'))

/**
 * 是否允许删除角色。
 */
const canDelete = computed(() => session.hasPermission('system:role:delete'))

/**
 * 是否允许为角色授权菜单与权限。
 */
const canGrant = computed(() => session.hasPermission('system:role:grant'))

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
const form = reactive<{ code: string; name: string; description: string; sortNo: number | null }>({
  code: '',
  name: '',
  description: '',
  sortNo: 0,
})

/**
 * 授权弹窗状态。
 */
const grantVisible = ref(false)
const grantLoading = ref(false)
const grantSubmitting = ref(false)
const grantTargetId = ref<string | null>(null)
const grantTargetName = ref('')

/**
 * 授权弹窗数据：菜单树、节点权限摘要与当前勾选结果。
 *
 * 权限不再单独勾选：角色获得的接口权限由勾选的菜单节点声明推导，
 * 因此这里只维护菜单勾选状态，另外保存一份“节点到权限标识”的索引用于界面提示。
 */
const menuTreeOptions = ref<TreeOption[]>([])
const menuPermissions = ref<MenuPermissionSummary[]>([])
const selectedMenuIds = ref<Array<string | number>>([])

/**
 * 弹窗标题，按新增或编辑切换。
 */
const formTitle = computed(() => (editingId.value === null ? '新增角色' : '编辑角色'))

/**
 * 授权弹窗标题，带上角色名称便于确认对象。
 */
const grantTitle = computed(() => `授权：${grantTargetName.value || '—'}`)

/**
 * 按当前筛选条件与分页读取角色列表。
 *
 * 失败时提示一次并清空表格，页面保持可用状态。
 */
async function loadRoles(): Promise<void> {
  loading.value = true
  try {
    const result = await fetchRoles(buildQuery())
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
function buildQuery(): PageQuery & { name?: string } {
  const name = (filters.name ?? '').trim()
  return {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    name: name === '' ? undefined : name,
  }
}

/**
 * 按当前筛选条件从第一页重新查询。
 */
function handleSearch(): void {
  pageNum.value = 1
  void loadRoles()
}

/**
 * 清空筛选条件并重新查询。
 */
function handleReset(): void {
  filters.name = null
  pageNum.value = 1
  void loadRoles()
}

/**
 * 手动刷新当前页列表。
 */
function handleRefresh(): void {
  void loadRoles()
}

/**
 * 切换页码后重新查询。
 *
 * @param page 目标页码
 */
function handlePageChange(page: number): void {
  pageNum.value = page
  void loadRoles()
}

/**
 * 切换每页条数后回到第一页重新查询。
 *
 * @param size 目标每页条数
 */
function handlePageSizeChange(size: number): void {
  pageSize.value = size
  pageNum.value = 1
  void loadRoles()
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
 * @param errors 字段错误目标对象
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
 * 读取行的主键，缺失时提示一次并返回 null。
 *
 * @param row 表格行数据
 * @returns 主键字符串，缺失时为 null
 */
function readRowId(row: RoleDetail): string | null {
  if (!row.id) {
    message.warning('该记录缺少标识，无法执行该操作')
    return null
  }
  return row.id
}

/**
 * 重置表单校验状态，弹窗内容渲染后调用。
 *
 * @param instance 表单实例
 */
function resetValidation(instance: FormInst | null): void {
  void nextTick(() => {
    instance?.restoreValidation()
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
  form.sortNo = 0
  formErrors.value = {}
  formVisible.value = true
  resetValidation(formRef.value)
}

/**
 * 打开编辑弹窗并回填当前行。
 *
 * @param row 表格行数据
 */
function openEdit(row: RoleDetail): void {
  const id = readRowId(row)
  if (!id) {
    return
  }
  editingId.value = id
  form.code = row.code ?? ''
  form.name = row.name ?? ''
  form.description = row.description ?? ''
  form.sortNo = row.sortNo ?? 0
  formErrors.value = {}
  formVisible.value = true
  resetValidation(formRef.value)
}

/**
 * 保存新增或编辑结果，成功后刷新列表。
 *
 * 新增时只提交角色本身，权限与菜单在授权弹窗中单独处理，避免一个表单承担多种语义。
 */
async function handleSubmit(): Promise<void> {
  if (!(await validateForm(formRef.value))) {
    return
  }
  formErrors.value = {}
  submitting.value = true
  try {
    const payload = {
      code: form.code.trim(),
      name: form.name.trim(),
      description: form.description.trim(),
      sortNo: form.sortNo ?? 0,
    }
    if (editingId.value === null) {
      await createRole(payload)
      message.success('角色已创建')
      pageNum.value = 1
    } else {
      await updateRole(editingId.value, payload)
      message.success('角色已更新')
    }
    formVisible.value = false
    await loadRoles()
  } catch (error) {
    applySubmitError(error, formErrors.value)
  } finally {
    submitting.value = false
  }
}

/**
 * 删除角色；被账号或授权关系引用时后端返回 409，这里只提示一次。
 *
 * @param row 表格行数据
 */
async function handleDelete(row: RoleDetail): Promise<void> {
  const id = readRowId(row)
  if (!id) {
    return
  }
  try {
    await deleteRole(id)
    message.success('角色已删除')
    if (rows.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await loadRoles()
  } catch (error) {
    message.error(resolveErrorMessage(error))
  }
}

/**
 * 当前勾选菜单会授予的接口权限标识，去重后按代码排序。
 *
 * 用于在授权弹窗里实时展示“保存后这个角色能调用哪些接口”，
 * 实际权限仍由后端按同一份菜单配置解析。
 */
const grantedPermissionCodes = computed(() => {
  const codes = new Set<string>()
  for (const summary of menuPermissions.value) {
    if (!selectedMenuIds.value.includes(summary.menuId)) {
      continue
    }
    for (const code of summary.codes) {
      codes.add(code)
    }
  }
  return [...codes].sort()
})

/**
 * 把菜单树转换为树组件数据，并在节点标签上标注该节点会授予的权限数量。
 *
 * @param nodes 菜单树
 * @returns 树组件选项，缺少标识的节点被忽略
 */
function toMenuTreeOptions(nodes: MenuNode[]): TreeOption[] {
  const options: TreeOption[] = []
  for (const node of nodes) {
    if (!node.id) {
      continue
    }
    const children = toMenuTreeOptions(node.children ?? [])
    const permissionCount = (node.permissions ?? []).length
    const permissionText = permissionCount > 0 ? `（授予 ${permissionCount} 个权限）` : ''
    options.push({
      key: node.id,
      label: `${node.name ?? '未命名菜单'}${permissionText}`,
      children: children.length > 0 ? children : undefined,
    })
  }
  return options
}

/**
 * 收集菜单树中每个节点声明的权限标识。
 *
 * @param nodes 菜单树
 * @returns 节点权限摘要，缺少标识的节点被忽略
 */
function collectMenuPermissions(nodes: MenuNode[]): MenuPermissionSummary[] {
  const summaries: MenuPermissionSummary[] = []
  for (const node of nodes) {
    if (node.id) {
      const codes = (node.permissions ?? [])
        .map((permission) => permission.code ?? '')
        .filter((code) => code !== '')
      if (codes.length > 0) {
        summaries.push({ menuId: node.id, codes })
      }
    }
    summaries.push(...collectMenuPermissions(node.children ?? []))
  }
  return summaries
}

/**
 * 把选中值收窄为字符串 ID 列表。
 *
 * @param values 树或复选框组的选中值
 * @returns 字符串 ID 列表
 */
function toIdList(values: Array<string | number>): string[] {
  return values.map((value) => String(value))
}

/**
 * 打开授权弹窗，读取角色已授予的菜单与全部可授权菜单。
 *
 * 权限标识不单独读取：它内嵌在菜单树节点上，勾选菜单即决定权限。
 *
 * @param row 表格行数据
 */
async function openGrant(row: RoleDetail): Promise<void> {
  const id = readRowId(row)
  if (!id) {
    return
  }
  grantTargetId.value = id
  grantTargetName.value = row.name ?? row.code ?? ''
  menuTreeOptions.value = []
  menuPermissions.value = []
  selectedMenuIds.value = []
  grantVisible.value = true
  grantLoading.value = true
  try {
    const [detail, menus] = await Promise.all([fetchRoleDetail(id), fetchMenuTree()])
    selectedMenuIds.value = toIdList(detail.menuIds ?? [])
    menuTreeOptions.value = toMenuTreeOptions(menus)
    menuPermissions.value = collectMenuPermissions(menus)
  } catch (error) {
    grantVisible.value = false
    message.error(resolveErrorMessage(error))
  } finally {
    grantLoading.value = false
  }
}

/**
 * 同步菜单树勾选结果。
 *
 * @param keys 勾选节点的键集合
 */
function handleMenuCheckedKeys(keys: Array<string | number>): void {
  selectedMenuIds.value = [...keys]
}

/**
 * 保存角色的菜单授权；全量替换语义，未勾选的菜单与随之而来的权限会被解除。
 */
async function handleGrantSubmit(): Promise<void> {
  const id = grantTargetId.value
  if (!id) {
    return
  }
  grantSubmitting.value = true
  try {
    await grantRole(id, {
      menuIds: toIdList(selectedMenuIds.value),
    })
    message.success('授权已保存')
    grantVisible.value = false
    await loadRoles()
  } catch (error) {
    message.error(resolveErrorMessage(error))
  } finally {
    grantSubmitting.value = false
  }
}

/**
 * 渲染内置超级管理员角色标记。
 *
 * @param superRole 是否为配置识别的内置超级管理员角色
 * @returns 标签节点
 */
function renderSuperRole(superRole: boolean | undefined): VNodeChild {
  if (!superRole) {
    return h(NText, { depth: 3 }, { default: () => '自定义' })
  }
  return h(NTag, { size: 'small', bordered: false, type: 'warning' }, { default: () => '内置' })
}

/**
 * 渲染行内操作按钮，按权限决定是否展示。
 *
 * @param row 表格行数据
 * @returns 操作按钮组节点
 */
function renderActions(row: RoleDetail): VNodeChild {
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
  if (canGrant.value) {
    actions.push(
      h(
        NButton,
        { size: 'small', quaternary: true, onClick: () => void openGrant(row) },
        { default: () => '授权' },
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
          default: () => '删除后该角色的授权关系一并解除，且无法恢复，确认删除？',
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
function rowKey(row: RoleDetail): string {
  return row.id ?? ''
}

/**
 * 角色列表列定义，操作列按当前权限动态生成。
 */
const columns = computed<DataTableColumns<RoleDetail>>(() => [
  { title: '角色代码', key: 'code', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '角色名称', key: 'name', minWidth: 150, ellipsis: { tooltip: true } },
  {
    title: '说明',
    key: 'description',
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (row) => row.description || '—',
  },
  { title: '展示顺序', key: 'sortNo', width: 100, render: (row) => row.sortNo ?? 0 },
  { title: '类型', key: 'superRole', width: 100, render: (row) => renderSuperRole(row.superRole) },
  { title: '操作', key: 'actions', width: 230, render: (row) => renderActions(row) },
])

onMounted(() => {
  if (!canView.value) {
    return
  }
  void loadRoles()
})
</script>

<template>
  <PageBody>
    <NCard>
      <PageHeader
        title="角色列表"
        description="维护角色信息与菜单授权"
        icon="shield"
      >
        <template #actions>
          <NButton
            class="action-button"
            type="primary"
            secondary
            :disabled="!canView"
            :loading="loading"
            @click="handleRefresh"
          >
            <template #icon>
              <AppIcon
                name="refresh"
                :size="16"
              />
            </template>
            刷新列表
          </NButton>
          <NButton
            v-if="canCreate"
            class="action-button"
            type="primary"
            @click="openCreate"
          >
            <template #icon>
              <AppIcon
                name="plus"
                :size="16"
              />
            </template>新增角色
          </NButton>
        </template>
      </PageHeader>
      <div class="filter-bar">
        <NInput
          v-model:value="filters.name"
          class="filter-bar__item"
          clearable
          placeholder="按角色名称搜索"
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
        :scroll-x="960"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />

      <p
        v-if="!canView"
        class="form-hint"
      >
        当前账号没有角色查看权限，请联系管理员授权后再试；按钮显示不替代后端授权。
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
        角色代码是稳定标识，保存前统一去空格并转为小写；权限与菜单在“授权”中单独配置。
      </p>
      <NForm
        ref="formRef"
        :model="form"
        :rules="FORM_RULES"
        label-placement="top"
      >
        <NFormItem
          label="角色代码"
          path="code"
          :validation-status="formErrors.code ? 'error' : undefined"
          :feedback="formErrors.code"
        >
          <NInput
            v-model:value="form.code"
            clearable
            placeholder="不超过 64 个字符，例如 ops-admin"
          />
        </NFormItem>
        <NFormItem
          label="角色名称"
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
          label="角色说明"
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
        <NFormItem
          label="展示顺序"
          path="sortNo"
          :validation-status="formErrors.sortNo ? 'error' : undefined"
          :feedback="formErrors.sortNo"
        >
          <NInputNumber
            v-model:value="form.sortNo"
            :min="0"
            :max="9999"
            placeholder="数字越小越靠前"
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
      v-model:show="grantVisible"
      preset="card"
      :title="grantTitle"
      :mask-closable="false"
      :style="{ width: 'min(900px, 94vw)' }"
    >
      <NSpin :show="grantLoading">
        <p class="form-hint">
          授权为全量替换：保存后该角色只保留本次勾选的菜单节点，未勾选的会被解除。
          接口权限由勾选菜单节点上声明的权限标识推导，不需要也不能单独勾选。
        </p>
        <div class="grant">
          <section class="grant__panel">
            <h4>可见菜单与权限</h4>
            <p class="grant__hint">
              勾选父节点会一并勾选子节点；标签中的“授予 N 个权限”即该节点带来的接口权限。
            </p>
            <NTree
              block-line
              cascade
              checkable
              default-expand-all
              :data="menuTreeOptions"
              :checked-keys="selectedMenuIds"
              @update:checked-keys="handleMenuCheckedKeys"
            />
            <NText
              v-if="menuTreeOptions.length === 0 && !grantLoading"
              depth="3"
            >
              暂无可授予的菜单
            </NText>
          </section>
          <section class="grant__panel">
            <h4>保存后生效的权限</h4>
            <p class="grant__hint">
              由当前勾选推导，保存后立即对该角色生效；此处只读，如需调整请改菜单节点上的权限标识。
            </p>
            <div
              v-if="grantedPermissionCodes.length > 0"
              class="grant__codes"
            >
              <NTag
                v-for="code in grantedPermissionCodes"
                :key="code"
                size="small"
                :bordered="false"
                type="info"
              >
                {{ code }}
              </NTag>
            </div>
            <NText
              v-else
              depth="3"
            >
              当前勾选不包含任何接口权限
            </NText>
          </section>
        </div>
      </NSpin>
      <template #footer>
        <div class="modal-actions">
          <NButton
            :disabled="grantSubmitting"
            @click="grantVisible = false"
          >
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="grantSubmitting"
            @click="handleGrantSubmit"
          >
            保存授权
          </NButton>
        </div>
      </template>
    </NModal>
  </PageBody>
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
  width: 240px;
}

.form-hint {
  margin: 0 0 12px;
  color: #8290a2;
  font-size: 12px;
  line-height: 1.7;
}

.grant {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.grant__panel {
  max-height: 420px;
  overflow: auto;
  border: 1px solid #e8ecf2;
  border-radius: 12px;
  padding: 12px 14px;
  background: #f7f9fc;
}

.grant__panel h4 {
  margin: 0 0 4px;
  color: #17212b;
  font-size: 13px;
}

.grant__hint {
  margin: 0 0 10px;
  color: #8290a2;
  font-size: 11px;
  line-height: 1.7;
}

.grant__codes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 900px) {
  .grant {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .filter-bar__item {
    width: 100%;
  }
}
</style>
