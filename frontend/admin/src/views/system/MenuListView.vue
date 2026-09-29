<script setup lang="ts">
import {
  NButton,
  NCard,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NPopconfirm,
  NSpace,
  NSpin,
  NText,
  NTree,
  useMessage,
  type FormInst,
  type FormRules,
  type TreeOption,
} from 'naive-ui'
import { computed, h, nextTick, onMounted, reactive, ref, type VNodeChild } from 'vue'

import { createMenu, deleteMenu, fetchMenuTree, updateMenu, type MenuNode } from '@/api/system'
import PageHeader from '@/components/PageHeader.vue'
import { resolveMenuRoute } from '@/router/menu-routes'
import { useSessionStore } from '@/stores/session'
import { isBusinessCode, readFieldErrors, resolveErrorMessage } from '@/utils/error-message'

/**
 * 顶级菜单的父菜单标识约定值，与后端保持一致。
 */
const ROOT_PARENT_ID = '0'

/**
 * 菜单表单校验规则，字段名与后端请求字段保持一致。
 *
 * 路由标识复用前端菜单白名单校验：只有已登记到本地路由的标识才允许保存，
 * 避免后端下发无法渲染的菜单节点。
 */
const FORM_RULES: FormRules = {
  name: [
    { required: true, message: '请输入菜单名称', trigger: ['blur', 'input'] },
    { max: 64, message: '菜单名称不能超过 64 个字符', trigger: ['blur', 'input'] },
  ],
  routeKey: [
    { max: 64, message: '路由标识不能超过 64 个字符', trigger: ['blur', 'input'] },
    {
      validator: (_rule, value) => {
        const routeKey = typeof value === 'string' ? value.trim() : ''
        if (routeKey === '' || resolveMenuRoute(routeKey)) {
          return true
        }
        return new Error('路由标识未在前端路由白名单中登记')
      },
      trigger: ['blur', 'input'],
    },
  ],
  sortNo: [{ type: 'number', required: true, message: '请输入展示顺序', trigger: ['blur', 'change'] }],
}

const session = useSessionStore()
const message = useMessage()

/**
 * 完整菜单树，来自后端接口。
 */
const menuNodes = ref<MenuNode[]>([])

/**
 * 菜单树加载状态。
 */
const loading = ref(false)

/**
 * 过滤条件：名称或路由标识关键字，空值表示不过滤。
 */
const filters = reactive<{ keyword: string | null }>({ keyword: null })

/**
 * 已应用的关键字，只有点击查询后才生效。
 */
const appliedKeyword = ref('')

/**
 * 新增与编辑弹窗状态。
 */
const formVisible = ref(false)
const editingId = ref<string | null>(null)
const submitting = ref(false)
const formRef = ref<FormInst | null>(null)
const formErrors = ref<Record<string, string>>({})

/**
 * 表单中的上级菜单标识与展示名称。
 */
const formParentId = ref<string | null>(null)
const formParentLabel = ref('顶级菜单')

/**
 * 菜单表单模型。
 */
const form = reactive<{ name: string; routeKey: string; sortNo: number | null }>({
  name: '',
  routeKey: '',
  sortNo: 0,
})

/**
 * 是否允许查看菜单。
 */
const canView = computed(() => session.hasPermission('system:menu:view'))

/**
 * 是否允许新增菜单。
 */
const canCreate = computed(() => session.hasPermission('system:menu:create'))

/**
 * 是否允许修改菜单。
 */
const canUpdate = computed(() => session.hasPermission('system:menu:update'))

/**
 * 是否允许删除菜单。
 */
const canDelete = computed(() => session.hasPermission('system:menu:delete'))

/**
 * 经过关键字过滤后要展示的菜单树。
 */
const visibleMenus = computed(() => filterMenus(menuNodes.value, appliedKeyword.value))

/**
 * 树组件数据。
 */
const menuTreeOptions = computed(() => toTreeOptions(visibleMenus.value))

/**
 * 菜单标识到节点的索引，供标签渲染读取排序与路由标识。
 */
const menuIndex = computed(() => {
  const index = new Map<string, MenuNode>()
  collectMenus(menuNodes.value, index)
  return index
})

/**
 * 弹窗标题，按新增或编辑切换。
 */
const formTitle = computed(() => (editingId.value === null ? '新增菜单' : '编辑菜单'))

/**
 * 读取菜单树。
 *
 * 菜单树没有分页与查询参数，关键字过滤在本地完成；失败时提示一次并保持页面可用。
 */
async function loadMenus(): Promise<void> {
  loading.value = true
  try {
    menuNodes.value = await fetchMenuTree()
  } catch (error) {
    menuNodes.value = []
    message.error(resolveErrorMessage(error))
  } finally {
    loading.value = false
  }
}

/**
 * 应用当前关键字过滤条件。
 */
function handleSearch(): void {
  appliedKeyword.value = (filters.keyword ?? '').trim()
}

/**
 * 清空关键字过滤条件。
 */
function handleReset(): void {
  filters.keyword = null
  appliedKeyword.value = ''
}

/**
 * 重新读取菜单树。
 */
