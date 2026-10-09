<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NDatePicker, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NSelect, NSpace, NTag, type DataTableColumns } from 'naive-ui'
import { activationStatusLabels, formatTime, memberApi, type MemberActivationCode } from '@/api/member'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { copyText } from '@/utils/clipboard'
import { videoPageSize, videoPageSizeOptions } from '@/views/video/video-options'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, errors, act, message } = useVideoActions()
const rows = ref<MemberActivationCode[]>([])
const filters = reactive<{ keyword: string; status: string | null; remark: string }>({ keyword: '', status: null, remark: '' })
const pagination = reactive({ page: 1, pageSize: videoPageSize, itemCount: 0, showSizePicker: true, pageSizes: videoPageSizeOptions })
const statusOptions = Object.entries(activationStatusLabels).map(([value, label]) => ({ label, value }))
const statusTags: Record<string, 'success' | 'info' | 'warning' | 'default'> = { UNUSED: 'success', USED: 'info', EXPIRED: 'warning', REVOKED: 'default' }
const createShow = ref(false)
const createForm = reactive<{ count: number | null; expireAt: number | null; remark: string }>({ count: 10, expireAt: null, remark: '' })
const created = ref<MemberActivationCode[]>([])

/**
 * 按筛选条件读取当前页。
 */
async function load() {
  await act(async () => {
    const page = await memberApi.activationCodes({ pageNum: pagination.page, pageSize: pagination.pageSize, keyword: filters.keyword || undefined, status: filters.status ?? undefined, remark: filters.remark || undefined })
    rows.value = page.records
    pagination.itemCount = page.total
  })
}

/**
 * 筛选条件变化后回到第一页。
 */
function search() {
  pagination.page = 1
  void load()
}

/**
 * 打开生成对话框并清空上一次结果。
 */
function openCreate() {
  createForm.count = 10
  createForm.expireAt = null
  createForm.remark = ''
  created.value = []
  createShow.value = true
}

/**
 * 批量生成激活码，成功后在对话框内展示新码以便复制。
 */
async function create() {
  let result: MemberActivationCode[] = []
  const ok = await act(async () => {
    result = await memberApi.createActivationCodes({ count: createForm.count ?? 0, expireAt: createForm.expireAt ? new Date(createForm.expireAt).toISOString() : undefined, remark: createForm.remark || undefined })
  }, '激活码已生成')
  if (ok) {
    created.value = result
    search()
  }
}

/**
 * 复制激活码文本，多个码每行一个。
 */
async function copy(codes: string[]) {
  if (await copyText(codes.join('\n'))) message.success(codes.length > 1 ? `已复制 ${codes.length} 个激活码` : '已复制')
  else message.error('复制失败，请手动选中复制')
}

/**
 * 作废未使用的激活码。
 */
async function revoke(row: MemberActivationCode) {
  if (await act(() => memberApi.revokeActivationCode(row.id), '激活码已作废')) await load()
}

