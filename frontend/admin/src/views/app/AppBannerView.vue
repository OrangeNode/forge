<script setup lang="ts">
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NCard, NDataTable, NDatePicker, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NRadioButton, NRadioGroup, NSelect, NSpace, NSwitch, NTag, type DataTableColumns } from 'naive-ui'
import { appConfigApi, imageSrc, windowText, type Banner } from '@/api/app-config'
import { videoApi, type Category } from '@/api/video'
import { uploadFile } from '@/api/files'
import { fetchBinary } from '@/utils/download'
import { defaultTagline, renderBanner } from './banner-template'
import ImageFilePicker from '@/components/ImageFilePicker.vue'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { categoryOptions } from '@/views/video/video-options'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const generating = ref(false)
const tagline = ref('')
const pickerKey = ref(0)
const rows = ref<Banner[]>([])
const categories = ref<Category[]>([])
const categorySelect = computed(() => categoryOptions(categories.value))
const videoSelect = ref<{ label: string; value: string }[]>([])
const show = ref(false)
const editingId = ref<string>()
const preview = ref('')
const tvPreview = ref('')
const tvPickerKey = ref(0)
const form = reactive({ title: '', imageFileId: '', tvImageFileId: '', targetType: 'NONE', targetId: null as string | null, targetUrl: null as string | null, startAt: null as number | null, endAt: null as number | null, enabled: true, sortNo: 0 })
const targetLabels: Record<string, string> = { NONE: '不跳转', VIDEO: '影片', CATEGORY: '分类', URL: '链接' }

/**
 * 读取轮播与可选分类。
 */
async function load() {
  await act(async () => {
    rows.value = await appConfigApi.banners()
    if (session.hasPermission('video:category:view') || session.hasPermission('video:video:view')) categories.value = await videoApi.categories()
  })
}

/**
 * 按片名搜索影片作为跳转目标。
 */
async function searchVideos(keyword: string) {
  if (!keyword) return
  await act(async () => {
    const page = await videoApi.videos({ pageNum: 1, pageSize: 20, keyword })
    videoSelect.value = page.records.map(video => ({ label: `${video.name}${video.year ? `（${video.year}）` : ''}`, value: video.id }))
  })
}

/**
 * 打开新增或编辑表单。
 */
function edit(row?: Banner) {
  editingId.value = row?.id
  tagline.value = ''
  preview.value = imageSrc(row?.imageUrl)
  tvPreview.value = imageSrc(row?.tvImageUrl)
  tvPickerKey.value++
  videoSelect.value = row?.targetType === 'VIDEO' && row.targetId ? [{ label: row.targetName ?? row.targetId, value: row.targetId }] : []
  Object.assign(form, {
    title: row?.title ?? '', imageFileId: row?.imageFileId ?? '', tvImageFileId: row?.tvImageFileId ?? '', targetType: row?.targetType ?? 'NONE',
    targetId: row?.targetId ?? null, targetUrl: row?.targetUrl ?? null, startAt: row?.startAt ? new Date(row.startAt).getTime() : null,
    endAt: row?.endAt ? new Date(row.endAt).getTime() : null, enabled: row?.enabled ?? true, sortNo: row?.sortNo ?? 0,
  })
  show.value = true
}

/**
 * 按模板为所选影片生成轮播图：后端代取海报，浏览器画布绘制后上传到文件管理并回填图片。
 * 标题为空时同时填入片名。
 */
async function generateImage() {
  const videoId = form.targetId
  if (!videoId || generating.value) return
  generating.value = true
  await act(async () => {
    const { video } = await videoApi.detail(videoId)
    const poster = await (await fetchBinary(`/video/videos/${videoId}/poster`)).blob()
    const categoryName = categorySelect.value.find(option => option.value === video.categoryId)?.label.split(' / ').pop()
    const blob = await renderBanner({
      poster,
      title: video.name,
      tagline: tagline.value.trim() || defaultTagline(video.description),
      meta: [categoryName ?? video.area, video.year].filter(Boolean).join(' · '),
    })
    const result = await uploadFile(new File([blob], `banner-${video.id}.jpg`, { type: 'image/jpeg' }))
    form.imageFileId = result.id ?? ''
    preview.value = URL.createObjectURL(blob)
    pickerKey.value++
    if (!form.title) form.title = video.name
  }, '已按模板生成图片')
  generating.value = false
}

/**
 * 保存轮播，跳转目标由后端校验存在性。
 */
async function save() {
  if (busy.value) return
  const ok = await act(() => appConfigApi.saveBanner(editingId.value, {
    ...form,
    targetId: form.targetType === 'NONE' || form.targetType === 'URL' ? undefined : form.targetId ?? undefined,
    targetUrl: form.targetType === 'URL' ? form.targetUrl?.trim() || undefined : undefined,
    tvImageFileId: form.tvImageFileId || undefined,
    startAt: form.startAt ? new Date(form.startAt).toISOString() : undefined,
    endAt: form.endAt ? new Date(form.endAt).toISOString() : undefined,
  }), '轮播已保存')
  if (ok) { show.value = false; await load() }
}

/**
 * 逻辑删除轮播。
 */
async function remove(id: string) {
  if (await act(() => appConfigApi.deleteBanner(id), '轮播已删除')) await load()
}

