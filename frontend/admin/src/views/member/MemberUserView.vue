<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue'
import { NButton, NCard, NDataTable, NDatePicker, NDescriptions, NDescriptionsItem, NDrawer, NDrawerContent, NForm, NFormItem, NInput, NInputNumber, NModal, NPopconfirm, NRadioButton, NRadioGroup, NSelect, NSpace, NTabPane, NTabs, NTag, type DataTableColumns } from 'naive-ui'
import { formatTime, memberApi, memberStatusLabels, offlineReasonLabels, vipChangeLabels, type MemberDevice, type MemberUser, type MemberVipLog } from '@/api/member'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { videoPageSize, videoPageSizeOptions } from '@/views/video/video-options'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const rows = ref<MemberUser[]>([])
const filters = reactive<{ username: string; status: string | null; vip: string | null }>({ username: '', status: null, vip: null })
const pagination = reactive({ page: 1, pageSize: videoPageSize, itemCount: 0, showSizePicker: true, pageSizes: videoPageSizeOptions })
const current = ref<MemberUser>()
const detailShow = ref(false)
const devices = ref<MemberDevice[]>([])
const vipLogs = ref<MemberVipLog[]>([])
const vipShow = ref(false)
const vipForm = reactive<{ mode: 'EXTEND' | 'SET' | 'CANCEL'; days: number; expireAt: number | null; remark: string }>({ mode: 'EXTEND', days: 30, expireAt: null, remark: '' })
const passwordShow = ref(false)
const password = ref('')
const statusOptions = [{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]
const vipOptions = [{ label: 'VIP 有效', value: 'ACTIVE' }, { label: 'VIP 已过期', value: 'EXPIRED' }, { label: '从未开通', value: 'NONE' }]

/**
 * 按筛选条件读取当前页。
 */
async function load() {
  await act(async () => {
    const page = await memberApi.users({ pageNum: pagination.page, pageSize: pagination.pageSize, username: filters.username || undefined, status: filters.status ?? undefined, vip: filters.vip ?? undefined })
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
 * 打开用户详情，同时读取登录设备与 VIP 记录。
 */
async function openDetail(row: MemberUser) {
  current.value = row
  if (await act(async () => {
    current.value = await memberApi.user(row.id)
    devices.value = (await memberApi.devices({ memberId: row.id, pageNum: 1, pageSize: 100 })).records
    vipLogs.value = await memberApi.vipLogs(row.id)
  })) detailShow.value = true
}

/**
 * 详情中的操作完成后刷新详情与列表。
 */
async function refreshCurrent() {
  if (current.value && detailShow.value) await openDetail(current.value)
  await load()
}

/**
 * 启用或停用账号，停用后其全部令牌立即失效。
 */
async function toggleStatus(row: MemberUser) {
  const target = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  if (await act(() => memberApi.changeStatus(row.id, target), target === 'DISABLED' ? '已停用并使全部令牌失效' : '已启用')) await refreshCurrent()
}

/**
 * 打开 VIP 调整弹窗。
 */
function openVip(row: MemberUser) {
  current.value = row
  Object.assign(vipForm, { mode: 'EXTEND', days: 30, expireAt: row.vipExpireAt ? new Date(row.vipExpireAt).getTime() : null, remark: '' })
  vipShow.value = true
}

/**
 * 按所选方式提交 VIP 调整。
 */
async function saveVip() {
  const user = current.value
  if (!user || busy.value) return
  const ok = await act(async () => {
    if (vipForm.mode === 'EXTEND') await memberApi.extendVip(user.id, vipForm.days, vipForm.remark || undefined)
    else await memberApi.setVip(user.id, vipForm.mode === 'SET' && vipForm.expireAt ? new Date(vipForm.expireAt).toISOString() : null, vipForm.remark || undefined)
  }, 'VIP 已更新，App 下一次请求即生效')
  if (ok) { vipShow.value = false; await refreshCurrent() }
}

/**
 * 打开重置密码弹窗。
 */
function openPassword(row: MemberUser) {
  current.value = row
  password.value = ''
  passwordShow.value = true
}

/**
 * 重置密码，成功后该用户全部令牌失效。
 */
async function savePassword() {
  const user = current.value
  if (!user || busy.value) return
  if (await act(() => memberApi.resetPassword(user.id, password.value), '密码已重置，该用户需重新登录')) passwordShow.value = false
}

/**
 * 强制单个设备下线。
 */
async function offlineDevice(row: MemberDevice) {
  if (await act(() => memberApi.offlineDevice(row.id), '设备已下线')) await refreshCurrent()
}

/**
 * 强制用户全部设备下线。
 */
async function offlineAll(row: MemberUser) {
  if (await act(() => memberApi.offlineUser(row.id), '全部设备已下线')) await refreshCurrent()
}

const columns: DataTableColumns<MemberUser> = [
  { title: '用户名', key: 'username', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '昵称', key: 'nickname', width: 130, ellipsis: { tooltip: true } },
  { title: '状态', key: 'status', width: 80, render: row => h(NTag, { type: row.status === 'ENABLED' ? 'success' : 'default', bordered: false }, () => memberStatusLabels[row.status] ?? row.status) },
  { title: 'VIP', key: 'vipExpireAt', width: 190, render: row => row.vipActive ? h(NTag, { type: 'warning', bordered: false }, () => `至 ${formatTime(row.vipExpireAt)}`) : (row.vipExpireAt ? `已过期 ${formatTime(row.vipExpireAt)}` : '未开通') },
  { title: '在线设备', key: 'onlineDevices', width: 90 },
  { title: '最近登录', key: 'lastLoginAt', width: 170, render: row => formatTime(row.lastLoginAt) },
  { title: '注册时间', key: 'createdAt', width: 170, render: row => formatTime(row.createdAt) },
  { title: '操作', key: 'actions', width: 330, fixed: 'right', render: row => h(NSpace, { size: 6 }, () => [
    h(NButton, { size: 'small', onClick: () => openDetail(row) }, () => '详情'),
    session.hasPermission('member:user:vip') ? h(NButton, { size: 'small', type: 'warning', secondary: true, onClick: () => openVip(row) }, () => 'VIP') : null,
    session.hasPermission('member:user:password') ? h(NButton, { size: 'small', onClick: () => openPassword(row) }, () => '重置密码') : null,
    session.hasPermission('member:user:status') ? h(NPopconfirm, { onPositiveClick: () => toggleStatus(row) }, {
      trigger: () => h(NButton, { size: 'small', type: row.status === 'ENABLED' ? 'error' : 'primary', quaternary: true }, () => row.status === 'ENABLED' ? '停用' : '启用'),
      default: () => row.status === 'ENABLED' ? `确认停用「${row.username}」？其全部登录设备会立即下线。` : `确认启用「${row.username}」？`,
    }) : null,
  ]) },
]
const deviceColumns: DataTableColumns<MemberDevice> = [
  { title: '设备', key: 'deviceName', minWidth: 140, render: row => `${row.deviceName}（${row.deviceCode}）` },
  { title: 'IP', key: 'clientIp', width: 130 },
  { title: '状态', key: 'online', width: 150, render: row => row.online ? h(NTag, { type: 'success', bordered: false }, () => '在线') : (offlineReasonLabels[row.offlineReason] ?? '离线') },
  { title: '登录时间', key: 'loginAt', width: 170, render: row => formatTime(row.loginAt) },
  { title: '最近活跃', key: 'lastActiveAt', width: 170, render: row => formatTime(row.lastActiveAt) },
  { title: '操作', key: 'actions', width: 90, render: row => row.online && session.hasPermission('member:device:offline')
    ? h(NPopconfirm, { onPositiveClick: () => offlineDevice(row) }, { trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => '下线'), default: () => '确认强制该设备下线？' })
    : '—' },
]
const vipColumns: DataTableColumns<MemberVipLog> = [
  { title: '时间', key: 'createdAt', width: 170, render: row => formatTime(row.createdAt) },
  { title: '类型', key: 'changeType', width: 90, render: row => vipChangeLabels[row.changeType] ?? row.changeType },
  { title: '天数', key: 'days', width: 70, render: row => row.days ?? '—' },
  { title: '变更前', key: 'beforeExpireAt', width: 170, render: row => formatTime(row.beforeExpireAt) },
  { title: '变更后', key: 'afterExpireAt', width: 170, render: row => formatTime(row.afterExpireAt) },
  { title: '备注', key: 'remark', minWidth: 120, ellipsis: { tooltip: true } },
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
        title="用户列表"
        description="App 普通用户的查询、启停、重置密码、VIP 开通与登录设备"
        icon="users"
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
            v-model:value="filters.username"
            placeholder="搜索用户名"
            clearable
            style="width: 220px"
            @keyup.enter="search"
            @clear="search"
          />
          <NSelect
            v-model:value="filters.status"
            :options="statusOptions"
            placeholder="账号状态"
            clearable
            style="width: 130px"
            @update:value="search"
          />
          <NSelect
            v-model:value="filters.vip"
            :options="vipOptions"
            placeholder="VIP 状态"
            clearable
            style="width: 140px"
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
          :scroll-x="1260"
          @update:page="page => { pagination.page = page; load() }"
          @update:page-size="size => { pagination.pageSize = size; pagination.page = 1; load() }"
        />
      </div>
    </NCard>
    <NDrawer
      v-model:show="detailShow"
      :width="760"
      placement="right"
    >
      <NDrawerContent
        v-if="current"
        :title="current.username"
        closable
      >
        <NDescriptions
          :column="2"
          label-placement="left"
          bordered
          size="small"
        >
          <NDescriptionsItem label="用户 ID">
            {{ current.id }}
          </NDescriptionsItem>
          <NDescriptionsItem label="昵称">
            {{ current.nickname }}
          </NDescriptionsItem>
          <NDescriptionsItem label="状态">
            {{ memberStatusLabels[current.status] }}
          </NDescriptionsItem>
          <NDescriptionsItem label="VIP">
            {{ current.vipActive ? '有效，至 ' + formatTime(current.vipExpireAt) : (current.vipExpireAt ? '已过期' : '未开通') }}
          </NDescriptionsItem>
          <NDescriptionsItem label="注册 IP">
            {{ current.registerIp || '—' }}
          </NDescriptionsItem>
          <NDescriptionsItem label="最近登录">
            {{ formatTime(current.lastLoginAt) }}（{{ current.lastLoginIp || '—' }}）
          </NDescriptionsItem>
        </NDescriptions>
        <NTabs
          type="line"
          style="margin-top: 16px"
        >
          <NTabPane
            name="devices"
            tab="登录设备"
          >
            <NSpace
              v-if="session.hasPermission('member:device:offline')"
              class="video-filters"
            >
              <NPopconfirm @positive-click="offlineAll(current)">
                <template #trigger>
                  <NButton
                    size="small"
                    type="error"
                    secondary
                  >
                    全部设备下线
                  </NButton>
                </template>
                确认让该用户全部设备立即下线？
              </NPopconfirm>
            </NSpace>
            <NDataTable
              :columns="deviceColumns"
              :data="devices"
              :row-key="row => row.id"
              :scroll-x="820"
              size="small"
            />
          </NTabPane>
          <NTabPane
            name="vip"
            tab="VIP 记录"
          >
            <NDataTable
              :columns="vipColumns"
              :data="vipLogs"
              :row-key="row => row.id"
              :scroll-x="760"
              size="small"
            />
          </NTabPane>
        </NTabs>
      </NDrawerContent>
    </NDrawer>
    <NModal
      v-model:show="vipShow"
      preset="card"
      :title="`调整 VIP · ${current?.username ?? ''}`"
      class="video-modal"
      style="width: min(520px, calc(100vw - 32px))"
    >
      <NForm label-placement="top">
        <NFormItem label="方式">
          <NRadioGroup v-model:value="vipForm.mode">
            <NRadioButton value="EXTEND">
              开通 / 延长
            </NRadioButton>
            <NRadioButton value="SET">
              设置到期时间
            </NRadioButton>
            <NRadioButton value="CANCEL">
              取消 VIP
            </NRadioButton>
          </NRadioGroup>
        </NFormItem>
        <NFormItem
          v-if="vipForm.mode === 'EXTEND'"
          label="延长天数（未开通或已过期从现在起算）"
          :feedback="errors.days"
          :validation-status="errors.days ? 'error' : undefined"
        >
          <NInputNumber
            v-model:value="vipForm.days"
            :min="1"
            :max="3650"
          />
        </NFormItem>
        <NFormItem
          v-if="vipForm.mode === 'SET'"
          label="到期时间"
        >
          <NDatePicker
            v-model:value="vipForm.expireAt"
            type="datetime"
            clearable
          />
        </NFormItem>
        <NFormItem label="备注">
          <NInput
            v-model:value="vipForm.remark"
            maxlength="255"
            placeholder="可选，例如线下付款单号"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="vipShow = false">
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="busy"
            :disabled="vipForm.mode === 'SET' && !vipForm.expireAt"
            @click="saveVip"
          >
            保存
          </NButton>
        </NSpace>
      </template>
    </NModal>
    <NModal
      v-model:show="passwordShow"
      preset="card"
      :title="`重置密码 · ${current?.username ?? ''}`"
      style="width: min(440px, calc(100vw - 32px))"
    >
      <NFormItem
        label="新密码（6 到 64 个字符）"
        :feedback="errors.password"
        :validation-status="errors.password ? 'error' : undefined"
      >
        <NInput
          v-model:value="password"
          type="password"
          show-password-on="click"
        />
      </NFormItem>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="passwordShow = false">
            取消
          </NButton>
          <NButton
            type="primary"
            :loading="busy"
            :disabled="password.length < 6"
            @click="savePassword"
          >
            重置
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </PageBody>
</template>
