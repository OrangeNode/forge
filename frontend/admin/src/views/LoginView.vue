<script setup lang="ts">
import { ApiError } from '@orange-forge/api-client'
import { NButton, NCard, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

/**
 * 登录页骨架。
 *
 * 本阶段只搭结构与失败分流，视觉样式等设计确定后再统一调整；
 * 登录失败只提示一次，且不区分账号是否存在，与后端提示口径一致。
 */
const username = ref('')
const password = ref('')
const submitting = ref(false)

const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()

/**
 * 把登录异常转换为用户提示。
 *
 * @param error 捕获到的未知异常
 * @returns 中文提示
 */
function resolveFailureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 401) {
      return '用户名或密码错误'
    }
    const firstFieldError = error.fieldErrors[0]
    if (firstFieldError) {
      return firstFieldError.message
    }
    return error.message
  }
  return '登录失败，请稍后重试'
}

/**
 * 提交登录表单，成功后回到原地址或概览页。
 */
async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    await session.login(username.value, password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    message.error(resolveFailureMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <NCard
      class="login__card"
      title="Orange Forge 管理端"
      :bordered="false"
    >
      <NForm @submit.prevent="submit">
        <NFormItem label="用户名">
          <NInput
            v-model:value="username"
            placeholder="请输入用户名"
            :input-props="{ autocomplete: 'username' }"
          />
        </NFormItem>
        <NFormItem label="密码">
          <NInput
            v-model:value="password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
            :input-props="{ autocomplete: 'current-password' }"
            @keyup.enter="submit"
          />
        </NFormItem>
        <NButton
          type="primary"
          attr-type="submit"
          :loading="submitting"
          block
        >
          登录
        </NButton>
      </NForm>
    </NCard>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 16px;
}

.login__card {
  width: 100%;
  max-width: 360px;
}
</style>
