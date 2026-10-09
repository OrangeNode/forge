import type { adminComponents } from '@orange-forge/api-client'

import { API_PREFIX, anonymousRequest, request } from './request'

/**
 * 登录响应载荷，类型来自后端管理端分组的生成结果。
 */
export type AdminLoginPayload = adminComponents['schemas']['AdminLoginResponse']

/**
 * 当前管理员身份与权限载荷。
 */
export type AdminProfilePayload = adminComponents['schemas']['AdminProfileResponse']

/**
 * 菜单节点载荷。
 */
export type AdminMenuPayload = adminComponents['schemas']['AdminMenuResponse']

/**
 * 登录验证码载荷；enabled=false 表示服务端关闭了验证码。
 */
export type AdminCaptchaPayload = adminComponents['schemas']['AdminCaptchaResponse']

/**
 * 登录表单提交的凭据。
 */
export interface LoginCredentials {
  username: string
  password: string
  captchaId?: string
  captchaCode?: string
}

/**
 * 认证接口路径前缀。
 */
const AUTH_PATH = `${API_PREFIX}/auth`

/**
 * 获取登录图形验证码，每次登录失败后需要重新获取。
 *
 * @returns 验证码编号与图片
 */
export async function loadCaptcha(): Promise<AdminCaptchaPayload> {
  return anonymousRequest.get<AdminCaptchaPayload>(`${AUTH_PATH}/captcha`)
}

/**
 * 使用用户名、密码与图形验证码登录。
 *
 * @param credentials 登录凭据
 * @returns 令牌与当前管理员基础信息
 */
export async function login(credentials: LoginCredentials): Promise<AdminLoginPayload> {
  const payload = await anonymousRequest.post<AdminLoginPayload>(`${AUTH_PATH}/login`, {
    body: credentials,
  })
  return payload
}

/**
 * 退出登录并撤销当前令牌。
 */
export async function logout(): Promise<void> {
  await request.post<null>(`${AUTH_PATH}/logout`)
}

/**
 * 查询当前管理员身份与权限。
 *
 * @returns 身份与权限载荷
 */
export async function loadProfile(): Promise<AdminProfilePayload> {
  return request.get<AdminProfilePayload>(`${AUTH_PATH}/me`)
}

/**
 * 查询当前管理员可见菜单。
 *
 * @returns 菜单树，没有可见菜单时为空数组
 */
export async function loadMenus(): Promise<AdminMenuPayload[]> {
  const menus = await request.get<AdminMenuPayload[] | null>(`${AUTH_PATH}/menus`)
  return menus ?? []
}
