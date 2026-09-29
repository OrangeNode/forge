<script setup lang="ts">
import { NCard, NSpin, NTag } from 'naive-ui'
import { onMounted, ref } from 'vue'

import { fetchLoginLogs, fetchOperationLogs, type LoginLog, type OperationLog } from '@/api/audit'
import { fetchFiles, fetchStorageConfigs } from '@/api/files'
import { fetchAdmins } from '@/api/system'
import { useSessionStore } from '@/stores/session'

/**
 * 工作台。
 *
 * 只展示来自真实接口的数据：无权限或接口失败时对应指标显示为“—”，
 * 不用模拟数据填充，避免把演示数据当成真实运行状态。
 */
const session = useSessionStore()
const loading = ref(true)
const adminTotal = ref<number>()
const fileTotal = ref<number>()
const storageTotal = ref<number>()
const loginFailureTotal = ref<number>()
const recentOperations = ref<OperationLog[]>([])
const recentLogins = ref<LoginLog[]>([])

/**
 * 读取工作台需要的汇总数据。
 *
 * 每个指标独立容错：缺少某个权限时只有该指标不可用，不影响其他区块展示。
 */
async function loadOverview(): Promise<void> {
  loading.value = true
  try {
    const [admins, files, storages, loginFailures, operations, logins] = await Promise.allSettled([
      fetchAdmins({ pageNum: 1, pageSize: 1 }),
      fetchFiles({ pageNum: 1, pageSize: 1 }),
      fetchStorageConfigs(),
      fetchLoginLogs({ pageNum: 1, pageSize: 1, result: 'failure' }),
      fetchOperationLogs({ pageNum: 1, pageSize: 6 }),
      fetchLoginLogs({ pageNum: 1, pageSize: 6 }),
    ])
    adminTotal.value = admins.status === 'fulfilled' ? admins.value.total : undefined
    fileTotal.value = files.status === 'fulfilled' ? files.value.total : undefined
    storageTotal.value = storages.status === 'fulfilled' ? storages.value.length : undefined
    loginFailureTotal.value = loginFailures.status === 'fulfilled' ? loginFailures.value.total : undefined
    recentOperations.value = operations.status === 'fulfilled' ? operations.value.records : []
    recentLogins.value = logins.status === 'fulfilled' ? logins.value.records : []
  } finally {
    loading.value = false
  }
}

/**
 * 把时间戳格式化为本地时间文案。
 *
 * @param value 时间值（ISO 8601 或时间戳）
 * @returns 本地时间文案，空值返回空字符串
 */
function formatTime(value: string | number | undefined): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

/**
 * 展示业务结果码对应的标签类型。
 *
 * @param code 业务结果码
 * @returns Naive UI 标签类型
 */
function resultTagType(code: number | undefined): 'success' | 'error' | 'warning' {
  if (code === 0) {
    return 'success'
  }
  return code === undefined ? 'warning' : 'error'
}

onMounted(() => {
  void loadOverview()
})
</script>

