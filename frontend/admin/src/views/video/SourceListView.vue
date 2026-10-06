<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NModal, NPopconfirm, NSelect, NSpace, NSwitch, NTag, type DataTableColumns, type FormInst } from 'naive-ui'
import { videoApi, type Source, type SourceInput, type SourceCategory, type Category } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { categoryOptions, videoZone } from './video-options'
import './video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const sources = ref<Source[]>([])
const localCategories = ref<Category[]>([])
const options = computed(() => categoryOptions(localCategories.value))
const search = ref('')
const rows = computed(() => sources.value.filter(s => s.name.includes(search.value)))
const show = ref(false)
const editingId = ref<string>()
const formRef = ref<FormInst>()
const form = reactive<SourceInput>({ name: '', apiUrl: '', enabled: true, sourceZone: videoZone })
const mapping = ref<Source>()
const sourceCategories = ref<SourceCategory[]>([])
const mappingShow = ref(false)
const mappingResult = ref('')
const unmappedCount = computed(() => sourceCategories.value.filter(row => !row.categoryId).length)

/**
 * 读取来源与本地分类。
 */
async function load() {
  await act(async () => {
    sources.value = await videoApi.sources()
    localCategories.value = await videoApi.categories()
  })
}

/**
 * 打开来源维护表单。
 */
function edit(row?: Source) {
  editingId.value = row?.id
  Object.assign(form, { name: row?.name ?? '', apiUrl: row?.apiUrl ?? '', enabled: row?.enabled ?? true, sourceZone: row?.sourceZone ?? videoZone })
  show.value = true
}

/**
 * 校验后保存来源，不自动启动采集。
 */
async function save() {
  if (busy.value) return
  const ok = await act(async () => {
    await formRef.value?.validate()
    await videoApi.saveSource(editingId.value, form)
  }, '资源站已保存')
  if (ok) { show.value = false; await load() }
}

/**
 * 打开源分类缓存，不隐式绑定分类。
 */
async function openMapping(row: Source) {
  mapping.value = row
  mappingResult.value = ''
  if (await act(async () => { sourceCategories.value = await videoApi.sourceCategories(row.id) })) mappingShow.value = true
}

/**
 * 一次匹配全部源分类，可创建缺少的分类，刷新本地选择项。
 */
async function autoMap(createMissing: boolean) {
  if (!mapping.value || busy.value) return
  const id = mapping.value.id
  await act(async () => {
    if (!sourceCategories.value.length) sourceCategories.value = await videoApi.refreshSource(id)
    const result = await videoApi.autoMapSource(id, createMissing)
    mappingResult.value = `新建 ${result.created} 个分类，新增绑定 ${result.bound} 个，保留已有绑定 ${result.preserved} 个，待手动确认 ${result.unresolved?.length ?? 0} 个。`
    sourceCategories.value = await videoApi.sourceCategories(id)
    localCategories.value = await videoApi.categories()
  }, '自动分类操作已完成')
}

/**
 * 通过来源接口检测连接并刷新分类缓存。
 */
async function refreshCategories() {
  if (!mapping.value || busy.value) return
  const sourceId = mapping.value.id
  await act(async () => { sourceCategories.value = await videoApi.refreshSource(sourceId) }, '连接成功，分类已更新')
}

/**
 * 将所选源分类绑定到管理员确认的本地分类。
 */
async function bind(row: SourceCategory, categoryId: string | null) {
  if (!mapping.value) return
  const sourceId = mapping.value.id
  if (await act(() => videoApi.mapCategory(sourceId, row.upstreamId, categoryId), categoryId ? '分类已绑定' : '已解除分类映射')) row.categoryId = categoryId ?? undefined
}

/**
 * 删除未引用来源，有引用时由后端阻止。
 */
async function remove(id: string) {
  if (await act(() => videoApi.deleteSource(id), '来源已删除')) await load()
}

