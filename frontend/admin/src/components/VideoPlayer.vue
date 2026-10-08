<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { NAlert, NButton } from 'naive-ui'
import type Hls from 'hls.js'

const props = defineProps<{ url: string; urlType: string }>()
const video = ref<HTMLVideoElement>()
const failure = ref('')
let hls: Hls | undefined

/**
 * 释放上一次的 HLS 实例，避免切换分集或关闭弹窗后继续下载分片。
 */
function release() {
  hls?.destroy()
  hls = undefined
  if (video.value) {
    video.value.pause()
    video.value.removeAttribute('src')
    video.value.load()
  }
}

/**
 * 按地址类型装载播放源：MP4 直接播放；M3U8 优先使用 hls.js（MSE），
 * 仅在不支持 MSE 的浏览器（如 iOS Safari）回退到原生 HLS，Chromium 的 canPlayType 结果不可靠。
 */
async function attach() {
  release()
  failure.value = ''
  const element = video.value
  if (!element || !props.url || props.urlType === 'OTHER') return
  if (props.urlType === 'M3U8') {
    const { default: HlsPlayer } = await import('hls.js')
    if (HlsPlayer.isSupported()) {
      const player = new HlsPlayer()
      hls = player
      player.on(HlsPlayer.Events.ERROR, (_event, data) => {
        if (data.fatal) failure.value = `播放失败（${data.details}），可能是源站地址失效或禁止跨域访问。`
      })
      player.loadSource(props.url)
      player.attachMedia(element)
    } else if (element.canPlayType('application/vnd.apple.mpegurl')) {
      element.src = props.url
    } else {
      failure.value = '当前浏览器不支持 HLS 播放，请在新窗口打开地址。'
      return
    }
  } else {
    element.src = props.url
  }
  element.play().catch(() => undefined)
}

watch(() => [props.url, props.urlType, video.value], () => { void attach() }, { immediate: true })
onBeforeUnmount(release)
</script>

<template>
  <div class="video-player">
    <iframe
      v-if="urlType === 'OTHER'"
      :src="url"
      class="video-player__frame"
      allow="autoplay; fullscreen"
      allowfullscreen
      referrerpolicy="no-referrer"
    />
    <video
      v-else
      ref="video"
      class="video-player__media"
      controls
      playsinline
      @error="failure = failure || '播放失败，源站地址可能已失效。'"
    />
    <NAlert
      v-if="failure"
      type="error"
      :bordered="false"
      class="video-player__alert"
    >
      {{ failure }}
    </NAlert>
    <div class="video-player__url">
      <span class="video-muted">{{ url }}</span>
      <NButton
        text
        type="primary"
        tag="a"
        :href="url"
        target="_blank"
        rel="noopener noreferrer"
      >
        新窗口打开
      </NButton>
    </div>
  </div>
</template>

<style scoped>
.video-player__media,
.video-player__frame { display: block; width: 100%; aspect-ratio: 16 / 9; background: #0b1220; border: 0; border-radius: 10px; }
.video-player__alert { margin-top: 12px; }
.video-player__url { display: flex; gap: 12px; align-items: center; justify-content: space-between; margin-top: 10px; }
.video-player__url span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
