import type { adminComponents, PageQuery, PageResponse } from '@orange-forge/api-client'

import { API_PREFIX, request } from './request'

/**
 * 审计接口的类型别名，类型来自后端 OpenAPI 生成结果。
 */
export type LoginLog = adminComponents['schemas']['LoginLogResponse']
export type OperationLog = adminComponents['schemas']['OperationLogResponse']

/**
 * 审计接口路径前缀。
 */
const AUDIT_PATH = `${API_PREFIX}/audit`

/**
 * 登录日志筛选条件。
 */
export interface LoginLogQuery extends PageQuery {
  /** 登录用户名，模糊匹配 */
  username?: string
  /** 结果：success 或 failure */
  result?: string
  /** 起始时间（含），带时区的 ISO 8601 */
  startTime?: string
  /** 结束时间（含），带时区的 ISO 8601 */
  endTime?: string
}

/**
 * 操作日志筛选条件。
 */
export interface OperationLogQuery extends PageQuery {
  /** 操作者名称，模糊匹配 */
  operatorName?: string
  /** 动作代码，精确匹配，例如 system:admin:create */
  action?: string
  /** 业务结果码，精确匹配；0 表示成功 */
  resultCode?: string
  /** 起始时间（含），带时区的 ISO 8601 */
  startTime?: string
  /** 结束时间（含），带时区的 ISO 8601 */
  endTime?: string
}

/**
 * 分页查询登录日志。
 *
 * @param query 页码、条数与筛选条件
 * @returns 登录日志分页结果
 */
export async function fetchLoginLogs(query: LoginLogQuery): Promise<PageResponse<LoginLog>> {
  return request.get<PageResponse<LoginLog>>(`${AUDIT_PATH}/login-logs`, { query })
}

/**
 * 分页查询操作日志。
 *
 * @param query 页码、条数与筛选条件
 * @returns 操作日志分页结果
 */
export async function fetchOperationLogs(query: OperationLogQuery): Promise<PageResponse<OperationLog>> {
  return request.get<PageResponse<OperationLog>>(`${AUDIT_PATH}/operation-logs`, { query })
}
