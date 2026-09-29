import { ApiError, RequestFactory } from '@orange-forge/api-client'

import { redirectToLogin } from '@/utils/navigation'
import { clearSessionToken, readSessionToken } from '@/utils/session'

/**
 * 管理端接口前缀。
 *
 * 协议路径属于接口契约，基础地址才来自环境配置；开发环境经 Vite 代理转发到本机后端。
 */
export const API_PREFIX = '/api/admin/v1'

/**
 * 接口基础地址，来自本应用环境配置；为空表示同源相对路径。
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

/**
 * 受保护接口共用的请求工厂。
 *
 * 令牌由本应用注入，公共请求包不保存令牌；收到 code=401 时清理会话并跳转登录页。
 * 其他非零 code 由调用方处理，保证一次失败只出现一处用户提示。
 */
export const request = new RequestFactory(
  {
    baseUrl: API_BASE_URL,
    tokenProvider: readSessionToken,
  },
  {
    onUnauthorized: () => {
      clearSessionToken()
      redirectToLogin()
    },
  },
)

/**
 * 登录接口专用的请求工厂。
 *
 * 登录失败本身也是 code=401，如果复用受保护工厂会在凭据错误时触发会话清理与跳转；
 * 因此登录调用不带未认证钩子，由登录页自行提示凭据错误。
 */
export const anonymousRequest = new RequestFactory({
  baseUrl: API_BASE_URL,
})

/**
 * 拼接完整的接口地址，供需要直接使用 fetch 的场景（例如文件下载）复用同一套地址规则。
 *
 * @param path 以 / 开头的接口路径
 * @param query 查询参数，值为 undefined 的键会被忽略
 * @returns 完整地址
 */
export function urlWithQuery(
  path: string,
  query?: Record<string, string | number | boolean | undefined>,
): string {
  const search = new URLSearchParams()
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) {
        search.set(key, String(value))
      }
    }
  }
  const queryString = search.toString()
  return `${API_BASE_URL}${path}${queryString ? `?${queryString}` : ''}`
}

/**
 * 判断异常是否为统一业务异常。
 *
 * @param error 捕获到的未知异常
 * @returns 是业务异常时返回 true
 */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
