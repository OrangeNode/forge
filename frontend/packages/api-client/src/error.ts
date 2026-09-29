import type { FieldError } from './types'

/**
 * 请求失败的分类。
 *
 * - `business`：收到符合协议的响应，但 code 非 0，属于业务结果失败。
 * - `protocol`：HTTP 响应不是约定的统一结构，无法判断业务结果。
 * - `transport`：连接、超时或代理错误，没有可用的应用响应体。
 */
export type ApiErrorKind = 'business' | 'protocol' | 'transport'

/**
 * 前端统一请求异常。
 *
 * 页面只依据 `code` 分支，不匹配 message 文案。
 */
export class ApiError extends Error {
  /** 失败分类 */
  readonly kind: ApiErrorKind
  /** 业务结果码；协议或传输异常时使用对应 HTTP 状态或 0 */
  readonly code: number
  /** 请求追踪编号，可能为空 */
  readonly traceId: string | null
  /** 字段级校验错误 */
  readonly fieldErrors: FieldError[]

  /**
   * 构造请求异常。
   *
   * @param kind 失败分类
   * @param code 业务结果码
   * @param message 面向用户的提示
   * @param options 可选追踪编号与字段错误
   */
  constructor(
    kind: ApiErrorKind,
    code: number,
    message: string,
    options: { traceId?: string | null; fieldErrors?: FieldError[] | null } = {},
  ) {
    super(message)
    this.name = 'ApiError'
    this.kind = kind
    this.code = code
    this.traceId = options.traceId ?? null
    this.fieldErrors = options.fieldErrors ?? []
  }

  /**
   * 判断是否为业务结果失败。
   *
   * @returns 业务失败时为 true
   */
  get isBusiness(): boolean {
    return this.kind === 'business'
  }
}