const columns: DataTableColumns<Banner> = [
  { title: '手机图片', key: 'imageUrl', width: 150, render: row => h('img', { src: imageSrc(row.imageUrl), alt: row.title, style: 'width:128px;height:54px;object-fit:cover;border-radius:6px' }) },
  { title: '电视图片', key: 'tvImageUrl', width: 150, render: row => row.tvImageUrl ? h('img', { src: imageSrc(row.tvImageUrl), alt: row.title, style: 'width:128px;height:58px;object-fit:cover;border-radius:6px' }) : h('span', { class: 'video-muted' }, '同手机图片') },
  { title: '标题', key: 'title', minWidth: 140 },
  { title: '跳转', key: 'targetType', minWidth: 160, ellipsis: { tooltip: true }, render: row => row.targetType === 'NONE' ? '不跳转' : row.targetType === 'URL' ? `链接：${row.targetUrl}` : `${targetLabels[row.targetType]}：${row.targetName ?? '目标已删除'}` },
  { title: '上下线时间', key: 'startAt', minWidth: 260, render: row => windowText(row.startAt, row.endAt) },
  { title: '顺序', key: 'sortNo', width: 70 },
  { title: '状态', key: 'active', width: 100, render: row => h(NTag, { type: row.active ? 'success' : 'default', bordered: false }, () => row.active ? '在线' : (row.enabled ? '不在时段' : '停用')) },
  { title: '操作', key: 'actions', width: 150, render: row => h(NSpace, {}, () => [
    session.hasPermission('app:banner:edit') ? h(NButton, { size: 'small', onClick: () => edit(row) }, () => '编辑') : null,
    session.hasPermission('app:banner:delete') ? h(NPopconfirm, { onPositiveClick: () => remove(row.id) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '删除'), default: () => '确认删除该轮播？' }) : null,
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
        title="首页轮播"
        description="App 首页顶部轮播（手机与电视）：可跳转影片、分类，或作为广告跳转外部链接"
        icon="dashboard"
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
            v-if="session.hasPermission('app:banner:edit')"
            type="primary"
            @click="edit()"
          >
            新增轮播
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NDataTable
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :scroll-x="1120"
        />
      </div>
    </NCard>
    <NModal
      v-model:show="show"
      preset="card"
      :title="editingId ? '编辑轮播' : '新增轮播'"
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
            />
          </NFormItem>
          <NFormItem label="顺序">
            <NInputNumber
              v-model:value="form.sortNo"
              :min="0"
            />
          </NFormItem>
        </div>
        <div class="video-form-grid">
          <NFormItem label="手机图片（建议 1280×600）">
            <ImageFilePicker
              :key="pickerKey"
              v-model="form.imageFileId"
              :preview-src="preview"
            />
          </NFormItem>
          <NFormItem label="电视图片（建议 1200×540，可不传，不传时电视用手机图片）">
            <NSpace
              vertical
              :size="8"
            >
              <ImageFilePicker
                :key="tvPickerKey"
                v-model="form.tvImageFileId"
                :preview-src="tvPreview"
              />
              <NButton
                v-if="form.tvImageFileId"
                size="small"
                quaternary
                @click="form.tvImageFileId = ''; tvPreview = ''; tvPickerKey++"
              >
                不用电视图片
              </NButton>
            </NSpace>
          </NFormItem>
        </div>
        <NFormItem label="跳转目标">
          <NRadioGroup
            v-model:value="form.targetType"
            @update:value="form.targetId = null; form.targetUrl = null"
          >
            <NRadioButton value="NONE">
              不跳转
            </NRadioButton>
            <NRadioButton value="VIDEO">
              影片
            </NRadioButton>
            <NRadioButton value="CATEGORY">
              分类
            </NRadioButton>
            <NRadioButton value="URL">
              外部链接（广告）
            </NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem
          v-if="form.targetType === 'VIDEO'"
          label="影片（输入片名搜索）"
        >
          <NSelect
            v-model:value="form.targetId"
            filterable
            remote
            clearable
            :options="videoSelect"
            placeholder="输入片名搜索"
            @search="searchVideos"
          />
        </NFormItem>
        <NFormItem
          v-if="form.targetType === 'VIDEO' && form.targetId"
          label="按模板生成图片（用影片海报与信息，生成后自动填入上方图片）"
        >
          <NSpace
            align="center"
            :wrap="false"
            style="width: 100%"
          >
            <NInput
              v-model:value="tagline"
              maxlength="30"
              placeholder="宣传语，留空则取简介第一句"
              style="min-width: 260px"
            />
            <NButton
              type="primary"
              secondary
              :loading="generating"
              @click="generateImage"
            >
              {{ form.imageFileId ? '重新生成' : '生成图片' }}
            </NButton>
          </NSpace>
        </NFormItem>
        <NFormItem
          v-if="form.targetType === 'URL'"
          label="外部链接（http:// 或 https:// 开头，在浏览器打开）"
          :feedback="errors.targetUrl"
          :validation-status="errors.targetUrl ? 'error' : undefined"
        >
          <NInput
            v-model:value="form.targetUrl"
            maxlength="512"
            placeholder="https://"
          />
        </NFormItem>
        <NFormItem
          v-if="form.targetType === 'CATEGORY'"
          label="分类"
        >
          <NSelect
            v-model:value="form.targetId"
            filterable
            clearable
            :options="categorySelect"
          />
        </NFormItem>
        <div class="video-form-grid">
          <NFormItem label="上线时间（空表示立即）">
            <NDatePicker
              v-model:value="form.startAt"
              type="datetime"
              clearable
            />
          </NFormItem>
          <NFormItem label="下线时间（空表示长期）">
            <NDatePicker
              v-model:value="form.endAt"
              type="datetime"
              clearable
            />
          </NFormItem>
        </div>
        <NFormItem label="启用">
          <NSwitch v-model:value="form.enabled" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="show = false">
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="busy"
            :disabled="!form.imageFileId || !form.title || (form.targetType === 'URL' ? !form.targetUrl : form.targetType !== 'NONE' && !form.targetId)"
            @click="save"
          >
            保存
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
