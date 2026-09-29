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
 * 认证接口路径前缀。
 */
const AUTH_PATH = `${API_PREFIX}/auth`

/**
 * 使用用户名与密码登录。
 *
 * @param username 登录用户名
 * @param password 登录密码
 * @returns 令牌与当前管理员基础信息
 */
export async function login(username: string, password: string): Promise<AdminLoginPayload> {
  const payload = await anonymousRequest.post<AdminLoginPayload>(`${AUTH_PATH}/login`, {
    body: { username, password },
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