const columns: DataTableColumns<Source> = [
  { title: '资源站', key: 'name', width: 150 },
  { title: '接口地址', key: 'apiUrl', ellipsis: { tooltip: true } },
  { title: '时区', key: 'sourceZone', width: 140 },
  { title: '状态', key: 'enabled', width: 90, render: row => h(NTag, { type: row.enabled ? 'success' : 'default', bordered: false }, () => row.enabled ? '启用' : '停用') },
  { title: '操作', key: 'actions', width: 250, render: row => h(NSpace, {}, () => [
    h(NButton, { size: 'small', onClick: () => openMapping(row) }, () => '分类映射'),
    session.hasPermission('video:source:edit') ? h(NButton, { size: 'small', onClick: () => edit(row) }, () => '编辑') : null,
    session.hasPermission('video:source:delete') ? h(NPopconfirm, { onPositiveClick: () => remove(row.id) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '删除'), default: () => '确认删除未引用的资源站？' }) : null,
  ]) },
]
const mappingColumns: DataTableColumns<SourceCategory> = [
  { title: '源编号', key: 'upstreamId', width: 90 },
  { title: '源分类', key: 'name', width: 180 },
  { title: '本地分类', key: 'categoryId', render: row => h(NSelect, {
    value: row.categoryId ?? null, options: options.value, filterable: true, clearable: true, placeholder: '请选择本地分类',
    disabled: busy.value || !session.hasPermission('video:source:edit'), onUpdateValue: (value: string | null) => bind(row, value),
  }) },
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
        title="资源站管理"
        description="JSON 接口配置、连接检测与分类映射"
        icon="database"
      >
        <template #actions>
          <NButton
            secondary
            type="primary"
            :loading="busy"
            @click="load"
          >
            刷新
          </NButton>
          <NButton
            v-if="session.hasPermission('video:source:edit')"
            type="primary"
            @click="edit()"
          >
            新增资源站
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NInput
          v-model:value="search"
          placeholder="搜索资源站名称"
          clearable
          class="video-filters"
        />
        <NDataTable
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :scroll-x="850"
        />
      </div>
    </NCard>
    <NModal
      v-model:show="show"
      preset="card"
      :title="editingId ? '编辑资源站' : '新增资源站'"
      class="video-modal"
    >
      <NForm
        ref="formRef"
        :model="form"
        label-placement="top"
        :rules="{ name: { required: true, message: '请输入名称', trigger: 'blur' }, apiUrl: { required: true, message: '请输入接口地址', trigger: 'blur' } }"
      >
        <NFormItem
          label="名称"
          path="name"
          :feedback="errors.name"
          :validation-status="errors.name ? 'error' : undefined"
        >
          <NInput v-model:value="form.name" />
        </NFormItem>
        <NFormItem
          label="MacCMS JSON 接口地址"
          path="apiUrl"
          :feedback="errors.apiUrl"
          :validation-status="errors.apiUrl ? 'error' : undefined"
        >
          <NInput
            v-model:value="form.apiUrl"
            placeholder="https://…"
          />
        </NFormItem>
        <NFormItem label="来源时间的时区">
          <NInput v-model:value="form.sourceZone" />
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
    <NModal
      v-model:show="mappingShow"
      preset="card"
      :title="mapping?.name + ' · 分类映射'"
      class="video-modal"
    >
      <NSpace class="video-filters">
        <NButton
          v-if="session.hasPermission('video:source:edit')"
          type="primary"
          :loading="busy"
          @click="refreshCategories"
        >
          检测连接并读取分类
        </NButton>
        <NButton
          v-if="session.hasPermission('video:source:edit')"
          :loading="busy"
          @click="autoMap(false)"
        >
          自动识别并绑定
        </NButton>
        <NButton
          v-if="session.hasPermission('video:source:edit') && session.hasPermission('video:category:edit')"
          type="primary"
          :loading="busy"
          @click="autoMap(true)"
        >
          一键创建缺少分类并绑定
        </NButton>
      </NSpace>
      <NAlert
        :type="unmappedCount ? 'warning' : 'success'"
        :bordered="false"
        class="video-filters"
      >
        {{ mappingResult || `共 ${sourceCategories.length} 个源分类，${unmappedCount} 个未绑定。按名称及常见后缀匹配，已有绑定保持不变；歧义或停用分类可手动调整。` }}
      </NAlert>
      <NDataTable
        :columns="mappingColumns"
        :data="sourceCategories"
        :row-key="row => row.upstreamId"
        :scroll-x="560"
      />
    </NModal>
  </PageBody>
</template>
