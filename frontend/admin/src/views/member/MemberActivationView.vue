<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDataTable, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NRadioButton, NRadioGroup, NSelect, NSpace, NTag, type DataTableColumns } from 'naive-ui'
import { activationDurationOptions, activationStatusLabels, activationUsageLabels, formatDuration, formatTime, memberApi, type MemberActivationCode } from '@/api/member'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { copyText } from '@/utils/clipboard'
import { saveBlob } from '@/utils/download'
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
const createForm = reactive<{ count: number | null; preset: number | 'custom'; customDays: number | null; remark: string }>({ count: 10, preset: 30, customDays: 30, remark: '' })
const created = ref<MemberActivationCode[]>([])
const checkedKeys = ref<string[]>([])
const selectedRows = new Map<string, MemberActivationCode>()

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
  createForm.preset = 30
  createForm.customDays = 30
  createForm.remark = ''
  created.value = []
  createShow.value = true
}

/**
 * 当前选择的会员天数：预设时长或自定义天数。
 */
function createVipDays(): number | null {
  return createForm.preset === 'custom' ? createForm.customDays : createForm.preset
}

/**
 * 批量生成激活码，成功后在对话框内展示新码以便复制。
 */
async function create() {
  let result: MemberActivationCode[] = []
  const ok = await act(async () => {
    result = await memberApi.createActivationCodes({ count: createForm.count ?? 0, vipDays: createVipDays() ?? 0, remark: createForm.remark || undefined })
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

/**
 * 勾选变化：记住各页勾选的行，跨页勾选后导出所选时仍有完整数据。
 */
function onChecked(keys: Array<string | number>, checkedRows: Array<object | undefined>) {
  checkedKeys.value = keys.map(String)
  for (const row of checkedRows as Array<MemberActivationCode | undefined>) if (row) selectedRows.set(row.id, row)
  for (const id of [...selectedRows.keys()]) if (!checkedKeys.value.includes(id)) selectedRows.delete(id)
}

/**
 * 清空勾选。
 */
function clearChecked() {
  checkedKeys.value = []
  selectedRows.clear()
}

/**
 * 批量作废或删除所选激活码，提示实际处理与跳过的数量。
 */
async function runBatch(kind: 'revoke' | 'delete') {
  const ids = [...checkedKeys.value]
  let summary = ''
  const ok = await act(async () => {
    const result = kind === 'revoke' ? await memberApi.revokeActivationCodes(ids) : await memberApi.deleteActivationCodes(ids)
    const verb = kind === 'revoke' ? '作废' : '删除'
    const reason = kind === 'revoke' ? '（已使用或已作废）' : '（已使用的码保留注册记录）'
    summary = `已${verb} ${result.succeeded} 个${result.skipped ? `，跳过 ${result.skipped} 个${reason}` : ''}`
  })
  if (!ok) return
  message.success(summary)
  clearChecked()
  await load()
}

/**
 * 导出激活码为 CSV（带 BOM，Excel 直接打开不乱码）；不传数据时按当前筛选条件从后端读取全部匹配项。
 */
async function exportCodes(source?: MemberActivationCode[]) {
  let list: MemberActivationCode[] = source ?? []
  const ok = await act(async () => {
    if (!source) list = await memberApi.exportActivationCodes({ keyword: filters.keyword || undefined, status: filters.status ?? undefined, remark: filters.remark || undefined })
  })
  if (!ok) return
  if (list.length === 0) {
    message.warning('没有可导出的激活码')
    return
  }
  const header = ['激活码', '会员时长', '状态', '用途', '使用账号', '使用时间', '备注', '生成时间']
  const lines = list.map(row => [row.code, formatDuration(row.vipDays), activationStatusLabels[row.status] ?? row.status, usageLabel(row.usedFor), row.usedUsername ?? '', row.usedAt ? formatTime(row.usedAt) : '', row.remark ?? '', formatTime(row.createdAt)])
  const csv = [header, ...lines].map(cells => cells.map(csvCell).join(',')).join('\r\n')
  const stamp = new Date().toISOString().slice(0, 10)
  saveBlob(new Blob(['﻿', csv], { type: 'text/csv;charset=utf-8' }), `激活码-${stamp}.csv`)
  message.success(`已导出 ${list.length} 个激活码`)
}

/**
 * 用途显示文字，未使用为空。
 */
function usageLabel(usedFor?: string | null): string {
  return usedFor ? activationUsageLabels[usedFor] ?? usedFor : ''
}

/**
 * CSV 单元格转义；以 = + - @ 开头的文本加前导单引号，避免表格软件当成公式执行。
 */
function csvCell(value: string): string {
  const text = /^[=+\-@]/.test(value) ? `'${value}` : value
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

const columns: DataTableColumns<MemberActivationCode> = [
  { type: 'selection', fixed: 'left' },
  { title: '激活码', key: 'code', width: 190, render: row => h('span', { class: 'activation-code' }, row.code) },
  { title: '状态', key: 'status', width: 90, render: row => h(NTag, { type: statusTags[row.status] ?? 'default', bordered: false, size: 'small' }, () => activationStatusLabels[row.status] ?? row.status) },
  { title: '用途', key: 'usedFor', width: 80, render: row => usageLabel(row.usedFor) || '—' },
  { title: '使用账号', key: 'usedUsername', minWidth: 140, render: row => row.usedUsername || '—' },
  { title: '使用时间', key: 'usedAt', width: 170, render: row => formatTime(row.usedAt) },
  { title: '会员时长', key: 'vipDays', width: 110, render: row => formatDuration(row.vipDays) },
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
        description="开放注册时，新用户必须填写激活码才能注册；每个码只能注册一次，注册成功后从当天起开通该码的会员时长"
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
          <NButton
            secondary
            :loading="busy"
            @click="exportCodes()"
          >
            导出筛选结果
          </NButton>
        </NSpace>
        <NSpace
          v-if="checkedKeys.length > 0"
          class="activation-batch"
          align="center"
        >
          <span>已选 {{ checkedKeys.length }} 个</span>
          <NPopconfirm
            v-if="session.hasPermission('member:activation:revoke')"
            @positive-click="runBatch('revoke')"
          >
            <template #trigger>
              <NButton
                size="small"
                type="warning"
                secondary
              >
                批量作废
              </NButton>
            </template>
            确认作废所选的 {{ checkedKeys.length }} 个激活码？已使用或已作废的会跳过
          </NPopconfirm>
          <NPopconfirm
            v-if="session.hasPermission('member:activation:delete')"
            @positive-click="runBatch('delete')"
          >
            <template #trigger>
              <NButton
                size="small"
                type="error"
                secondary
              >
                批量删除
              </NButton>
            </template>
            确认删除所选的 {{ checkedKeys.length }} 个激活码？删除后无法恢复，已使用的码保留注册记录不会删除
          </NPopconfirm>
          <NButton
            size="small"
            secondary
            @click="exportCodes([...selectedRows.values()])"
          >
            导出所选
          </NButton>
          <NButton
            size="small"
            quaternary
            @click="clearChecked"
          >
            取消选择
          </NButton>
        </NSpace>
        <NDataTable
          remote
          :columns="columns"
          :data="rows"
          :loading="busy"
          :row-key="row => row.id"
          :checked-row-keys="checkedKeys"
          :pagination="pagination"
          :scroll-x="1290"
          @update:checked-row-keys="onChecked"
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
        <NFormItem
          label="会员时长（从用户注册成功时开始计算）"
          :feedback="errors.vipDays"
          :validation-status="errors.vipDays ? 'error' : undefined"
        >
          <NSpace vertical>
            <NRadioGroup v-model:value="createForm.preset">
              <NRadioButton
                v-for="option in activationDurationOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </NRadioButton>
              <NRadioButton value="custom">
                自定义
              </NRadioButton>
            </NRadioGroup>
            <NInputNumber
              v-if="createForm.preset === 'custom'"
              v-model:value="createForm.customDays"
              :min="1"
              :max="3650"
            >
              <template #suffix>
                天
              </template>
            </NInputNumber>
          </NSpace>
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
          已生成 {{ created.length }} 个{{ formatDuration(created[0]?.vipDays) }}激活码，可以复制后发给用户。会员时长从用户注册成功时开始计算，之后也能在列表里按备注查到。
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
              :disabled="!createForm.count || !createVipDays()"
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
.activation-batch {
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.06);
}

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
