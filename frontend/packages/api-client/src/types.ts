/**
 * 统一接口响应协议类型。
 *
 * 与后端 core 的 ApiResponse 对应，仅描述协议，不承载业务字段。
 */

/**
 * 统一响应中的成功结果码。
 */
export const SUCCESS_CODE = 0

/**
 * 字段级校验错误，只在参数错误等场景出现。
 */
export interface FieldError {
  /** 出错字段名 */
  field: string
  /** 面向用户的中文说明 */
  message: string
}

/**
 * 后端统一响应结构。
 *
 * 应用可处理的接口统一返回 HTTP 200，必须读取 `code` 判断结果。
 */
export interface ApiResponse<T> {
  /** 0 表示成功，其余为错误语义码 */
  code: number
  /** 面向调用者的中文说明，不用于程序分支判断 */
  message: string
  /** 成功结果；失败时为 null */
  data: T | null
  /** 请求追踪编号，用于关联服务端日志 */
  traceId: string | null
  /** 可选字段错误明细 */
  fieldErrors?: FieldError[] | null
}

/**
 * 分页结果结构，保持与后端 PageResponse 一致。
 */
export interface PageResponse<T> {
  /** 当前页记录，空页为空数组 */
  records: T[]
  /** 满足条件的记录总数 */
  total: number
  /** 当前页码 */
  pageNum: number
  /** 每页条数 */
  pageSize: number
}

/**
 * 分页请求参数。
 */
export interface PageQuery {
  /** 页码，从 1 开始 */
  pageNum?: number
  /** 每页条数，1—100 */
  pageSize?: number
  /** 排序字段，需在该接口白名单内 */
  sortBy?: string
  /** 排序方向 */
  sortDirection?: 'asc' | 'desc'
}
