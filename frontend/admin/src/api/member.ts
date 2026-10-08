import type { adminComponents, PageQuery, PageResponse } from '@orange-forge/api-client'
import { API_PREFIX, request } from './request'

type Schemas = adminComponents['schemas']
export type MemberUser = Required<Schemas['MemberUserResponse']>
export type MemberDevice = Required<Schemas['MemberDeviceResponse']>
export type MemberVipLog = Required<Schemas['MemberVipLogResponse']>
export type MemberCreateInput = Schemas['MemberCreateRequest']
type Query = PageQuery & Record<string, string | number | boolean | undefined>
const base = `${API_PREFIX}/member`

/**
 * 会员管理接口：普通用户、VIP 与登录设备，类型来自后端 OpenAPI。
 */
export const memberApi = {
  users: (query: Query) => request.get<PageResponse<MemberUser>>(`${base}/users`, { query }),
  user: (id: string) => request.get<MemberUser>(`${base}/users/${id}`),
  create: (body: MemberCreateInput) => request.post<MemberUser>(`${base}/users`, { body }),
  changeStatus: (id: string, status: 'ENABLED' | 'DISABLED') =>
    request.patch<MemberUser>(`${base}/users/${id}/status`, { body: { status } }),
  resetPassword: (id: string, password: string) =>
    request.put<void>(`${base}/users/${id}/password`, { body: { password } }),
  extendVip: (id: string, days: number, remark?: string) =>
    request.post<MemberUser>(`${base}/users/${id}/vip`, { body: { days, remark } }),
  setVip: (id: string, expireAt: string | null, remark?: string) =>
    request.put<MemberUser>(`${base}/users/${id}/vip`, { body: { expireAt, remark } }),
  vipLogs: (id: string) => request.get<MemberVipLog[]>(`${base}/users/${id}/vip-logs`),
  devices: (query: Query) => request.get<PageResponse<MemberDevice>>(`${base}/devices`, { query }),
  offlineDevice: (id: string) => request.post<void>(`${base}/devices/${id}/offline`),
  offlineUser: (id: string) => request.post<void>(`${base}/users/${id}/offline`),
}

/** 账号状态中文名。 */
export const memberStatusLabels: Record<string, string> = { ENABLED: '启用', DISABLED: '停用' }

/** 设备离线原因中文名。 */
export const offlineReasonLabels: Record<string, string> = {
  LOGOUT: '主动退出', LIMIT: '超出台数被踢', ADMIN: '后台强制下线', REVOKED: '改密或停用', EXPIRED: '长期未用过期',
}

/** VIP 变更类型中文名。 */
export const vipChangeLabels: Record<string, string> = { EXTEND: '延长', SET: '设置到期', CANCEL: '取消' }

/**
 * 把 ISO 时间格式化为本地时间文本。
 *
 * @param value ISO 8601 时间
 * @returns 本地时间文本，空值显示为破折号
 */
export function formatTime(value?: string | null): string {
  return value ? new Date(value).toLocaleString() : '—'
}
