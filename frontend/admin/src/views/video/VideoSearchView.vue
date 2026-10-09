<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NAlert, NButton, NCard, NDataTable, NEmpty, NInput, NPagination, NPopconfirm, NSelect, NSpace, NTag, NTooltip, type DataTableColumns } from 'naive-ui'
import { videoApi, type BatchResult, type SearchItem, type Source, type SourceSearch } from '@/api/video'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import './video.css'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()
const { busy, act } = useVideoActions()
const sources = ref<Source[]>([])
const sourceId = ref<string>(typeof route.query.sourceId === 'string' ? route.query.sourceId : '')
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const results = ref<SourceSearch[]>([])
const searched = ref('')
const page = ref(1)
const selected = ref<string[]>([])
const collectResult = ref<BatchResult>()
const canCollect = computed(() => session.hasPermission('video:search:collect'))
const sourceOptions = computed(() => [
  { label: '全部启用的资源站', value: '' },
  ...sources.value.filter(s => s.enabled).map(s => ({ label: s.name, value: s.id })),
])
const hitCount = computed(() => results.value.reduce((sum, r) => sum + r.items.length, 0))
const selectable = computed(() => results.value.flatMap(r => r.items.filter(i => i.upstreamId).map(i => key(r.sourceId, i.upstreamId))))
const statusTags: Record<string, { label: string; type: 'success' | 'warning' | 'default' }> = {
  COLLECTED: { label: '本站已采集', type: 'success' },
  SAME_NAME: { label: '库里有同名', type: 'warning' },
  NONE: { label: '未入库', type: 'default' },
}

/**
 * 勾选键由资源站与源站影片编号组成，同一片名在不同站分别勾选。
 */
function key(source: string, upstreamId: string) {
  return `${source}:${upstreamId}`
}

/**
 * 读取资源站供选择；没有资源站查看权限时只保留“全部启用的资源站”。
 */
async function loadSources() {
  try {
    sources.value = await videoApi.sources()
  } catch {
    sources.value = []
  }
}

/**
 * 全站模式并发搜索每个启用资源站的第一页，单站模式按页搜索；换关键词或资源站时清空勾选。
 */
async function search(nextPage = 1) {
  const word = keyword.value.trim()
  if (!word || busy.value) return
  if (word !== searched.value || nextPage === 1) selected.value = []
  collectResult.value = undefined
  const ok = await act(async () => {
    results.value = sourceId.value
      ? [await videoApi.searchSource(sourceId.value, word, nextPage)]
      : await videoApi.searchAll(word)
  })
  if (ok) {
    searched.value = word
    page.value = nextPage
    router.replace({ query: { ...(sourceId.value ? { sourceId: sourceId.value } : {}), keyword: word } })
  }
}

/**
 * 从全站结果切换到某个资源站，查看该站的更多结果。
 */
function openSource(id: string) {
  sourceId.value = id
  search(1)
}

/**
 * 更新某个资源站分组内的勾选，保留其他分组的勾选。
 */
function updateGroup(group: SourceSearch, keys: (string | number)[]) {
  const prefix = `${group.sourceId}:`
  selected.value = [...selected.value.filter(k => !k.startsWith(prefix)), ...keys.map(String)]
}

/**
 * 按资源站分组提交采集，入库规则与普通采集一致；返回每站是否已排队。
 */
async function collect(keys: string[]) {
  if (!keys.length || busy.value) return
  const items = keys.map(k => {
    const index = k.indexOf(':')
    return { sourceId: k.slice(0, index), upstreamId: k.slice(index + 1) }
  })
  await act(async () => {
    collectResult.value = await videoApi.collectSearch(items)
    if (collectResult.value.succeeded) selected.value = []
  })
}

/**
 * 某个资源站分组的表格列；缺少源站编号的条目无法采集，不可勾选。
 */
function columns(): DataTableColumns<SearchItem> {
  return [
    ...(canCollect.value ? [{ type: 'selection' as const, disabled: (row: SearchItem) => !row.upstreamId }] : []),
    { title: '片名', key: 'name', minWidth: 180, render: row => h(NSpace, { size: 6, align: 'center' }, () => [
      row.name || '名称缺失',
      row.problems?.length ? h(NTooltip, {}, { trigger: () => h(NTag, { size: 'small', type: 'error', bordered: false }, () => '数据异常'), default: () => `${row.problems.join('；')}。采集时会进入待确认。` }) : null,
    ]) },
    { title: '年份', key: 'year', width: 70, render: row => row.year ?? '—' },
    { title: '类型', key: 'typeName', width: 100, render: row => row.typeName ?? '—' },
    { title: '集数', key: 'episodeCount', width: 110, render: row => row.lineCount ? `${row.episodeCount} 集 · ${row.lineCount} 条线路` : '无播放地址' },
    { title: '更新说明', key: 'remarks', width: 120, ellipsis: { tooltip: true }, render: row => row.remarks ?? '—' },
    { title: '源站更新时间', key: 'sourceUpdatedAt', width: 170, render: row => row.sourceUpdatedAt ? new Date(row.sourceUpdatedAt).toLocaleString() : '—' },
    { title: '本库', key: 'libraryStatus', width: 200, render: row => {
      const tag = statusTags[row.libraryStatus] ?? statusTags.NONE
      return h(NSpace, { size: 6, align: 'center' }, () => [
        h(NTag, { size: 'small', type: tag.type, bordered: false }, () => tag.label),
        row.videoName ? h('span', { class: 'video-muted' }, row.videoName) : null,
      ])
    } },
  ]
}