function handleRefresh(): void {
  void loadMenus()
}

/**
 * 按关键字过滤菜单树，命中节点的祖先一并保留，避免子树脱离层级。
 *
 * @param nodes 菜单树
 * @param keyword 关键字，空字符串表示不过滤
 * @returns 过滤后的菜单树
 */
function filterMenus(nodes: MenuNode[], keyword: string): MenuNode[] {
  if (keyword === '') {
    return nodes
  }
  const lowerKeyword = keyword.toLowerCase()
  const result: MenuNode[] = []
  for (const node of nodes) {
    const text = `${node.name ?? ''} ${node.routeKey ?? ''}`.toLowerCase()
    const children = filterMenus(node.children ?? [], keyword)
    if (text.includes(lowerKeyword) || children.length > 0) {
      result.push({ ...node, children })
    }
  }
  return result
}

/**
 * 把菜单树转换为树组件数据。
 *
 * @param nodes 菜单树
 * @returns 树组件选项，缺少标识的节点被忽略
 */
function toTreeOptions(nodes: MenuNode[]): TreeOption[] {
  const options: TreeOption[] = []
  for (const node of nodes) {
    if (!node.id) {
      continue
    }
    const children = toTreeOptions(node.children ?? [])
    options.push({
      key: node.id,
      label: node.name ?? '未命名菜单',
      children: children.length > 0 ? children : undefined,
    })
  }
  return options
}

/**
 * 递归建立菜单标识到节点的索引。
 *
 * @param nodes 菜单树
 * @param index 目标索引
 */
function collectMenus(nodes: MenuNode[], index: Map<string, MenuNode>): void {
  for (const node of nodes) {
    if (node.id) {
      index.set(node.id, node)
    }
    collectMenus(node.children ?? [], index)
  }
}

/**
 * 判断父菜单标识是否表示顶级菜单。
 *
 * 后端用 0 或空值表示顶级，这里统一按空值处理，避免把 0 当成真实菜单 ID。
 *
 * @param parentId 父菜单标识
 * @returns 顶级菜单时返回 true
 */
function isRootParent(parentId: string | undefined): boolean {
  return !parentId || parentId === ROOT_PARENT_ID
}

/**
 * 读取上级菜单名称用于表单展示。
 *
 * @param parentId 上级菜单标识，空值或 0 表示顶级菜单
 * @returns 上级菜单名称
 */
