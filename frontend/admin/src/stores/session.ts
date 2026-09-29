import { ApiError } from '@orange-forge/api-client'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  login as loginRequest,
  loadMenus,
  loadProfile,
  logout as logoutRequest,
  type AdminMenuPayload,
  type AdminProfilePayload,
} from '@/api/auth'
import { clearSessionToken, readSessionToken, writeSessionToken } from '@/utils/session'

/**
 * 管理端会话状态。
 *
 * 只保存跨页面必需的令牌、当前身份与菜单：令牌落盘在 sessionStorage，身份与菜单每次进入受保护页面时
 * 重新从后端读取，权限变动不需要等前端缓存过期。
 */
export const useSessionStore = defineStore('session', () => {
  /**
   * 当前会话令牌，初始值来自本应用的 sessionStorage。
   */
  const token = ref<string | undefined>(readSessionToken())

  /**
   * 当前管理员身份与权限。
   */
  const profile = ref<AdminProfilePayload>()

  /**
   * 当前管理员可见菜单。
   */
  const menus = ref<AdminMenuPayload[]>([])

  /**
   * 权限代码集合，供按钮级显示判断使用；后端仍会独立校验每个接口。
   */
  const permissionCodes = computed(() => new Set(profile.value?.permissionCodes ?? []))

  /**
   * 登录并读取身份与菜单。
   *
   * @param username 登录用户名
   * @param password 登录密码
   */
  async function login(username: string, password: string): Promise<void> {
    const payload = await loginRequest(username, password)
    const accessToken = payload.accessToken
    if (!accessToken) {
      // 成功的登录响应必须带令牌，缺失时按协议异常处理，不保存半截会话
      throw new ApiError('protocol', 0, '登录响应缺少令牌，请稍后重试')
    }
    writeSessionToken(accessToken)
    token.value = accessToken
    await loadIdentity()
  }

  /**
   * 读取当前身份与菜单。
   *
   * 收到 code=401 时请求工厂已清理存储，这里同步清空内存状态并继续抛出，
   * 由调用方决定跳转登录页，避免界面停留在“已登录”状态。
   */
  async function loadIdentity(): Promise<void> {
    try {
      const [loadedProfile, loadedMenus] = await Promise.all([loadProfile(), loadMenus()])
      profile.value = loadedProfile
      menus.value = loadedMenus
    } catch (error) {
      if (error instanceof ApiError && error.code === 401) {
        reset()
      }
      throw error
    }
  }

  /**
   * 退出登录：无论后端调用是否成功都清理本地会话。
   */
  async function logout(): Promise<void> {
    try {
      await logoutRequest()
    } finally {
      reset()
    }
  }

  /**
   * 清空本地会话状态。
   */
  function reset(): void {
    clearSessionToken()
    token.value = undefined
    profile.value = undefined
    menus.value = []
  }

  /**
   * 判断当前管理员是否拥有指定权限代码。
   *
   * @param code 权限代码，格式为 模块:资源:动作
   * @returns 拥有该权限时返回 true
   */
  function hasPermission(code: string): boolean {
    return permissionCodes.value.has(code)
  }

  /**
   * 判断本地是否保存了会话令牌。
   *
   * @returns 存在令牌时返回 true；令牌是否仍然有效由后端判定
   */
  function isAuthenticated(): boolean {
    return Boolean(token.value)
  }

  return {
    token,
    profile,
    menus,
    login,
    loadIdentity,
    logout,
    reset,
    hasPermission,
    isAuthenticated,
  }
})
