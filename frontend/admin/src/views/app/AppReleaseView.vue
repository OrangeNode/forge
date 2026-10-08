<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { NAlert, NButton, NCard, NDescriptions, NDescriptionsItem, NForm, NFormItem, NInput, NInputNumber, NPopconfirm, NSpace, NSwitch, NTag, NUpload, type UploadFileInfo } from 'naive-ui'
import { appConfigApi, type Release } from '@/api/app-config'
import { absoluteLink, uploadFile } from '@/api/files'
import PageBody from '@/components/PageBody.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useSessionStore } from '@/stores/session'
import { useVideoActions } from '@/composables/use-video-actions'
import { copyText } from '@/utils/clipboard'
import '@/views/video/video.css'

const session = useSessionStore()
const { busy, errors, act } = useVideoActions()
const release = ref<Release>()
const uploading = ref(false)
const apkFile = ref<File | null>(null)
const fileList = ref<UploadFileInfo[]>([])
const form = reactive({ changelog: '', force: true })
const minVersion = ref<number | null>(null)
const canEdit = computed(() => session.hasPermission('app:release:edit'))
const link = computed(() => absoluteLink(release.value?.apkUrl))

/**
 * 读取当前版本配置。
 */
async function load() {
  await act(async () => {
    release.value = await appConfigApi.release()
    minVersion.value = release.value.minVersionCode ?? null
  })
}

/**
 * 记录选择的安装包，只保留最后一个。
 */
function choose(files: UploadFileInfo[]) {
  fileList.value = files.slice(-1)
  apkFile.value = fileList.value[0]?.file ?? null
}

/**
 * 上传安装包并发布：版本号、版本名、包名与 SHA-256 由后端解析，不需要手填。
 */
async function publish() {
  if (!apkFile.value || busy.value) return
  uploading.value = true
  try {
    const ok = await act(async () => {
      const uploaded = await uploadFile(apkFile.value as File)
      release.value = await appConfigApi.publishRelease({ fileId: uploaded.id ?? '', changelog: form.changelog || undefined, force: form.force })
    }, '新版本已发布')
    if (ok) {
      fileList.value = []
      apkFile.value = null
      form.changelog = ''
      await load()
    }
  } finally {
    uploading.value = false
  }
}

/**
 * 保存最低可用版本，空值表示不强制更新。
 */
async function saveMinVersion() {
  if (await act(async () => { release.value = await appConfigApi.updateMinVersion(minVersion.value) }, '最低可用版本已保存')) await load()
}

/**
 * 撤销发布，App 不再提示更新。
 */
async function withdraw() {
  if (await act(async () => { release.value = await appConfigApi.withdrawRelease() }, '已撤销发布')) await load()
}

/**
 * 复制下载外链。
 */
async function copyLink() {
  await copyText(link.value)
}

/**
 * 格式化字节数。
 */
function sizeText(bytes?: number | null): string {
  return bytes ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : '—'
}
onMounted(load)
</script>

