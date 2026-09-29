<script setup lang="ts">
import { NButton, NForm, NFormItem, NInput, useMessage } from 'naive-ui'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'
import { resolveErrorMessage } from '@/utils/error-message'

/**
 * 登录页（克制科技 · Calm Tech）。
 *
 * 登录失败只提示一次，且不区分账号是否存在，与后端提示口径一致；
 * 提交期间按钮进入加载态，避免重复提交。
 */
const username = ref('')
const password = ref('')
const submitting = ref(false)

const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()

/**
 * 提交登录表单，成功后回到原地址或工作台。
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
    message.error(resolveErrorMessage(error))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="login__panel">
      <div class="login__brand">
        <span class="login__logo">O</span>
        <div>
          <h1>Orange Forge</h1>
          <p>后台管理端</p>
        </div>
      </div>
      <NForm
        class="login__form"
        @submit.prevent="submit"
      >
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
          class="login__submit"
          type="primary"
          attr-type="submit"
          :loading="submitting"
          block
        >
          登录
        </NButton>
      </NForm>
      <p class="login__hint">
        连续登录失败会触发限流，请确认账号密码后重试。
      </p>
    </section>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px 16px;
  background: radial-gradient(circle at 15% 0%, rgba(255, 125, 51, 0.16), transparent 28rem), #eef0f3;
}

.login__panel {
  width: 100%;
  max-width: 396px;
  border: 1px solid rgba(33, 39, 45, 0.08);
  border-radius: 22px;
  padding: 28px 26px 22px;
  background: #fff;
  box-shadow: 0 24px 55px rgba(25, 32, 40, 0.08);
}

.login__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 22px;
}

.login__logo {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(135deg, #5b7cfa, #8f63ef);
  color: #fff;
  font-size: 18px;
  font-weight: 800;
}

.login__brand h1 {
  margin: 0;
  font-size: 18px;
  letter-spacing: -0.02em;
  color: #17212b;
}

.login__brand p {
  margin: 2px 0 0;
  color: #8290a2;
  font-size: 12px;
}

.login__form {
  margin-top: 6px;
}

.login__submit {
  margin-top: 4px;
}

.login__hint {
  margin: 18px 0 0;
  color: #9aa5b1;
  font-size: 11px;
  line-height: 1.7;
}

@media (max-width: 640px) {
  .login__panel {
    padding: 22px 18px 18px;
  }
}
</style>