onMounted(async () => {
  await loadSources()
  if (keyword.value.trim()) await search(1)
})
</script>

<template>
  <PageBody>
    <NCard
      class="video-panel"
      :bordered="false"
    >
      <PageHeader
        title="资源搜索"
        description="按片名搜索资源站，勾选结果后采集入库；入库规则与普通采集相同"
        icon="search"
      />
      <div class="video-content">
        <div class="video-search-bar video-filters">
          <NSelect
            v-model:value="sourceId"
            :options="sourceOptions"
            class="video-search-source"
          />
          <NInput
            v-model:value="keyword"
            placeholder="输入片名关键词，例如：交锋"
            clearable
            maxlength="50"
            @keyup.enter="search(1)"
          />
          <NButton
            type="primary"
            :loading="busy"
            :disabled="!keyword.trim()"
            @click="search(1)"
          >
            搜索
          </NButton>
        </div>
        <NAlert
          v-if="collectResult"
          :type="collectResult.failures.length ? 'warning' : 'success'"
          :bordered="false"
          closable
          class="video-filters"
          @close="collectResult = undefined"
        >
          已为 {{ collectResult.succeeded }} 个资源站排队采集<span v-if="collectResult.failures.length">，{{ collectResult.failures.length }} 个未提交：{{ collectResult.failures.map(f => f.message).join('；') }}</span>。采集在后台执行，新片、更新与待确认数量见采集记录。
          <NButton
            text
            type="primary"
            @click="router.push({ name: 'video-run' })"
          >
            查看采集记录
          </NButton>
        </NAlert>
        <div
          v-if="searched && canCollect && selectable.length"
          class="video-search-actions video-filters"
        >
          <span class="video-muted">「{{ searched }}」共列出 {{ hitCount }} 条，已勾选 {{ selected.length }} 条</span>
          <NSpace>
            <NButton
              type="primary"
              :disabled="!selected.length || busy"
              @click="collect(selected)"
            >
              采集勾选
            </NButton>
            <NPopconfirm @positive-click="collect(selectable)">
              <template #trigger>
                <NButton
                  secondary
                  type="primary"
                  :disabled="busy"
                >
                  本页全部采集
                </NButton>
              </template>
              确认采集当前列出的 {{ selectable.length }} 条？已采集过的会按采集设置更新。
            </NPopconfirm>
          </NSpace>
        </div>
        <NEmpty
          v-if="searched && !results.length"
          description="没有启用的资源站"
        />
        <section
          v-for="group in results"
          :key="group.sourceId"
          class="video-search-group"
        >
          <header class="video-search-group__head">
            <strong>{{ group.sourceName }}</strong>
            <span
              v-if="!group.error"
              class="video-muted"
            >共 {{ group.total }} 条<template v-if="group.pageCount > 1">，第 {{ group.page }} / {{ group.pageCount }} 页</template></span>
            <NButton
              v-if="!sourceId && group.pageCount > 1"
              text
              type="primary"
              @click="openSource(group.sourceId)"
            >
              查看该站全部结果
            </NButton>
          </header>
          <NAlert
            v-if="group.error"
            type="error"
            :bordered="false"
          >
            {{ group.error }}
          </NAlert>
          <NDataTable
            v-else-if="group.items.length"
            :columns="columns()"
            :data="group.items"
            :row-key="row => key(group.sourceId, row.upstreamId ?? '')"
            :checked-row-keys="selected.filter(k => k.startsWith(group.sourceId + ':'))"
            :scroll-x="980"
            size="small"
            @update:checked-row-keys="keys => updateGroup(group, keys)"
          />
          <p
            v-else
            class="video-muted"
          >
            该资源站没有搜到结果
          </p>
          <NPagination
            v-if="sourceId && group.pageCount > 1"
            :page="page"
            :page-count="group.pageCount"
            class="video-search-pages"
            @update:page="search"
          />
        </section>
      </div>
    </NCard>
  </PageBody>
</template>

<style scoped>
.video-search-bar { display: grid; grid-template-columns: 220px minmax(0, 1fr) auto; gap: 12px; }
.video-search-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.video-search-group { margin-bottom: 24px; }
.video-search-group__head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.video-search-pages { margin-top: 12px; justify-content: flex-end; }
@media(max-width:768px) { .video-search-bar { grid-template-columns: 1fr; } }
</style>
