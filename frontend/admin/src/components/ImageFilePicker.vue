<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NSpace, useMessage } from 'naive-ui'
import { uploadFile } from '@/api/files'
import { resolveErrorMessage } from '@/utils/error-message'

/**
 * 图片选择：通过文件管理上传图片并回填文件 ID；已保存的配置可传入预览地址。
 */
const props = defineProps<{
  /** 已保存配置的图片预览地址，可为空 */
  previewSrc?: string
}>()
const fileId = defineModel<string>({ default: '' })
const message = useMessage()
const input = ref<HTMLInputElement>()
const uploading = ref(false)
const localPreview = ref('')
const uploadedName = ref('')

/**
 * 上传选择的图片并回填文件 ID，本地预览使用浏览器对象地址。
 */
async function onChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.error('请选择图片文件')
    return
  }
  uploading.value = true
  try {
    const result = await uploadFile(file)
    fileId.value = result.id ?? ''
    uploadedName.value = file.name
    localPreview.value = URL.createObjectURL(file)
  } catch (error: unknown) {
    message.error(resolveErrorMessage(error))
  } finally {
    uploading.value = false
    if (input.value) input.value.value = ''
  }
}
</script>

<template>
  <NSpace
    vertical
    :size="8"
  >
    <img
      v-if="localPreview || props.previewSrc"
      :src="localPreview || props.previewSrc"
      alt="图片预览"
      class="image-picker-preview"
    >
    <NSpace align="center">
      <NButton
        :loading="uploading"
        @click="input?.click()"
      >
        {{ fileId ? '更换图片' : '上传图片' }}
      </NButton>
      <span class="video-muted">{{ fileId ? `文件 ID ${fileId}${uploadedName ? ' · ' + uploadedName : ''}` : '未选择图片' }}</span>
    </NSpace>
    <input
      ref="input"
      type="file"
      accept="image/*"
      hidden
      @change="onChange"
    >
  </NSpace>
</template>

<style scoped>
.image-picker-preview { max-width: 320px; max-height: 180px; border-radius: 8px; object-fit: contain; background: #f2f5f9; }
</style>
