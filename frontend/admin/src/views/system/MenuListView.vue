<script setup lang="ts">
import {
  NButton,
  NCard,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NPopconfirm,
  NSelect,
  NSpace,
  NSpin,
  NTag,
  NTabs,
  NTabPane,
  NText,
  NTree,
  useMessage,
  type FormInst,
  type FormRules,
  type TreeOption,
} from 'naive-ui'
import { computed, h, nextTick, onMounted, reactive, ref, type VNodeChild } from 'vue'

import { createMenu, deleteMenu, fetchMenuTree, updateMenu, type MenuNode } from '@/api/system'
import AppIcon from '@/components/AppIcon.vue'
import MenuPermissionEditor from '@/components/MenuPermissionEditor.vue'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { resolveMenuRoute } from '@/router/menu-routes'
import { useSessionStore } from '@/stores/session'
import { isBusinessCode, readFieldErrors, resolveErrorMessage } from '@/utils/error-message'

/**
 * 顶级菜单的父菜单标识约定值，与后端保持一致。
 */
const ROOT_PARENT_ID = '0'

/** 菜单类型选项。 */
const MENU_TYPE_OPTIONS = [
  { label: '目录', value: 'directory' },
  { label: '页面', value: 'page' },
]

/** 前端内置图标白名单；菜单表单直接可视化选择，不要求手输代码。 */
const ICON_OPTIONS = [
  { label: '工作台', value: 'dashboard' },
  { label: '系统设置', value: 'settings' },
  { label: '用户', value: 'users' },
  { label: '角色与安全', value: 'shield' },
  { label: '菜单', value: 'menu' },
  { label: '文件目录', value: 'folder' },
  { label: '文件', value: 'file' },
  { label: '数据库', value: 'database' },
  { label: '审计动态', value: 'activity' },
  { label: '操作记录', value: 'clipboard' },
  { label: '登录记录', value: 'login' },
]

/**
 * 菜单表单校验规则，字段名与后端请求字段保持一致。
 *
 * 路由标识复用前端菜单白名单校验：只有已登记到本地路由的标识才允许保存，
 * 避免后端下发无法渲染的菜单节点。权限标识的具体格式由权限编辑器即时校验，
 * 后端仍会独立校验一次，并把 permissions 下的字段错误返回到权限编辑区域。
 */
const FORM_RULES: FormRules = {
  name: [
    { required: true, message: '请输入菜单名称', trigger: ['blur', 'input'] },
    { max: 64, message: '菜单名称不能超过 64 个字符', trigger: ['blur', 'input'] },
  ],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: ['blur', 'change'] }],
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
 * 完整菜单树，来自后端接口；节点自带该节点声明的接口权限。
 */
const menuNodes = ref<MenuNode[]>([])

/**
 * 菜单树加载状态。
 */
const loading = ref(false)

/**
 * 选中菜单的详情与权限，集中展示在右侧。
 */
const selectedKeys = ref<string[]>([])
const selectedMenu = computed(() => menuIndex.value.get(selectedKeys.value[0] ?? ''))

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
 * 权限编辑器实例，打开弹窗时清空输入状态。
 */
const permissionEditorRef = ref<InstanceType<typeof MenuPermissionEditor> | null>(null)

/**
 * 表单中的上级菜单标识与展示名称。
 */
const formParentId = ref<string | null>(null)
const formParentLabel = ref('顶级菜单')

/**
 * 菜单表单模型。
 */