const columns: DataTableColumns<MemberActivationCode> = [
  { title: '激活码', key: 'code', width: 190, render: row => h('span', { class: 'activation-code' }, row.code) },
  { title: '状态', key: 'status', width: 90, render: row => h(NTag, { type: statusTags[row.status] ?? 'default', bordered: false, size: 'small' }, () => activationStatusLabels[row.status] ?? row.status) },
  { title: '使用账号', key: 'usedUsername', minWidth: 140, render: row => row.usedUsername || '—' },
  { title: '使用时间', key: 'usedAt', width: 170, render: row => formatTime(row.usedAt) },
  { title: '有效期至', key: 'expireAt', width: 170, render: row => row.expireAt ? formatTime(row.expireAt) : '长期有效' },
  { title: '备注', key: 'remark', minWidth: 160, ellipsis: { tooltip: true }, render: row => row.remark || '—' },
  { title: '生成时间', key: 'createdAt', width: 170, render: row => formatTime(row.createdAt) },
  { title: '操作', key: 'actions', width: 130, fixed: 'right', render: row => h(NSpace, { size: 4, wrap: false }, () => [
    h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => copy([row.code]) }, () => '复制'),
    (row.status === 'UNUSED' || row.status === 'EXPIRED') && session.hasPermission('member:activation:revoke')
      ? h(NPopconfirm, { onPositiveClick: () => revoke(row) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '作废'), default: () => `确认作废激活码「${row.code}」？作废后不能再用于注册` })
      : null,
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
        title="激活码"
        description="开放注册时，新用户必须填写一个激活码才能注册；每个码只能注册一次，与 VIP 无关"
        icon="key"
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
            v-if="session.hasPermission('member:activation:create')"
            type="primary"
            @click="openCreate"
          >
            生成激活码
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NSpace class="video-filters">
          <NInput
            v-model:value="filters.keyword"
            placeholder="激活码或使用账号"
            clearable
            style="width: 220px"
            @keyup.enter="search"
            @clear="search"
          />
          <NInput
            v-model:value="filters.remark"
            placeholder="备注"
            clearable
            style="width: 180px"
            @keyup.enter="search"
            @clear="search"
          />
          <NSelect
            v-model:value="filters.status"
            :options="statusOptions"
            placeholder="状态"
            clearable
            style="width: 130px"
            @update:value="search"
          />
          <NButton
            type="primary"
            @click="search"
          >
            查询
          </NButton>
        </NSpace>
        <NDataTable
          remote
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :pagination="pagination"
          :scroll-x="1240"
          @update:page="page => { pagination.page = page; load() }"
          @update:page-size="size => { pagination.pageSize = size; pagination.page = 1; load() }"
        />
      </div>
    </NCard>
    <NModal
      v-model:show="createShow"
      preset="card"
      title="生成激活码"
      class="video-modal"
      style="width: min(520px, calc(100vw - 32px))"
    >
      <NForm
        v-if="created.length === 0"
        label-placement="top"
      >
        <NFormItem
          label="数量"
          :feedback="errors.count"
          :validation-status="errors.count ? 'error' : undefined"
        >
          <NInputNumber
            v-model:value="createForm.count"
            :min="1"
            :max="500"
          />
        </NFormItem>
        <NFormItem label="有效期至（清空表示长期有效）">
          <NDatePicker
            v-model:value="createForm.expireAt"
            type="datetime"
            clearable
          />
        </NFormItem>
        <NFormItem label="备注">
          <NInput
            v-model:value="createForm.remark"
            maxlength="255"
            placeholder="可选，例如发给谁、哪一批"
          />
        </NFormItem>
      </NForm>
      <template v-else>
        <NAlert
          type="success"
          :bordered="false"
        >
          已生成 {{ created.length }} 个激活码，可以复制后发给用户。之后也能在列表里按备注查到。
        </NAlert>
        <pre class="activation-created">{{ created.map(item => item.code).join('\n') }}</pre>
      </template>
      <template #footer>
        <NSpace justify="end">
          <template v-if="created.length === 0">
            <NButton @click="createShow = false">
              取消
            </NButton>
            <NButton
              type="primary"
              :loading="busy"
              :disabled="!createForm.count"
              @click="create"
            >
              生成
            </NButton>
          </template>
          <template v-else>
            <NButton @click="createShow = false">
              关闭
            </NButton>
            <NButton
              type="primary"
              @click="copy(created.map(item => item.code))"
            >
              全部复制
            </NButton>
          </template>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>

<style scoped>
.activation-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  letter-spacing: 0.04em;
}

.activation-created {
  max-height: 300px;
  margin: 12px 0 0;
  padding: 12px;
  overflow: auto;
  border-radius: 6px;
  background: var(--n-color-embedded, rgba(0, 0, 0, 0.04));
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  line-height: 1.7;
}
</style>
