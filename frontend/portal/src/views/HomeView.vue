<script setup lang="ts">
import { NCard, NText } from 'naive-ui'

/**
 * 用户端首页骨架。
 *
 * 注册、登录与当前用户信息在 M3、M5 按真实接口实现，
 * 本页不展示模拟用户或伪造的登录状态。
 */
const appEnv = import.meta.env.VITE_APP_ENV || 'local'
</script>

<template>
  <NCard
    title="用户端已启动"
    :bordered="false"
  >
    <NText depth="3">
      当前环境：{{ appEnv }}
    </NText>
    <NText depth="3">
      注册、登录与个人首页将在认证功能实现后接入，本页不展示模拟数据。
    </NText>
  </NCard>
</template>