const form = reactive<{
  name: string
  menuType: string
  icon: string | null
  routeKey: string
  permissions: Array<{ code: string; name: string; description?: string }>
  sortNo: number | null
}>({
  name: '',
  menuType: 'page',
  icon: 'menu',
  routeKey: '',
  permissions: [],
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
 * 是否允许修改菜单与权限标识。
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
 * 菜单标识到节点的索引，供标签渲染读取排序、路由标识与权限。
 */
const menuIndex = computed(() => {
  const index = new Map<string, MenuNode>()
  for (const node of flattenMenus(menuNodes.value)) {
    if (node.id) {
      index.set(node.id, node)
    }
  }
  return index
})

/**
 * 全树声明的权限标识数量，用于页面提示。
 */
const totalPermissionCount = computed(() =>
  flattenMenus(menuNodes.value).reduce((total, node) => total + (node.permissions ?? []).length, 0),
)

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
 * 把菜单树展开为一维列表。
 *
 * @param nodes 菜单树
 * @returns 含全部层级的菜单节点列表
 */
function flattenMenus(nodes: MenuNode[]): MenuNode[] {
  const result: MenuNode[] = []
  for (const node of nodes) {
    result.push(node)
    result.push(...flattenMenus(node.children ?? []))
  }
  return result
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
    const permissionText = (node.permissions ?? []).map((item) => item.code ?? '').join(' ')
    const text = `${node.name ?? ''} ${node.routeKey ?? ''} ${permissionText}`.toLowerCase()
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
        const displayField = field.startsWith('permissions') || field === 'permCodes'
          ? 'permissions'
          : field
        formErrors.value[displayField] = text
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
  form.menuType = 'page'
  form.icon = 'menu'
  form.routeKey = ''
  form.permissions = []
  form.sortNo = 0
  formErrors.value = {}
  formVisible.value = true
  permissionEditorRef.value?.reset()
  resetValidation()
}

/**
 * 打开新增顶级菜单弹窗。
 */
function openCreateRoot(): void {
  prepareCreate(null)
}

/**
 * 打开编辑弹窗并回填当前节点与它声明的权限标识。
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
  form.menuType = (node as MenuNode & { menuType?: string }).menuType ?? (node.routeKey ? 'page' : 'directory')
  form.icon = (node as MenuNode & { icon?: string }).icon ?? 'menu'
  form.routeKey = node.routeKey ?? ''
  form.permissions = (node.permissions ?? [])
    .filter((permission) => permission.code && permission.name)
    .map((permission) => ({
      code: permission.code ?? '',
      name: permission.name ?? '',
      description: permission.description ?? '',
    }))
  form.sortNo = node.sortNo ?? 0
  formErrors.value = {}
  formVisible.value = true
  permissionEditorRef.value?.reset()
  resetValidation()
}

/**
 * 保存菜单新增或修改结果，成功后重新读取菜单树。
 *
 * 上级菜单由入口决定：编辑时沿用原层级，避免在树页面里误改结构。
 * 提交的权限标识即该节点最终声明，提交空数组表示不再声明任何接口权限。
 */
async function handleSubmit(): Promise<void> {
  if (!(await validateForm())) {
    return
  }
  formErrors.value = {}
  submitting.value = true
  try {
    const routeKey = form.menuType === 'directory' ? '' : form.routeKey.trim()
    const payload = {
      parentId: formParentId.value ?? undefined,
      name: form.name.trim(),
      menuType: form.menuType,
      icon: form.icon ?? undefined,
      routeKey: routeKey === '' ? undefined : routeKey,
      permissions: form.permissions.map((permission) => ({
        code: permission.code.trim(),
        name: permission.name.trim(),
        description: permission.description?.trim() || undefined,
      })),
      sortNo: form.sortNo ?? 0,
    }
    if (editingId.value === null) {
      await createMenu(payload)
      message.success('菜单已创建')
    } else {
      await updateMenu(editingId.value, payload)
      message.success('菜单与权限标识已更新')
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
 * 删除菜单节点；存在子菜单或已被角色授予时后端返回 409，这里只提示一次。
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
 * 渲染简洁菜单标签，详细资料由右侧选中面板展示。
 *
 * @param info 树渲染信息
 * @returns 标签节点
 */
function renderMenuLabel(info: { option: TreeOption }): VNodeChild {
  const node = menuIndex.value.get(String(info.option.key))
  if (!node) {
    return String(info.option.label ?? '')
  }
  const menuType = (node as MenuNode & { menuType?: string }).menuType ?? (node.routeKey ? 'page' : 'directory')
  const icon = (node as MenuNode & { icon?: string }).icon ?? 'menu'
  return h(NSpace, { size: 8, align: 'center', wrap: false }, {
    default: () => [
      h(AppIcon, { name: icon, size: 17 }),
      h('span', { class: 'menu-label__name' }, node.name ?? '未命名菜单'),
      h(NText, { depth: 3 }, { default: () => menuType === 'directory' ? '目录' : '' }),
    ],
  })
}

/**
 * 渲染图标下拉选项。
 *
 * @param option 图标选项
 * @returns 带图形与中文名称的选项
 */
function renderIconOption(option: { label?: string; value?: string | number }): VNodeChild {
  return h(NSpace, { align: 'center', size: 8 }, {
    default: () => [h(AppIcon, { name: String(option.value ?? 'menu'), size: 17 }), String(option.label ?? '')],
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
        { type: 'primary', secondary: true, onClick: () => prepareCreate(node) },
        { default: () => '新增子菜单', icon: () => h(AppIcon, { name: 'plus', size: 17 }) },
      ),
    )
  }
  if (canUpdate.value) {
    actions.push(
      h(NButton, { onClick: () => openEdit(node) }, { default: () => '编辑与权限', icon: () => h(AppIcon, { name: 'edit', size: 17 }) }),
    )
  }
  if (canDelete.value) {
    if ((node.children ?? []).length > 0) {
      actions.push(
        h(
          NButton,
          { type: 'error', ghost: true, disabled: true, title: '存在子菜单，需先删除子菜单' },
          { default: () => '删除', icon: () => h(AppIcon, { name: 'trash', size: 17 }) },
        ),
      )
    } else {
      actions.push(
        h(
          NPopconfirm,
          { onPositiveClick: () => void handleDelete(node) },
          {
            trigger: () => h(NButton, { type: 'error', ghost: true }, { default: () => '删除', icon: () => h(AppIcon, { name: 'trash', size: 17 }) }),
            default: () =>
              '删除后该菜单不再下发给任何角色，其声明的权限标识同时失效，确认删除？',
          },
        ),
      )
    }
  }
  if (actions.length === 0) {
    return null
  }
  return h(NSpace, { size: 10, wrap: true, class: 'menu-node-actions' }, { default: () => actions })
}

onMounted(() => {
  if (!canView.value) {
    return
  }
  void loadMenus()
})
</script>

<template>
  <PageBody>
    <NCard
      :bordered="false"
      class="menu-card"
    >
      <PageHeader
        title="菜单与权限"
        description="维护导航结构与菜单接口权限"
        icon="menu"
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
            @click="openCreateRoot"
          >
            <template #icon>
              <AppIcon
                name="plus"
                :size="16"
              />
            </template>
            新建菜单
          </NButton>
        </template>
      </PageHeader>

      <div class="menu-workspace">
        <NCard
          title="菜单结构"
          :bordered="false"
          class="menu-navigation"
        >
          <template #header-extra>
            <NText depth="3">
              {{ menuIndex.size }} 项
            </NText>
          </template>
          <div class="filter-bar">
            <NInput
              v-model:value="filters.keyword"
              clearable
              placeholder="菜单或权限"
              aria-label="搜索菜单或权限"
              @keyup.enter="handleSearch"
            />
            <NButton
              @click="handleSearch"
            >
              查询
            </NButton>
            <NButton
              @click="handleReset"
            >
              重置
            </NButton>
          </div>
          <NSpin :show="loading">
            <NTree
              v-model:selected-keys="selectedKeys"
              block-line
              default-expand-all
              :data="menuTreeOptions"
              :render-label="renderMenuLabel"
            />
            <NEmpty
              v-if="!menuTreeOptions.length && !loading"
              description="暂无匹配菜单"
            />
          </NSpin>
        </NCard>
        <NCard
          :bordered="false"
          class="menu-detail"
        >
          <template v-if="selectedMenu">
            <div class="detail-header">
              <div class="detail-heading">
                <AppIcon
                  :name="selectedMenu.icon ?? 'menu'"
                  :size="24"
                />
                <div>
                  <h2>{{ selectedMenu.name }}</h2>
                  <NText depth="3">
                    {{ selectedMenu.menuType === 'directory' ? '目录' : '页面' }} · {{ resolveParentLabel(selectedMenu.parentId) }}
                  </NText>
                </div>
              </div>
              <component :is="renderNodeActions(selectedMenu)" />
            </div>
            <dl class="menu-metadata">
              <div><dt>路由标识</dt><dd>{{ selectedMenu.routeKey || '—' }}</dd></div>
              <div><dt>展示顺序</dt><dd>{{ selectedMenu.sortNo ?? 0 }}</dd></div>
            </dl>
            <div class="permission-heading">
              <h3>接口权限</h3>
              <NTag
                :bordered="false"
                type="info"
                size="small"
              >
                {{ selectedMenu.permissions?.length ?? 0 }} 项
              </NTag>
            </div>
            <div
              v-if="selectedMenu.permissions?.length"
              class="permission-table-wrap"
            >
              <table class="permission-table">
                <thead>
                  <tr>
                    <th scope="col">
                      权限名称
                    </th><th scope="col">
                      权限代码
                    </th><th scope="col">
                      说明
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="permission in selectedMenu.permissions"
                    :key="permission.code"
                  >
                    <td><strong>{{ permission.name }}</strong></td>
                    <td><code class="permission-code">{{ permission.code }}</code></td>
                    <td>
                      <NText depth="3">
                        {{ permission.description || '—' }}
                      </NText>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <NEmpty
              v-if="!selectedMenu.permissions?.length"
              description="此菜单尚未配置接口权限"
            />
          </template>
          <NEmpty
            v-else
            :description="canView ? '选择左侧菜单，查看详情与权限' : '当前账号没有菜单查看权限'"
            class="menu-placeholder"
          >
            <template #extra>
              <NText depth="3">
                共登记 {{ totalPermissionCount }} 项权限
              </NText>
            </template>
          </NEmpty>
        </NCard>
      </div>
    </NCard>

    <NModal
      v-model:show="formVisible"
      preset="card"
      :title="formTitle"
      :mask-closable="false"
      :style="{
        width: 'min(920px, 94vw)',
        maxHeight: 'calc(100vh - 32px)',
        display: 'flex',
        flexDirection: 'column',
      }"
      content-style="overflow: auto"
    >
      <p class="form-hint">
        目录用于组织页面，页面需要选择图标并填写路由标识。权限配置保存后会立即对已授权角色生效。
      </p>
      <NForm
        ref="formRef"
        :model="form"
        :rules="FORM_RULES"
        label-placement="top"
      >
        <NTabs
          type="line"
          animated
        >
          <NTabPane
            name="basic"
            tab="基本信息"
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
            <div class="form-grid">
              <NFormItem
                label="菜单类型"
                path="menuType"
                :validation-status="formErrors.menuType ? 'error' : undefined"
                :feedback="formErrors.menuType"
              >
                <NSelect
                  v-model:value="form.menuType"
                  :options="MENU_TYPE_OPTIONS"
                />
              </NFormItem>
              <NFormItem
                label="菜单图标"
                path="icon"
                :validation-status="formErrors.icon ? 'error' : undefined"
                :feedback="formErrors.icon"
              >
                <NSelect
                  v-model:value="form.icon"
                  clearable
                  filterable
                  placeholder="请选择图标"
                  :options="ICON_OPTIONS"
                  :render-label="renderIconOption"
                />
              </NFormItem>
            </div>
            <NFormItem
              label="路由标识"
              path="routeKey"
              :validation-status="formErrors.routeKey ? 'error' : undefined"
              :feedback="formErrors.routeKey"
            >
              <NInput
                v-model:value="form.routeKey"
                clearable
                :disabled="form.menuType === 'directory'"
                placeholder="页面填写前端路由白名单标识"
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
          </NTabPane>
          <NTabPane
            name="permissions"
            :tab="`接口权限（${form.permissions.length}）`"
            display-directive="show"
          >
            <NFormItem
              label="接口权限"
              path="permissions"
              :validation-status="formErrors.permissions ? 'error' : undefined"
            >
              <MenuPermissionEditor
                ref="permissionEditorRef"
                v-model="form.permissions"
                :error="formErrors.permissions"
              />
            </NFormItem>
          </NTabPane>
        </NTabs>
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
  </PageBody>
</template>

<style scoped>
.menu-workspace {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.menu-navigation, .menu-detail { min-width: 0; box-shadow: none; }
.detail-header, .detail-heading, .permission-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail-header { justify-content: space-between; flex-wrap: wrap; row-gap: 16px; }
.menu-node-actions :deep(.n-button) { height: 38px; padding: 0 14px; font-size: 14px; }
h2 { margin: 0 0 4px; font-size: 20px; }
h3 { margin: 0; font-size: 15px; }
.menu-metadata { display: flex; flex-wrap: wrap; gap: 32px; margin: 28px 0; }
dt { font-size: 12px; opacity: .65; }
dd { margin: 6px 0 0; overflow-wrap: anywhere; }
.permission-heading { margin-bottom: 16px; }
.permission-table-wrap { overflow-x: auto; }
.permission-table { width: 100%; border-collapse: collapse; text-align: left; font-size: 13px; }
.permission-table th { padding: 12px 16px; background: var(--n-action-color); font-size: 12px; font-weight: 500; }
.permission-table td { padding: 16px; border-bottom: 1px solid var(--n-border-color); vertical-align: middle; }
.permission-table th:first-child { width: 22%; border-radius: 8px 0 0 8px; }
.permission-table th:nth-child(2) { width: 34%; }
.permission-table th:last-child { border-radius: 0 8px 8px 0; }
.permission-table strong { font-weight: 500; white-space: nowrap; }
.permission-table tbody tr:last-child td { border-bottom: 0; }
.permission-code { overflow-wrap: anywhere; font-size: 12px; color: var(--n-text-color); }
.menu-placeholder { padding: 80px 0; }
@media (max-width: 900px) {
  .menu-workspace { grid-template-columns: minmax(0, 1fr); }
  .menu-placeholder { padding: 28px 0; }
}

.filter-bar {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.filter-bar :deep(.n-input) { flex: 1; min-width: 0; width: auto; }
.filter-bar :deep(.n-button) { flex-shrink: 0; }
@media (max-width: 640px) {
  .permission-table { min-width: 560px; }
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
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
  .form-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }

  .filter-bar__item {
    width: 100%;
  }
}
</style>