function resolveParentLabel(parentId: string | undefined): string {
  if (isRootParent(parentId)) {
    return '顶级菜单'
  }
  return menuIndex.value.get(String(parentId))?.name ?? '已删除的菜单'
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
 * 重置表单校验状态，弹窗内容渲染后调用。
 */
function resetValidation(): void {
  void nextTick(() => {
    formRef.value?.restoreValidation()
  })
}

/**
 * 打开新增弹窗。
 *
 * @param parent 上级菜单节点，为空表示新增顶级菜单
 */
function prepareCreate(parent: MenuNode | null): void {
  editingId.value = null
  formParentId.value = parent?.id ?? null
  formParentLabel.value = parent ? parent.name ?? '未命名菜单' : '顶级菜单'
  form.name = ''
  form.routeKey = ''
  form.sortNo = 0
  formErrors.value = {}
  formVisible.value = true
  resetValidation()
}

/**
 * 打开新增顶级菜单弹窗。
 */
function openCreateRoot(): void {
  prepareCreate(null)
}

/**
 * 打开编辑弹窗并回填当前节点。
 *
 * @param node 菜单节点
 */
function openEdit(node: MenuNode): void {
  if (!node.id) {
    message.warning('该菜单缺少标识，无法编辑')
    return
  }
  editingId.value = node.id
  formParentId.value = isRootParent(node.parentId) ? null : node.parentId ?? null
  formParentLabel.value = resolveParentLabel(node.parentId)
  form.name = node.name ?? ''
  form.routeKey = node.routeKey ?? ''
  form.sortNo = node.sortNo ?? 0
  formErrors.value = {}
  formVisible.value = true
  resetValidation()
}

/**
 * 保存菜单新增或修改结果，成功后重新读取菜单树。
 *
 * 上级菜单由入口决定：编辑时沿用原层级，避免在树页面里误改结构。
 */
async function handleSubmit(): Promise<void> {
  if (!(await validateForm())) {
    return
  }
  formErrors.value = {}
  submitting.value = true
  try {
    const routeKey = form.routeKey.trim()
    const payload = {
      parentId: formParentId.value ?? undefined,
      name: form.name.trim(),
      routeKey: routeKey === '' ? undefined : routeKey,
      sortNo: form.sortNo ?? 0,
    }
    if (editingId.value === null) {
      await createMenu(payload)
      message.success('菜单已创建')
    } else {
      await updateMenu(editingId.value, payload)
      message.success('菜单已更新')
    }
    formVisible.value = false
    await loadMenus()
  } catch (error) {
    applySubmitError(error)
  } finally {
    submitting.value = false
  }
}

/**
 * 删除菜单节点；存在子菜单时后端返回 409，这里只提示一次。
 *
 * @param node 菜单节点
 */
async function handleDelete(node: MenuNode): Promise<void> {
  if (!node.id) {
    message.warning('该菜单缺少标识，无法删除')
    return
  }
  try {
    await deleteMenu(node.id)
    message.success('菜单已删除')
    await loadMenus()
  } catch (error) {
    message.error(resolveErrorMessage(error))
  }
}

/**
 * 渲染菜单节点标签：名称加路由标识与排序说明。
 *
 * @param info 树渲染信息
 * @returns 标签节点
 */
function renderMenuLabel(info: { option: TreeOption }): VNodeChild {
  const node = menuIndex.value.get(String(info.option.key))
  if (!node) {
    return String(info.option.label ?? '')
  }
  const metaText = node.routeKey ? `${node.routeKey} · 排序 ${node.sortNo ?? 0}` : `目录 · 排序 ${node.sortNo ?? 0}`
  return h(NSpace, { size: 10, align: 'center' }, {
    default: () => [h('span', null, node.name ?? '未命名菜单'), h(NText, { depth: 3 }, { default: () => metaText })],
  })
}

/**
 * 渲染菜单节点的操作按钮，按权限决定是否展示。
 *
 * 仍有子菜单的节点不允许删除，先删除子菜单再删除自身。
 *
 * @param node 菜单节点
 * @returns 操作按钮组节点
 */
function renderNodeActions(node: MenuNode): VNodeChild {
  const actions: VNodeChild[] = []
  if (canCreate.value) {
    actions.push(
      h(
        NButton,
        { size: 'tiny', quaternary: true, type: 'primary', onClick: () => prepareCreate(node) },
        { default: () => '新增子菜单' },
      ),
    )
  }
  if (canUpdate.value) {
    actions.push(
      h(NButton, { size: 'tiny', quaternary: true, onClick: () => openEdit(node) }, { default: () => '编辑' }),
    )
  }
  if (canDelete.value) {
    if ((node.children ?? []).length > 0) {
      actions.push(
        h(
          NButton,
          { size: 'tiny', quaternary: true, type: 'error', disabled: true, title: '存在子菜单，需先删除子菜单' },
          { default: () => '删除' },
        ),
      )
    } else {
      actions.push(
        h(
          NPopconfirm,
          { onPositiveClick: () => void handleDelete(node) },
          {
            trigger: () => h(NButton, { size: 'tiny', quaternary: true, type: 'error' }, { default: () => '删除' }),
            default: () => '删除后该菜单不再下发给任何角色，确认删除？',
          },
        ),
      )
    }
  }
  if (actions.length === 0) {
    return null
  }
  return h(NSpace, { size: 6, wrap: false }, { default: () => actions })
}

/**
 * 渲染菜单节点右侧的操作按钮。
 *
 * @param info 树渲染信息
 * @returns 操作按钮组节点
 */
function renderMenuSuffix(info: { option: TreeOption }): VNodeChild {
  const node = menuIndex.value.get(String(info.option.key))
  if (!node) {
    return null
  }
  return renderNodeActions(node)
}

onMounted(() => {
  if (!canView.value) {
    return
  }
  void loadMenus()
})
</script>

<template>
  <div class="menu-list">
    <PageHeader
      title="菜单管理"
      description="菜单树与前端路由白名单标识，目录节点留空路由标识"
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
          @click="openCreateRoot"
        >
          新增菜单
        </NButton>
      </template>
    </PageHeader>

    <NCard>
      <div class="filter-bar">
        <NInput
          v-model:value="filters.keyword"
          class="filter-bar__item"
          clearable
          placeholder="按名称或路由标识过滤"
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

      <NSpin :show="loading">
        <NTree
          block-line
          default-expand-all
          :data="menuTreeOptions"
          :render-label="renderMenuLabel"
          :render-suffix="renderMenuSuffix"
        />
        <NText
          v-if="menuTreeOptions.length === 0 && !loading"
          depth="3"
        >
          暂无菜单数据，可点击“新增菜单”创建顶级菜单
        </NText>
      </NSpin>

      <p
        v-if="!canView"
        class="form-hint"
      >
        当前账号没有菜单查看权限，请联系管理员授权后再试；按钮显示不替代后端授权。
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
        目录节点不填路由标识，页面节点必须填写前端路由白名单中已登记的标识；菜单只控制可见性，接口权限在角色授权中配置。
      </p>
      <NForm
        ref="formRef"
        :model="form"
        :rules="FORM_RULES"
        label-placement="top"
      >
        <NFormItem label="上级菜单">
          <NText depth="3">
            {{ formParentLabel }}
          </NText>
        </NFormItem>
        <NFormItem
          label="菜单名称"
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
          label="路由标识"
          path="routeKey"
          :validation-status="formErrors.routeKey ? 'error' : undefined"
          :feedback="formErrors.routeKey"
        >
          <NInput
            v-model:value="form.routeKey"
            clearable
            placeholder="目录留空，页面填本地路由标识"
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
            placeholder="同级内数字越小越靠前"
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
  width: 260px;
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
