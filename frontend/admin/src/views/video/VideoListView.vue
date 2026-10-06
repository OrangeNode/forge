<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NCard, NCollapse, NCollapseItem, NDataTable, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NSelect, NSpace, NSwitch, NTag, type DataTableColumns } from 'naive-ui'
import { videoApi, type Video, type VideoDetail, type VideoInput, type Category, type PlayLine, type Episode } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { categoryOptions, videoPageSize, videoPageSizeOptions } from './video-options'
import './video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const categories = ref<Category[]>([])
const options = computed(() => categoryOptions(categories.value))
const rows = ref<Video[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(videoPageSize)
const filters = reactive({ keyword: '', categoryId: null as string | null, year: null as number | null, area: '', language: '', published: null as string | null })
const show = ref(false)
const detail = ref<VideoDetail>()
const primaryId = ref<string | null>(null)
const form = ref<VideoInput>({ name: '', categoryId: '' })

/**
 * 查询管理端影片，不把大体积分集混入列表。
 */
async function load() {
  await act(async () => {
    const result = await videoApi.videos({ pageNum: page.value, pageSize: pageSize.value,
      keyword: filters.keyword || undefined, categoryId: filters.categoryId ?? undefined, year: filters.year ?? undefined,
      area: filters.area || undefined, language: filters.language || undefined,
      published: filters.published === null ? undefined : filters.published === 'on' })
    rows.value = result.records
    total.value = result.total
  })
}

/**
 * 筛选条件变化后从第一页查询。
 */
function search() { page.value = 1; void load() }

/**
 * 明确复制可编辑字段，提交时不会携带 Entity 管理字段。
 */
async function open(row: Video) {
  if (await act(async () => {
    detail.value = await videoApi.detail(row.id)
    const v = detail.value.video
    form.value = { name: v.name, categoryId: v.categoryId, year: v.year, area: v.area, language: v.language,
      director: v.director, actors: v.actors, posterUrl: v.posterUrl, description: v.description, remarks: v.remarks,
      declaredTotal: v.declaredTotal, ended: v.ended, metadataLocked: true }
    primaryId.value = v.primaryOriginId ?? null
  })) show.value = true
}

/**
 * 保存手工编辑，默认资料锁定，不修改播放地址。
 */
async function save() {
  if (!detail.value || busy.value) return
  const id = detail.value.video.id
  if (await act(() => videoApi.editVideo(id, form.value), '资料已保存')) { show.value = false; await load() }
}

/**
 * 更换主要来源，必须选择该影片已有来源。
 */
async function savePrimary() {
  if (!detail.value || !primaryId.value || busy.value) return
  await act(() => videoApi.primary(detail.value!.video.id, primaryId.value!), '主要来源已更新')
}

/**
 * 保存线路的名称、顺序及启停，不修改分集地址。
 */
async function saveLine(line: PlayLine) {
  if (busy.value) return
  await act(() => videoApi.editLine(line.id, { name: line.name, sortNo: line.sortNo, enabled: line.enabled }), '线路已保存')
}

/**
 * 管理员主动调整上下架状态。
 */
async function publish(row: Video) {
  if (await act(() => videoApi.publishVideo(row.id, !row.published), row.published ? '影片已下架' : '影片已上架')) await load()
}

/**
 * 逻辑删除并保留来源关联，重采不会恢复。
 */
async function remove(row: Video) {
  if (await act(() => videoApi.deleteVideo(row.id), '影片已删除')) await load()
}

const columns: DataTableColumns<Video> = [
  { title: '影片', key: 'name', minWidth: 200 },
  { title: '分类', key: 'categoryId', width: 130, render: row => options.value.find(o => o.value === row.categoryId)?.label ?? '—' },
  { title: '年份', key: 'year', width: 80 },
  { title: '更新说明', key: 'remarks', minWidth: 150, ellipsis: { tooltip: true } },
  { title: '状态', key: 'published', width: 90, render: row => h(NTag, { type: row.published ? 'success' : 'default', bordered: false }, () => row.published ? '上架' : '下架') },
  { title: '操作', key: 'actions', width: 240, render: row => h(NSpace, {}, () => [
    h(NButton, { size: 'small', onClick: () => open(row) }, () => '详情'),
    session.hasPermission('video:video:edit') ? h(NButton, { size: 'small', onClick: () => publish(row) }, () => row.published ? '下架' : '上架') : null,
    session.hasPermission('video:video:delete') ? h(NPopconfirm, { onPositiveClick: () => remove(row) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '删除'), default: () => '确认删除？重采不会自动恢复。' }) : null,
  ]) },
]
const episodeColumns: DataTableColumns<Episode> = [
  { title: '分集', key: 'name', width: 120 }, { title: '类型', key: 'urlType', width: 90 },
  { title: '完整播放地址', key: 'url', ellipsis: { tooltip: true } },
]
onMounted(async () => {
  await act(async () => { categories.value = await videoApi.categories() })
  await load()
})
</script>
<template>
  <PageBody>
    <NCard
      class="video-panel"
      :bordered="false"
    >
      <PageHeader
        title="视频管理"
        description="影片资料、上下架、主要来源和播放线路"
        icon="file"
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
        </template>
      </PageHeader>
      <div class="video-content">
        <NSpace class="video-filters">
          <NInput
            v-model:value="filters.keyword"
            placeholder="影片名称"
            clearable
            @keyup.enter="search"
          />
          <NSelect
            v-model:value="filters.categoryId"
            :options="options"
            placeholder="分类"
            clearable
            filterable
            style="width: 180px"
          />
          <NInputNumber
            v-model:value="filters.year"
            placeholder="年份"
            :min="1"
            :max="9999"
            style="width: 120px"
          />
          <NInput
            v-model:value="filters.area"
            placeholder="地区"
            style="width: 120px"
          />
          <NInput
            v-model:value="filters.language"
            placeholder="语言"
            style="width: 120px"
          />
          <NSelect
            v-model:value="filters.published"
            :options="[{ label: '已上架', value: 'on' }, { label: '已下架', value: 'off' }]"
            placeholder="状态"
            clearable
            style="width: 120px"
          />
          <NButton
            :loading="busy"
            @click="search"
          >
            查询
          </NButton>
        </NSpace>
        <NDataTable
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :scroll-x="900"
          remote
          :pagination="{ page, pageSize, itemCount: total, showSizePicker: true, pageSizes: videoPageSizeOptions }"
          @update:page="page = $event; load()"
          @update:page-size="pageSize = $event; search()"
        />
      </div>
    </NCard>
    <NModal
      v-model:show="show"
      preset="card"
      title="影片详情与播放线路"
      class="video-modal"
    >
      <NForm
        :model="form"
        label-placement="top"
        :disabled="!session.hasPermission('video:video:edit') || busy"
      >
        <div class="video-form-grid">
          <NFormItem
            label="名称"
            :feedback="errors.name"
            :validation-status="errors.name ? 'error' : undefined"
          >
            <NInput v-model:value="form.name" />
          </NFormItem>
          <NFormItem label="本地分类">
            <NSelect
              v-model:value="form.categoryId"
              :options="options"
              filterable
            />
          </NFormItem>
          <NFormItem label="年份">
            <NInputNumber
              :value="form.year"
              :min="1"
              :max="9999"
              @update:value="form.year = $event ?? undefined"
            />
          </NFormItem>
          <NFormItem label="地区">
            <NInput v-model:value="form.area" />
          </NFormItem>
          <NFormItem label="语言">
            <NInput v-model:value="form.language" />
          </NFormItem>
          <NFormItem label="导演">
            <NInput v-model:value="form.director" />
          </NFormItem>
          <NFormItem label="演员">
            <NInput v-model:value="form.actors" />
          </NFormItem>
          <NFormItem label="封面地址">
            <NInput v-model:value="form.posterUrl" />
          </NFormItem>
          <NFormItem label="更新说明">
            <NInput v-model:value="form.remarks" />
          </NFormItem>
          <NFormItem label="源站声明的总集数">
            <NInputNumber
              :value="form.declaredTotal"
              :min="0"
              @update:value="form.declaredTotal = $event ?? undefined"
            />
          </NFormItem>
          <NFormItem label="源站完结标志">
            <NSelect
              :value="form.ended == null ? 'UNKNOWN' : form.ended ? 'YES' : 'NO'"
              :options="[{ label: '未知', value: 'UNKNOWN' }, { label: '完结', value: 'YES' }, { label: '未完结', value: 'NO' }]"
              @update:value="form.ended = $event === 'UNKNOWN' ? undefined : $event === 'YES'"
            />
          </NFormItem>
          <NFormItem label="锁定资料（播放线路继续更新）">
            <NSwitch v-model:value="form.metadataLocked" />
          </NFormItem>
        </div>
        <NFormItem label="简介">
          <NInput
            v-model:value="form.description"
            type="textarea"
            :rows="3"
          />
        </NFormItem>
      </NForm>
      <NSpace class="video-filters">
        <NSelect
          v-model:value="primaryId"
          :options="detail?.origins.map(o => ({ label: o.sourceName + ' · ' + o.upstreamId, value: o.id })) ?? []"
          placeholder="主要来源"
          style="width: 280px"
        />
        <NButton
          v-if="session.hasPermission('video:video:edit')"
          :loading="busy"
          @click="savePrimary"
        >
          保存主要来源
        </NButton>
      </NSpace>
      <NCollapse>
        <NCollapseItem
          v-for="line in detail?.playLines ?? []"
          :key="line.id"
          :name="line.id"
          :title="line.sourceName + ' · ' + line.name + ' · ' + line.availableEpisodeCount + ' 集'"
        >
          <NSpace
            class="video-filters"
            align="center"
          >
            <NInput
              v-model:value="line.name"
              placeholder="显示名称"
              :disabled="!session.hasPermission('video:video:edit')"
              style="width: 160px"
            />
            <NInputNumber
              v-model:value="line.sortNo"
              placeholder="排序"
              :min="0"
              :disabled="!session.hasPermission('video:video:edit')"
              style="width: 120px"
            />
            <NSwitch
              v-model:value="line.enabled"
              :disabled="!session.hasPermission('video:video:edit')"
            />
            <NButton
              v-if="session.hasPermission('video:video:edit')"
              :loading="busy"
              @click="saveLine(line)"
            >
              保存线路
            </NButton>
          </NSpace>
          <NDataTable
            :columns="episodeColumns"
            :data="line.episodes"
            :row-key="episode => episode.id"
            :scroll-x="650"
            :max-height="300"
          />
        </NCollapseItem>
      </NCollapse>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="show = false">
            关闭
          </NButton><NButton
            v-if="session.hasPermission('video:video:edit')"
            type="primary"
            :loading="busy"
            @click="save"
          >
            保存资料
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