<template>
  <div class="dashboard">
    <section class="dashboard__hero">
      <div>
        <p class="dashboard__eyebrow">
          ORANGE FORGE · ADMIN
        </p>
        <h2>你好，{{ session.profile?.displayName || '管理员' }}</h2>
        <p class="dashboard__lead">
          这里是当前实例的真实概览：账号、文件、存储与最近的操作和登录记录。
        </p>
      </div>
      <NTag
        round
        :bordered="false"
        type="info"
      >
        {{ session.profile?.username }}
      </NTag>
    </section>

    <NSpin :show="loading">
      <section class="dashboard__stats">
        <NCard
          class="stat"
          :bordered="true"
        >
          <small>管理员账号</small>
          <strong>{{ adminTotal ?? '—' }}</strong>
          <em>可管理账号总数</em>
        </NCard>
        <NCard
          class="stat"
          :bordered="true"
        >
          <small>文件记录</small>
          <strong>{{ fileTotal ?? '—' }}</strong>
          <em>未删除的文件元数据</em>
        </NCard>
        <NCard
          class="stat"
          :bordered="true"
        >
          <small>存储方案</small>
          <strong>{{ storageTotal ?? '—' }}</strong>
          <em>全部版本数量</em>
        </NCard>
        <NCard
          class="stat"
          :bordered="true"
        >
          <small>登录失败</small>
          <strong>{{ loginFailureTotal ?? '—' }}</strong>
          <em>保留期内失败次数</em>
        </NCard>
      </section>

      <section class="dashboard__grid">
        <NCard title="最近操作">
          <ul class="feed">
            <li
              v-for="item in recentOperations"
              :key="item.id"
            >
              <span class="feed__dot" />
              <span class="feed__text">
                {{ item.operatorName || '系统' }} · {{ item.action }}
                <small v-if="item.resourceType">（{{ item.resourceType }} {{ item.resourceId || '' }}）</small>
              </span>
              <NTag
                size="small"
                :bordered="false"
                :type="resultTagType(item.resultCode)"
              >
                {{ item.resultCode }}
              </NTag>
              <time>{{ formatTime(item.createdAt) }}</time>
            </li>
            <li
              v-if="recentOperations.length === 0"
              class="feed__empty"
            >
              暂无可展示的操作记录
            </li>
          </ul>
        </NCard>
        <NCard title="最近登录">
          <ul class="feed">
            <li
              v-for="item in recentLogins"
              :key="item.id"
            >
              <span
                class="feed__dot"
                :class="{ 'feed__dot--fail': item.result !== 'success' }"
              />
              <span class="feed__text">
                {{ item.username }}
                <small v-if="item.reason">（{{ item.reason }}）</small>
              </span>
              <NTag
                size="small"
                :bordered="false"
                :type="item.result === 'success' ? 'success' : 'error'"
              >
                {{ item.result === 'success' ? '成功' : '失败' }}
              </NTag>
              <time>{{ formatTime(item.createdAt) }}</time>
            </li>
            <li
              v-if="recentLogins.length === 0"
              class="feed__empty"
            >
              暂无可展示的登录记录
            </li>
          </ul>
        </NCard>
      </section>
    </NSpin>
  </div>
</template>

<style scoped>
.dashboard__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.dashboard__eyebrow {
  margin: 0 0 6px;
  color: #f26722;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.dashboard__hero h2 {
  margin: 0;
  font-size: 26px;
  letter-spacing: -0.04em;
  color: #17212b;
}

.dashboard__lead {
  max-width: 620px;
  margin: 8px 0 0;
  color: #62707e;
  font-size: 13px;
  line-height: 1.7;
}

.dashboard__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.stat small {
  color: #8c98a8;
  font-size: 11px;
}

.stat strong {
  display: block;
  margin: 8px 0 2px;
  font-size: 24px;
  letter-spacing: -0.03em;
}

.stat em {
  color: #9aa5b1;
  font-size: 11px;
  font-style: normal;
}

.dashboard__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
}

.feed {
  margin: 0;
  padding: 0;
  list-style: none;
}

.feed li {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid #f1f4f8;
  font-size: 12px;
  color: #3f4b56;
}

.feed li:last-child {
  border-bottom: none;
}

.feed__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #647ff3;
  box-shadow: 0 0 0 4px #edf0ff;
}

.feed__dot--fail {
  background: #e2554a;
  box-shadow: 0 0 0 4px #fdecea;
}

.feed__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed__text small {
  color: #9aa5b1;
}

.feed time {
  color: #a1aab4;
  font-size: 11px;
}

.feed__empty {
  display: block;
  padding: 14px 0;
  color: #9aa5b1;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .dashboard__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .dashboard__hero {
    flex-direction: column;
  }

  .dashboard__hero h2 {
    font-size: 22px;
  }

  .dashboard__stats {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
