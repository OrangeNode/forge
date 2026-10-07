<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NSelect, NSpace, NSwitch, type DataTableColumns, type FormInst } from 'naive-ui'
import { videoApi, type BatchResult, type Category, type CategoryInput } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { categoryOptions } from './video-options'
import './video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const rows = ref<Category[]>([])
const options = computed(() => [{ label: '顶级分类', value: '0' }, ...categoryOptions(rows.value)])
const show = ref(false)
const editing = ref<string>()
const formRef = ref<FormInst>()
const form = reactive<CategoryInput>({ parentId: '0', name: '', sortNo: 0, enabled: true })
const selection = ref<string[]>([])
const batchResult = ref<BatchResult>()
const batchResultShow = ref(false)
const canDelete = computed(() => session.hasPermission('video:category:delete'))
const names = computed(() => {
  const map = new Map<string, string>()
  const walk = (items: Category[]) => items.forEach(item => { map.set(item.id, item.name); walk(item.children) })
  walk(rows.value)
  return map
})
const failureRows = computed(() => (batchResult.value?.failures ?? [])
  .map(item => ({ ...item, name: names.value.get(item.id) ?? item.id })))

/**
 * 读取本地分类树。
 */
async function load() {
  await act(async () => {
    rows.value = await videoApi.categories()
    selection.value = selection.value.filter(id => names.value.has(id))
  })
}

/**
 * 编辑当前分类，或为指定父分类新增节点。
 */
function edit(row?: Category, parentId = '0') {
  editing.value = row?.id
  Object.assign(form, { parentId: row?.parentId ?? parentId, name: row?.name ?? '', sortNo: row?.sortNo ?? 0, enabled: row?.enabled ?? true })
  show.value = true
}

/**
 * 保存分类，循环与引用规则由后端独立校验。
 */
async function save() {
  if (busy.value) return
  if (await act(async () => { await formRef.value?.validate(); await videoApi.saveCategory(editing.value, form) }, '分类已保存')) {
    show.value = false
    await load()
  }
}

/**
 * 删除分类，被影片或映射引用时不会删除。
 */
async function remove(id: string) { if (await act(() => videoApi.deleteCategory(id), '分类已删除')) await load() }

/**
 * 批量删除勾选分类；后端先删子分类，逐条返回被影片或子分类引用的未删除原因。
 */
async function removeSelected() {
  if (busy.value || !selection.value.length) return
  if (await act(async () => {
    batchResult.value = await videoApi.batchDeleteCategories(selection.value)
    batchResultShow.value = true
  })) {
    selection.value = []
    await load()
  }
}

const columns: DataTableColumns<Category> = [
  { type: 'selection', disabled: () => !canDelete.value },
  { title: '分类名称', key: 'name', minWidth: 200 },
  { title: '排序', key: 'sortNo', width: 100 },
  { title: '状态', key: 'enabled', width: 100, render: row => row.enabled ? '启用' : '停用' },
  { title: '操作', key: 'actions', width: 270, render: row => h(NSpace, {}, () => [
    session.hasPermission('video:category:edit') ? h(NButton, { size: 'small', onClick: () => edit(undefined, row.id) }, () => '新增子分类') : null,
    session.hasPermission('video:category:edit') ? h(NButton, { size: 'small', onClick: () => edit(row) }, () => '编辑') : null,
    session.hasPermission('video:category:delete') ? h(NPopconfirm, { onPositiveClick: () => remove(row.id) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '删除'), default: () => '确认删除未引用分类？' }) : null,
  ]) },
]
onMounted(load)
</script>
<template>
  <PageBody>
    <NCard
      class="video-panel"
      :bordered="false"
    >
      <PageHeader
        title="视频分类"
        description="维护本地分类树，分类编号不与资源站编号混用"
        icon="folder"
      >
        <template #actions>
          <NButton
            secondary
            type="primary"
            :loading="busy"
            @click="load"
          >
            刷新
          </NButton><NButton
            v-if="session.hasPermission('video:category:edit')"
            type="primary"
            @click="edit()"
          >
            新增分类
          </NButton><NPopconfirm
            v-if="canDelete"
            :disabled="busy || !selection.length"
            @positive-click="removeSelected"
          >
            <template #trigger>
              <NButton
                type="error"
                secondary
                :disabled="!selection.length"
                :loading="busy"
              >
                批量删除（{{ selection.length }}）
              </NButton>
            </template>
            确认删除已勾选的 {{ selection.length }} 个分类？子分类会先删除，有影片或未勾选子分类的分类将保留。
          </NPopconfirm>
        </template>
      </PageHeader>
      <div class="video-content">
        <NDataTable
          :data="rows"
          :columns="columns"
          :loading="busy"
          :row-key="row => row.id"
          :checked-row-keys="selection"
          :scroll-x="700"
          @update:checked-row-keys="keys => selection = keys as string[]"
        />
      </div>
    </NCard>
    <NModal
      v-model:show="batchResultShow"
      preset="card"
      title="批量删除结果"
      class="video-modal"
    >
      <NAlert
        v-if="batchResult"
        :type="batchResult.failures.length ? 'warning' : 'success'"
        :bordered="false"
        class="video-filters"
      >
        共 {{ batchResult.total }} 个，已删除 {{ batchResult.succeeded }} 个，未删除 {{ batchResult.failures.length }} 个。
      </NAlert>
      <NDataTable
        v-if="failureRows.length"
        :columns="[{ title: '分类', key: 'name', width: 160 }, { title: '未删除原因', key: 'message' }]"
        :data="failureRows"
        :max-height="360"
      />
    </NModal>
    <NModal
      v-model:show="show"
      preset="card"
      :title="editing ? '编辑分类' : '新增分类'"
      class="video-modal"
    >
      <NForm
        ref="formRef"
        :model="form"
        label-placement="top"
        :rules="{ name: { required: true, message: '请输入分类名称', trigger: 'blur' } }"
      >
        <NFormItem label="父分类">
          <NSelect
            v-model:value="form.parentId"
            :options="options"
            filterable
          />
        </NFormItem>
        <NFormItem
          label="名称"
          path="name"
          :feedback="errors.name"
          :validation-status="errors.name ? 'error' : undefined"
        >
          <NInput v-model:value="form.name" />
        </NFormItem>
        <NFormItem label="排序">
          <NInputNumber
            v-model:value="form.sortNo"
            :min="0"
          />
        </NFormItem>
        <NFormItem label="启用">
          <NSwitch v-model:value="form.enabled" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="show = false">
            取消
          </NButton><NButton
            type="primary"
            :loading="busy"
            @click="save"
          >
            保存
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
