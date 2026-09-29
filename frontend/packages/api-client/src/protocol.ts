import type { ApiResponse, FieldError } from './types'

/**
 * 判断未知值是否为可用的统一响应结构。
 *
 * 缺失 code、data 字段结构非法或顶层不是对象时按协议异常处理，
 * 不默认当作成功，也不把任意 JSON 当作业务响应。
 *
 * @param value 解析后的未知数据
 * @returns 结构符合统一响应时为 true
 */
export function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return false
  }
  const candidate = value as Record<string, unknown>
  if (typeof candidate.code !== 'number' || !Number.isInteger(candidate.code)) {
    return false
  }
  if ('data' in candidate === false) {
    return false
  }
  if (candidate.traceId !== undefined && candidate.traceId !== null && typeof candidate.traceId !== 'string') {
    return false
  }
  return true
}

/**
 * 读取参数校验失败时的字段错误列表。
 *
 * 错误明细位于 `data.fieldErrors`；只保留 field 与 message 均为字符串且字段名非空的条目，
 * 忽略结构异常的明细，避免把未知内容渲染到页面。
 *
 * @param body 解析后的统一响应结构
 * @returns 规范化后的字段错误数组
 */
export function readFieldErrors(body: unknown): FieldError[] {
  if (typeof body !== 'object' || body === null) {
    return []
  }
  const data = (body as Record<string, unknown>).data
  if (typeof data !== 'object' || data === null) {
    return []
  }
  const raw = (data as Record<string, unknown>).fieldErrors
  if (!Array.isArray(raw)) {
    return []
  }
  const result: FieldError[] = []
  for (const item of raw) {
    if (typeof item !== 'object' || item === null) {
      continue
    }
    const entry = item as Record<string, unknown>
    if (typeof entry.field === 'string' && entry.field.length > 0 && typeof entry.message === 'string') {
      result.push({ field: entry.field, message: entry.message })
    }
  }
  return result
}
