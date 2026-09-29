import type { adminComponents, PageQuery, PageResponse } from '@orange-forge/api-client'

import { API_PREFIX, request } from './request'

/**
 * 系统管理接口的类型别名。
 *
 * 类型来自后端 OpenAPI 生成结果，字段不一致时只需在本文件对齐，页面不重复声明协议结构。
 */
export type AdminDetail = adminComponents['schemas']['AdminDetailResponse']
export type AdminCreatePayload = adminComponents['schemas']['AdminCreateRequest']
export type AdminUpdatePayload = adminComponents['schemas']['AdminUpdateRequest']
export type AdminStatusPayload = adminComponents['schemas']['AdminStatusRequest']
export type AdminRoleAssignPayload = adminComponents['schemas']['AdminRoleAssignRequest']
export type AdminPasswordResetPayload = adminComponents['schemas']['AdminPasswordResetRequest']
export type RoleDetail = adminComponents['schemas']['RoleDetailResponse']
export type RoleOption = adminComponents['schemas']['RoleOptionResponse']
export type RoleCreatePayload = adminComponents['schemas']['RoleCreateRequest']
export type RoleUpdatePayload = adminComponents['schemas']['RoleUpdateRequest']
export type RoleGrantPayload = adminComponents['schemas']['RoleGrantRequest']
export type MenuNode = adminComponents['schemas']['MenuNodeResponse']
export type MenuCreatePayload = adminComponents['schemas']['MenuCreateRequest']
export type MenuUpdatePayload = adminComponents['schemas']['MenuUpdateRequest']
export type PermissionItem = adminComponents['schemas']['PermissionResponse']
export type PermissionCreatePayload = adminComponents['schemas']['PermissionCreateRequest']
export type PermissionUpdatePayload = adminComponents['schemas']['PermissionUpdateRequest']

/**
 * 系统管理接口路径前缀。
 */
const SYSTEM_PATH = `${API_PREFIX}/system`

/**
 * 分页查询管理员。
 *
 * @param query 页码、条数与筛选条件
 * @returns 管理员分页结果
 */
export async function fetchAdmins(query: PageQuery & { username?: string; status?: string }): Promise<
  PageResponse<AdminDetail>
> {
  return request.get<PageResponse<AdminDetail>>(`${SYSTEM_PATH}/admins`, { query })
}

/**
 * 查询管理员详情。
 *
 * @param id 管理员 ID
 * @returns 管理员详情
 */
export async function fetchAdminDetail(id: string): Promise<AdminDetail> {
  return request.get<AdminDetail>(`${SYSTEM_PATH}/admins/${id}`)
}

/**
 * 创建管理员。
 *
 * @param payload 创建入参
 * @returns 创建后的管理员详情
 */
export async function createAdmin(payload: AdminCreatePayload): Promise<AdminDetail> {
  return request.post<AdminDetail>(`${SYSTEM_PATH}/admins`, { body: payload })
}

/**
 * 修改管理员基础信息。
 *
 * @param id 管理员 ID
 * @param payload 修改入参
 * @returns 修改后的管理员详情
 */
export async function updateAdmin(id: string, payload: AdminUpdatePayload): Promise<AdminDetail> {
  return request.put<AdminDetail>(`${SYSTEM_PATH}/admins/${id}`, { body: payload })
}

/**
 * 启用或停用管理员。
 *
 * @param id 管理员 ID
 * @param payload 状态入参
 * @returns 修改后的管理员详情
 */
export async function changeAdminStatus(id: string, payload: AdminStatusPayload): Promise<AdminDetail> {
  return request.patch<AdminDetail>(`${SYSTEM_PATH}/admins/${id}/status`, { body: payload })
}

/**
 * 重置管理员密码。
 *
 * @param id 管理员 ID
 * @param payload 新密码入参
 */
export async function resetAdminPassword(id: string, payload: AdminPasswordResetPayload): Promise<void> {
  await request.patch<null>(`${SYSTEM_PATH}/admins/${id}/password`, { body: payload })
}

/**
 * 全量替换管理员的角色。
 *
 * @param id 管理员 ID
 * @param payload 角色 ID 集合
 * @returns 修改后的管理员详情
 */
export async function assignAdminRoles(id: string, payload: AdminRoleAssignPayload): Promise<AdminDetail> {
  return request.put<AdminDetail>(`${SYSTEM_PATH}/admins/${id}/roles`, { body: payload })
}

/**
 * 分页查询角色。
 *
 * @param query 页码、条数与筛选条件
 * @returns 角色分页结果
 */
