<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NCard, NDataTable, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NRadioButton, NRadioGroup, NSelect, NSpace, NSwitch, NTag, type DataTableColumns } from 'naive-ui'
import { appConfigApi, type Section } from '@/api/app-config'
import { videoApi, type Category } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { categoryOptions } from '@/views/video/video-options'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const rows = ref<Section[]>([])
const categories = ref<Category[]>([])
const categorySelect = computed(() => categoryOptions(categories.value))
const videoSelect = ref<{ label: string; value: string }[]>([])
const show = ref(false)
const editingId = ref<string>()
const form = reactive({ title: '', sourceType: 'CATEGORY', categoryId: null as string | null, videoIds: [] as string[], itemLimit: 12, enabled: true, sortNo: 0 })

/**
 * 读取推荐位与可选分类。
 */
async function load() {
  await act(async () => {
    rows.value = await appConfigApi.sections()
    if (session.hasPermission('video:category:view') || session.hasPermission('video:video:view')) categories.value = await videoApi.categories()
  })
}

/**
 * 按片名搜索影片，追加到候选项且保留已选影片。
 */
async function searchVideos(keyword: string) {
  if (!keyword) return
  await act(async () => {
    const page = await videoApi.videos({ pageNum: 1, pageSize: 20, keyword })
    const selected = videoSelect.value.filter(option => form.videoIds.includes(option.value))
    const found = page.records.map(video => ({ label: `${video.name}${video.year ? `（${video.year}）` : ''}`, value: video.id }))
    videoSelect.value = [...selected, ...found.filter(option => !selected.some(item => item.value === option.value))]
  })
}

/**
 * 打开新增或编辑表单。
 */
function edit(row?: Section) {
  editingId.value = row?.id
  videoSelect.value = (row?.videos ?? []).map(video => ({ label: video.name ?? `已删除影片 ${video.id}`, value: video.id }))
  Object.assign(form, {
    title: row?.title ?? '', sourceType: row?.sourceType ?? 'CATEGORY', categoryId: row?.categoryId ?? null,
    videoIds: (row?.videos ?? []).map(video => video.id), itemLimit: row?.itemLimit ?? 12,
    enabled: row?.enabled ?? true, sortNo: row?.sortNo ?? 0,
  })
  show.value = true
}

/**
 * 保存推荐位，只提交当前数据来源需要的字段。
 */
async function save() {
  if (busy.value) return
  const ok = await act(() => appConfigApi.saveSection(editingId.value, {
    ...form,
    categoryId: form.sourceType === 'CATEGORY' ? form.categoryId ?? undefined : undefined,
    videoIds: form.sourceType === 'MANUAL' ? form.videoIds : undefined,
  }), '推荐位已保存')
  if (ok) { show.value = false; await load() }
}

/**
 * 逻辑删除推荐位。
 */
async function remove(id: string) {
  if (await act(() => appConfigApi.deleteSection(id), '推荐位已删除')) await load()
}

const columns: DataTableColumns<Section> = [
  { title: '顺序', key: 'sortNo', width: 70 },
  { title: '标题', key: 'title', minWidth: 140 },
  { title: '数据来源', key: 'sourceType', minWidth: 260, render: row => row.sourceType === 'CATEGORY'
    ? `按分类最近更新：${row.categoryName ?? '分类已删除'}`
    : `手动挑片：${row.videos.map(video => video.name ?? video.id).join('、')}` },
  { title: '条数', key: 'itemLimit', width: 70 },
  { title: '状态', key: 'enabled', width: 80, render: row => h(NTag, { type: row.enabled ? 'success' : 'default', bordered: false }, () => row.enabled ? '启用' : '停用') },
  { title: '操作', key: 'actions', width: 150, render: row => h(NSpace, {}, () => [
    session.hasPermission('app:section:edit') ? h(NButton, { size: 'small', onClick: () => edit(row) }, () => '编辑') : null,
    session.hasPermission('app:section:delete') ? h(NPopconfirm, { onPositiveClick: () => remove(row.id) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '删除'), default: () => '确认删除该推荐位？' }) : null,
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
        title="首页推荐位"
        description="App 首页各推荐行：按分类自动取最近更新的影片，或手动挑片并按顺序展示"
        icon="menu"
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
            v-if="session.hasPermission('app:section:edit')"
            type="primary"
            @click="edit()"
          >
            新增推荐位
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NDataTable
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :scroll-x="900"
        />
      </div>
    </NCard>
    <NModal
      v-model:show="show"
      preset="card"
      :title="editingId ? '编辑推荐位' : '新增推荐位'"
      class="video-modal"
    >
      <NForm label-placement="top">
        <div class="video-form-grid">
          <NFormItem
            label="标题"
            :feedback="errors.title"
            :validation-status="errors.title ? 'error' : undefined"
          >
            <NInput
              v-model:value="form.title"
              maxlength="64"
              placeholder="例如：热播电影"
            />
          </NFormItem>
          <NFormItem label="顺序">
            <NInputNumber
              v-model:value="form.sortNo"
              :min="0"
            />
          </NFormItem>
        </div>
        <NFormItem label="数据来源">
          <NRadioGroup v-model:value="form.sourceType">
            <NRadioButton value="CATEGORY">
              按分类自动
            </NRadioButton>
            <NRadioButton value="MANUAL">
              手动挑片
            </NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem
          v-if="form.sourceType === 'CATEGORY'"
          label="分类（包含子分类，按更新时间倒序）"
        >
          <NSelect
            v-model:value="form.categoryId"
            filterable
            clearable
            :options="categorySelect"
          />
        </NFormItem>
        <NFormItem
          v-else
          label="影片（输入片名搜索，按选择顺序展示）"
        >
          <NSelect
            v-model:value="form.videoIds"
            multiple
            filterable
            remote
            :options="videoSelect"
            placeholder="输入片名搜索"
            @search="searchVideos"
          />
        </NFormItem>
        <div class="video-form-grid">
          <NFormItem
            label="最多展示条数"
            :feedback="errors.itemLimit"
            :validation-status="errors.itemLimit ? 'error' : undefined"
          >
            <NInputNumber
              v-model:value="form.itemLimit"
              :min="1"
            />
          </NFormItem>
          <NFormItem label="启用">
            <NSwitch v-model:value="form.enabled" />
          </NFormItem>
        </div>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="show = false">
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="busy"
            :disabled="!form.title || (form.sourceType === 'CATEGORY' ? !form.categoryId : !form.videoIds.length)"
            @click="save"
          >
            保存
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
