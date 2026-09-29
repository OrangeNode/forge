<script setup lang="ts">
import { NCard, NDescriptions, NDescriptionsItem, NText } from 'naive-ui'

/**
 * 管理端概览页。
 *
 * 只展示当前构建使用的环境信息，证明骨架已启动；
 * 真实管理页面在 M5 按已实现接口补充，此处不伪造业务数据。
 */
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '同源，开发环境经 /api 代理'
const appEnv = import.meta.env.VITE_APP_ENV || 'local'
</script>

<template>
  <NCard
    title="管理端已启动"
    :bordered="false"
  >
    <NDescriptions
      :column="1"
      label-placement="left"
    >
      <NDescriptionsItem label="当前环境">
        <NText>{{ appEnv }}</NText>
      </NDescriptionsItem>
      <NDescriptionsItem label="接口基础地址">
        <NText>{{ apiBaseUrl }}</NText>
      </NDescriptionsItem>
    </NDescriptions>
    <NText depth="3">
      账号、权限与业务页面在后续阶段按接口实际实现接入，本页不展示模拟业务数据。
    </NText>
  </NCard>
</template>