export async function fetchRoles(query: PageQuery & { name?: string }): Promise<PageResponse<RoleDetail>> {
  return request.get<PageResponse<RoleDetail>>(`${SYSTEM_PATH}/roles`, { query })
}

/**
 * 查询角色下拉选项。
 *
 * @returns 角色选项列表
 */
export async function fetchRoleOptions(): Promise<RoleOption[]> {
  const options = await request.get<RoleOption[] | null>(`${SYSTEM_PATH}/roles/options`)
  return options ?? []
}

/**
 * 查询角色详情，含已授予的权限与菜单。
 *
 * @param id 角色 ID
 * @returns 角色详情
 */
export async function fetchRoleDetail(id: string): Promise<RoleDetail> {
  return request.get<RoleDetail>(`${SYSTEM_PATH}/roles/${id}`)
}

/**
 * 创建角色。
 *
 * @param payload 创建入参
 * @returns 创建后的角色详情
 */
export async function createRole(payload: RoleCreatePayload): Promise<RoleDetail> {
  return request.post<RoleDetail>(`${SYSTEM_PATH}/roles`, { body: payload })
}

/**
 * 修改角色。
 *
 * @param id 角色 ID
 * @param payload 修改入参
 * @returns 修改后的角色详情
 */
export async function updateRole(id: string, payload: RoleUpdatePayload): Promise<RoleDetail> {
  return request.put<RoleDetail>(`${SYSTEM_PATH}/roles/${id}`, { body: payload })
}

/**
 * 删除角色。
 *
 * @param id 角色 ID
 */
export async function deleteRole(id: string): Promise<void> {
  await request.delete<null>(`${SYSTEM_PATH}/roles/${id}`)
}

/**
 * 全量替换角色的权限与菜单授权。
 *
 * @param id 角色 ID
 * @param payload 授权入参
 */
export async function grantRole(id: string, payload: RoleGrantPayload): Promise<void> {
  await request.put<null>(`${SYSTEM_PATH}/roles/${id}/grants`, { body: payload })
}

/**
 * 查询菜单树。
 *
 * @returns 菜单树
 */
export async function fetchMenuTree(): Promise<MenuNode[]> {
  const menus = await request.get<MenuNode[] | null>(`${SYSTEM_PATH}/menus`)
  return menus ?? []
}

/**
 * 创建菜单。
 *
 * @param payload 创建入参
 * @returns 创建后的菜单节点
 */
export async function createMenu(payload: MenuCreatePayload): Promise<MenuNode> {
  return request.post<MenuNode>(`${SYSTEM_PATH}/menus`, { body: payload })
}

/**
 * 修改菜单。
 *
 * @param id 菜单 ID
 * @param payload 修改入参
 * @returns 修改后的菜单节点
 */
export async function updateMenu(id: string, payload: MenuUpdatePayload): Promise<MenuNode> {
  return request.put<MenuNode>(`${SYSTEM_PATH}/menus/${id}`, { body: payload })
}

/**
 * 删除菜单。
 *
 * @param id 菜单 ID
 */
export async function deleteMenu(id: string): Promise<void> {
  await request.delete<null>(`${SYSTEM_PATH}/menus/${id}`)
}

/**
 * 分页查询权限。
 *
 * @param query 页码、条数与筛选条件
 * @returns 权限分页结果
 */
export async function fetchPermissions(query: PageQuery & { code?: string; name?: string }): Promise<
  PageResponse<PermissionItem>
> {
  return request.get<PageResponse<PermissionItem>>(`${SYSTEM_PATH}/permissions`, { query })
}

/**
 * 创建权限。
 *
 * @param payload 创建入参
 * @returns 创建后的权限
 */
export async function createPermission(payload: PermissionCreatePayload): Promise<PermissionItem> {
  return request.post<PermissionItem>(`${SYSTEM_PATH}/permissions`, { body: payload })
}

/**
 * 修改权限说明。
 *
 * @param id 权限 ID
 * @param payload 修改入参
 * @returns 修改后的权限
 */
export async function updatePermission(id: string, payload: PermissionUpdatePayload): Promise<PermissionItem> {
  return request.put<PermissionItem>(`${SYSTEM_PATH}/permissions/${id}`, { body: payload })
}

/**
 * 删除权限。
 *
 * @param id 权限 ID
 */
export async function deletePermission(id: string): Promise<void> {
  await request.delete<null>(`${SYSTEM_PATH}/permissions/${id}`)
}