<template>
  <PageBody>
    <NCard
      class="video-panel"
      :bordered="false"
    >
      <PageHeader
        title="版本更新"
        description="手机与电视共用一个安装包；上传后自动识别版本，低于最低可用版本的 App 必须更新后才能使用"
        icon="refresh"
      >
        <template #actions>
          <NButton
            secondary
            type="primary"
            :loading="busy && !uploading"
            @click="load"
          >
            刷新
          </NButton>
        </template>
      </PageHeader>
      <div class="video-content">
        <NDescriptions
          label-placement="left"
          bordered
          :column="1"
          style="max-width: 860px"
        >
          <NDescriptionsItem label="当前版本">
            <template v-if="release?.versionCode">
              {{ release.versionName || '—' }}（版本号 {{ release.versionCode }}）
              <NTag
                :type="release.minVersionCode === release.versionCode ? 'error' : 'default'"
                :bordered="false"
                size="small"
                style="margin-left: 8px"
              >
                {{ release.minVersionCode ? `低于 ${release.minVersionCode} 强制更新` : '不强制更新' }}
              </NTag>
            </template>
            <span v-else>尚未发布，App 不检查更新</span>
          </NDescriptionsItem>
          <template v-if="release?.versionCode">
            <NDescriptionsItem label="包名">
              {{ release.packageName }}
            </NDescriptionsItem>
            <NDescriptionsItem label="大小">
              {{ sizeText(release.sizeBytes) }}
            </NDescriptionsItem>
            <NDescriptionsItem label="SHA-256">
              <span style="word-break: break-all; font-size: 12px">{{ release.sha256 }}</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="下载外链">
              <NSpace
                align="center"
                :wrap-item="false"
              >
                <a
                  :href="link"
                  target="_blank"
                  rel="noopener"
                  style="word-break: break-all"
                >{{ link }}</a>
                <NButton
                  size="tiny"
                  quaternary
                  type="primary"
                  @click="copyLink"
                >
                  复制
                </NButton>
              </NSpace>
            </NDescriptionsItem>
            <NDescriptionsItem label="更新说明">
              <span style="white-space: pre-wrap">{{ release.changelog || '—' }}</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="发布时间">
              {{ release.updatedAt ? new Date(release.updatedAt).toLocaleString() : '—' }}
            </NDescriptionsItem>
          </template>
        </NDescriptions>

        <template v-if="canEdit">
          <h3 style="margin: 24px 0 8px">
            发布新版本
          </h3>
          <NAlert
            type="info"
            :bordered="false"
            style="max-width: 860px; margin-bottom: 12px"
          >
            安装包必须用同一把正式签名打包，包名为 {{ release?.expectedPackage }}，版本号高于当前版本。
            上传需要“文件管理 → 上传”权限，安装包会自动生成公开外链供 App 下载。
          </NAlert>
          <NForm
            label-placement="top"
            style="max-width: 640px"
          >
            <NFormItem label="安装包（.apk）">
              <NUpload
                :default-upload="false"
                :file-list="fileList"
                accept=".apk"
                :max="1"
                @update:file-list="choose"
              >
                <NButton>选择 APK</NButton>
              </NUpload>
            </NFormItem>
            <NFormItem
              label="更新说明"
              :feedback="errors.changelog"
              :validation-status="errors.changelog ? 'error' : undefined"
            >
              <NInput
                v-model:value="form.changelog"
                type="textarea"
                maxlength="1000"
                show-count
                placeholder="例如：修复播放问题，优化电视遥控操作"
              />
            </NFormItem>
            <NFormItem label="强制更新（开：旧版本必须更新后才能使用；关：提示更新，可跳过）">
              <NSwitch v-model:value="form.force" />
            </NFormItem>
            <NButton
              type="primary"
              :loading="uploading"
              :disabled="!apkFile"
              @click="publish"
            >
              {{ uploading ? '正在上传并解析，请勿关闭页面…' : '上传并发布' }}
            </NButton>
          </NForm>

          <template v-if="release?.versionCode">
            <h3 style="margin: 24px 0 8px">
              调整
            </h3>
            <NSpace align="center">
              <span>最低可用版本号</span>
              <NInputNumber
                v-model:value="minVersion"
                :min="1"
                :max="release.versionCode"
                clearable
                placeholder="空表示不强制"
              />
              <NButton
                :loading="busy && !uploading"
                @click="saveMinVersion"
              >
                保存
              </NButton>
              <NPopconfirm @positive-click="withdraw">
                <template #trigger>
                  <NButton
                    type="error"
                    quaternary
                  >
                    撤销发布
                  </NButton>
                </template>
                撤销后 App 不再提示更新（安装包与外链保留在文件管理中）。确认撤销？
              </NPopconfirm>
            </NSpace>
          </template>
        </template>
      </div>
    </NCard>
  </PageBody>
</template>
