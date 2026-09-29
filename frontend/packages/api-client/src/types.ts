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
 * 参数校验失败的字段明细位于 `data.fieldErrors`，与后端 `data` 载荷约定一致，
 * 不使用顶层 `fieldErrors` 这类协议外字段。
 */
export interface ApiResponse<T> {
  /** 0 表示成功，其余为错误语义码 */
  code: number
  /** 面向调用者的中文说明，不用于程序分支判断 */
  message: string
  /** 成功结果；失败时为 null，参数校验失败时为字段错误结构 */
  data: T | ApiValidationErrorData | null
  /** 请求追踪编号，用于关联服务端日志 */
  traceId: string | null
}

/**
 * 参数校验失败的响应载荷。
 *
 * 与后端 `data.fieldErrors` 对应，只在 code=400 等参数错误场景出现。
 */
export interface ApiValidationErrorData {
  /** 字段级校验错误列表，空数组表示没有可展示的明细 */
  fieldErrors: FieldError[]
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
 *
 * 只包含页码与每页条数：排序字段与方向随 M2 的排序白名单一并撤回，
 * 等第一个真实列表接口出现时再按该接口的字段集合设计。
 */
export interface PageQuery {
  /** 页码，从 1 开始 */
  pageNum?: number
  /** 每页条数，1—100 */
  pageSize?: number
}
