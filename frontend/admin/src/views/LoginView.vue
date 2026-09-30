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
    <section class="login__visual">
      <div class="login__brand login__brand--visual">
        <span class="login__logo">OF</span>
        <span>Orange Forge</span>
      </div>
      <div class="login__visual-content">
        <span class="login__eyebrow">让每一次管理都井然有序</span>
        <h1>系统管理，<br><em>一目了然。</em></h1>
        <p>统一管理用户、角色、菜单权限与系统资源。简单、清晰、高效。</p>
      </div>
      <div
        class="login__preview"
        aria-hidden="true"
      >
        <div class="login__preview-head">
          <strong>系统总览</strong>
          <span>2026</span>
        </div>
        <div class="login__preview-grid">
          <span>用户</span>
          <span>角色</span>
          <span class="is-active">菜单</span>
          <span>文件</span>
          <span>存储</span>
          <span>审计</span>
        </div>
      </div>
    </section>

    <section class="login__workspace">
      <div class="login__panel">
        <div class="login__brand login__brand--mobile">
          <span class="login__logo">OF</span>
          <span>Orange Forge</span>
        </div>
        <div class="login__heading">
          <span>欢迎回来</span>
          <h2>登录你的账户</h2>
          <p>继续访问 Orange Forge 管理控制台</p>
        </div>
        <NForm
          class="login__form"
          label-placement="top"
          @submit.prevent="submit"
        >
          <NFormItem label="用户名">
            <NInput
              v-model:value="username"
              size="large"
              placeholder="请输入用户名"
              :input-props="{ autocomplete: 'username' }"
            />
          </NFormItem>
          <NFormItem label="密码">
            <NInput
              v-model:value="password"
              size="large"
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
            size="large"
            attr-type="submit"
            :loading="submitting"
            block
          >
            登录
          </NButton>
        </NForm>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login {
  display: grid;
  width: 100%;
  min-height: 100%;
  grid-template-columns: minmax(520px, 1fr) minmax(480px, 1fr);
  background: #fff;
}

.login__visual {
  position: relative;
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  overflow: hidden;
  padding: clamp(36px, 4.2vw, 64px);
  background: #dcecff;
  color: #17365f;
}

.login__visual::before,
.login__visual::after {
  position: absolute;
  border-radius: 50%;
  background: #b9d2ff;
  content: '';
}

.login__visual::before {
  top: -118px;
  right: -105px;
  width: 360px;
  height: 360px;
  opacity: 0.82;
}

.login__visual::after {
  bottom: -92px;
  left: -92px;
  width: 190px;
  height: 190px;
  opacity: 0.58;
}

.login__brand,
.login__visual-content,
.login__preview {
  position: relative;
  z-index: 1;
}

.login__visual-content {
  width: min(100%, 580px);
  margin: auto 0;
  padding-bottom: 7vh;
}

.login__eyebrow {
  display: inline-block;
  margin-bottom: 18px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.login__visual h1 {
  margin: 0;
  font-size: clamp(48px, 5vw, 76px);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -0.045em;
}

.login__visual h1 em {
  color: #3974e8;
  font-style: normal;
}

.login__visual p {
  max-width: 520px;
  margin: 28px 0 0;
  color: #536c8d;
  font-size: 15px;
  line-height: 2;
}

.login__brand {
  display: flex;
  align-items: center;
  gap: 11px;
  color: #17223d;
  font-size: 16px;
  font-weight: 700;
}

.login__brand--visual {
  color: #17365f;
}

.login__brand--mobile {
  display: none;
}

.login__logo {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 11px;
  background: #5868ee;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  box-shadow: 0 8px 24px rgba(88, 104, 238, 0.28);
}

.login__preview {
  position: absolute;
  right: -46px;
  bottom: 34px;
  width: min(48vw, 430px);
  border: 1px solid rgba(88, 132, 196, 0.16);
  border-radius: 24px;
  padding: 24px;
  background: #eef5ff;
  box-shadow: 0 28px 60px rgba(48, 91, 154, 0.16);
  opacity: 0.88;
  transform: rotate(-3deg);
}

.login__preview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  color: #24466f;
}

.login__preview-head span {
  color: #7890ad;
  font-size: 12px;
}

.login__preview-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}

.login__preview-grid span {
  display: grid;
  min-height: 54px;
  place-items: center;
  border-radius: 10px;
  background: #dbe9fb;
  color: #547092;
  font-size: 11px;
}

.login__preview-grid .is-active {
  background: #3974e8;
  color: #fff;
}

.login__workspace {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 64px;
  background: #fff;
}

.login__panel {
  width: min(100%, 430px);
}

.login__heading {
  margin-bottom: 34px;
}

.login__heading > span {
  display: block;
  margin-bottom: 14px;
  color: #4f6df5;
  font-size: 12px;
  font-weight: 700;
}

.login__heading h2 {
  margin: 0;
  color: #101a2f;
  font-size: 30px;
  letter-spacing: -0.035em;
}

.login__heading p {
  margin: 12px 0 0;
  color: #8993a7;
  font-size: 13px;
}

.login__form {
  margin-top: 0;
}

.login__submit {
  height: 48px;
  margin-top: 8px;
  font-weight: 700;
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.2);
}

@media (max-width: 900px) {
  .login {
    display: block;
  }

  .login__visual {
    display: none;
  }

  .login__workspace {
    padding: 24px;
  }

  .login__brand--mobile {
    display: flex;
    margin-bottom: 64px;
  }
}

@media (max-width: 520px) {
  .login__workspace {
    padding: 0;
    background: #fff;
  }

  .login__panel {
    width: 100%;
    padding: 28px 24px;
  }

  .login__brand--mobile {
    margin-bottom: 48px;
  }
}
</style>
