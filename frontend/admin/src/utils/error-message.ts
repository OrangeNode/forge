import { ApiError } from '@orange-forge/api-client'

/**
 * 把请求异常转换为面向用户的一句话提示。
 *
 * 页面只调用本函数提示一次，避免同一失败出现多处提示；
 * 判定依据是 body.code，不匹配服务端 message 文案。
 *
 * @param error 捕获到的未知异常
 * @returns 中文提示；字段校验失败时返回第一条字段错误
 */
export function resolveErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return '操作失败，请稍后重试'
  }
  if (error.kind === 'transport') {
    return error.message
  }
  if (error.kind === 'protocol') {
    return error.message
  }
  const firstFieldError = error.fieldErrors[0]
  if (firstFieldError) {
    return firstFieldError.message
  }
  return error.message
}

/**
 * 判断异常是否为指定结果码的业务失败。
 *
 * @param error 捕获到的未知异常
 * @param code 期望的业务结果码
 * @returns 结果码一致时返回 true
 */
export function isBusinessCode(error: unknown, code: number): boolean {
  return error instanceof ApiError && error.kind === 'business' && error.code === code
}

/**
 * 读取字段级校验错误，供表单逐字段展示。
 *
 * @param error 捕获到的未知异常
 * @returns 字段名到提示的映射；没有字段错误时为空对象
 */
export function readFieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) {
    return {}
  }
  const result: Record<string, string> = {}
  for (const item of error.fieldErrors) {
    if (!(item.field in result)) {
      result[item.field] = item.message
    }
  }
  return result
}
