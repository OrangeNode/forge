import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'

import { ApiError } from './error'
import { isApiResponse, readFieldErrors } from './protocol'
import { SUCCESS_CODE, type ApiResponse } from './types'

/**
 * 请求工厂的构建参数。
 */
export interface RequestFactoryOptions {
  /**
   * 接口基础地址，来自各端环境配置；为空表示使用同源相对路径（开发环境经 Vite 代理）。
   */
  baseUrl?: string
  /**
   * 是否携带同源 Cookie。默认关闭，认证使用各自应用的 Bearer 令牌。
   */
  withCredentials?: boolean
  /**
   * 请求超时时间，单位毫秒。
   */
  timeoutMs?: number
  /**
   * 当前应用令牌读取函数，由应用注入；返回空值时不添加认证请求头。
   *
   * 公共包只负责把令牌写进请求头，不保存令牌，也不管理登录状态。
   */
  tokenProvider?: () => string | undefined
}

/**
 * 业务错误处理钩子。
 */
export interface RequestErrorHandlers {
  /**
   * 受保护请求返回 code=401 时清理当前应用会话并进入登录页；
   * 登录接口的 401 由调用方单独处理，不在此处跳转。
   */
  onUnauthorized?: (error: ApiError) => void
  /**
   * 返回 code=403 时提示无权限。
   */
  onForbidden?: (error: ApiError) => void
  /**
   * 其余非零 code 的统一处理，用于一次性用户提示。
   */
  onError?: (error: ApiError) => void
}

/**
 * 单次请求的可选参数。
 */
export interface RequestOptions {
  /**
   * 查询参数，值为 undefined 的键会被忽略。
   */
  query?: Record<string, string | number | boolean | undefined>
  /**
   * 请求体，由 axios 按 JSON 序列化。
   */
  body?: unknown
  /**
   * 额外请求头。
   */
  headers?: Record<string, string>
}

/**
 * 公共请求工厂。
 *
 * 只承载协议处理：注入令牌、解析统一响应、按 body.code 分流失败。
 * 不保存令牌、路由或账号状态，这些属于各端应用自己的职责。
 */
export class RequestFactory {
  /**
   * axios 实例，复用连接与默认配置。
   */
  private readonly client: AxiosInstance

  /**
   * 失败分支处理钩子。
   */
  private readonly handlers: RequestErrorHandlers

  /**
   * 当前应用的令牌读取函数，未注入时视为匿名请求。
   */
  private readonly tokenProvider?: () => string | undefined

  /**
   * 构造请求工厂。
   *
   * @param options 基础地址、超时、凭据策略与令牌读取函数
   * @param handlers 失败分支处理钩子
   */
  constructor(options: RequestFactoryOptions = {}, handlers: RequestErrorHandlers = {}) {
    this.handlers = handlers
    this.tokenProvider = options.tokenProvider
    this.client = axios.create({
      baseURL: options.baseUrl ?? '',
      timeout: options.timeoutMs ?? 15000,
      withCredentials: options.withCredentials ?? false,
    })
  }

  /**
   * 发起 GET 请求。
   *
   * @param url 相对基础地址的接口路径
   * @param options 查询参数与额外请求头
   * @returns 统一响应中的业务数据
   */
  get<T>(url: string, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('GET', url, options)
  }

  /**
   * 发起 POST 请求，用于新增与无需返回结果的提交。
   *
   * @param url 相对基础地址的接口路径
   * @param options 请求体与额外请求头
   * @returns 统一响应中的业务数据
   */
  post<T>(url: string, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('POST', url, options)
  }

  /**
   * 发起 PUT 请求，用于完整修改。
   *
   * @param url 相对基础地址的接口路径
   * @param options 请求体与额外请求头
   * @returns 统一响应中的业务数据
   */
  put<T>(url: string, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('PUT', url, options)
  }

  /**
   * 发起 PATCH 请求，用于状态等局部修改。
   *
   * @param url 相对基础地址的接口路径
   * @param options 请求体与额外请求头
   * @returns 统一响应中的业务数据
   */
  patch<T>(url: string, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('PATCH', url, options)
  }

  /**
   * 发起 DELETE 请求。
   *
   * @param url 相对基础地址的接口路径
   * @param options 查询参数与额外请求头
   * @returns 统一响应中的业务数据
   */
  delete<T>(url: string, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('DELETE', url, options)
  }

  /**
   * 执行请求并按 HTTP 200 下的 body.code 判定结果。
   *
   * 无论 HTTP 状态是否为 200，只要响应体符合统一结构就以 code 为准：
   * code=0 返回 data，其余先触发对应钩子再抛出 {@link ApiError}。
   * 响应体不符合协议结构时抛出协议异常，不返回伪成功数据。
   *
   * @param method HTTP 方法
   * @param url 相对基础地址的接口路径
   * @param options 查询参数、请求体与额外请求头
   * @returns 统一响应中的业务数据
   * @throws ApiError 业务失败、协议异常或传输失败
   */
  private async request<T>(method: string, url: string, options: RequestOptions): Promise<T> {
    const config: AxiosRequestConfig = {
      method,
      url,
      params: compactQuery(options.query),
      headers: this.withAuthorization(options.headers),
      data: options.body,
    }

    let response: AxiosResponse<unknown>
    try {
      response = await this.client.request<unknown>(config)
    } catch (error) {
      throw toTransportError(error)
    }

    return resolveResponse<T>(response.data, this.handlers)
  }

  /**
   * 在请求头上补充当前应用的 Bearer 令牌。
   *
   * 令牌由应用注入的读取函数提供：读取不到令牌时不添加请求头，
   * 由后端按未认证返回 body.code=401，公共包不伪造身份。
   *
   * @param headers 调用方传入的额外请求头
   * @returns 含 Authorization 的请求头
   */
  private withAuthorization(headers: Record<string, string> | undefined): Record<string, string> | undefined {
    const token = this.tokenProvider?.()
    if (!token) {
      return headers
    }
    return { ...headers, Authorization: `Bearer ${token}` }
  }
}

/**
 * 移除查询参数中值为 undefined 的键，避免拼出无意义参数。
 *
 * @param query 原始查询参数
 * @returns 过滤后的查询参数
 */
function compactQuery(query: RequestOptions['query']): Record<string, string | number | boolean> | undefined {
  if (!query) {
    return undefined
  }
  const result: Record<string, string | number | boolean> = {}
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) {
      result[key] = value
    }
  }
  return Object.keys(result).length > 0 ? result : undefined
}

/**
 * 依据响应体结构判定请求结果。
 *
 * @param body 已解析的响应体
 * @param handlers 失败分支处理钩子
 * @returns 统一响应中的业务数据；无业务数据时为 null
 * @throws ApiError 响应结构不符合协议或 body.code 非 0
 */
function resolveResponse<T>(body: unknown, handlers: RequestErrorHandlers): T {
  if (!isApiResponse(body)) {
    throw new ApiError('protocol', 0, '服务返回数据格式异常，请稍后重试')
  }

  const response = body as ApiResponse<T>
  if (response.code === SUCCESS_CODE) {
    // data 为 null 表示该用例没有业务数据，不是协议异常
    return response.data as T
  }

  const error = new ApiError('business', response.code, response.message, {
    traceId: response.traceId,
    fieldErrors: readFieldErrors(response),
  })

  if (error.code === 401) {
    handlers.onUnauthorized?.(error)
  } else if (error.code === 403) {
    handlers.onForbidden?.(error)
  } else {
    handlers.onError?.(error)
  }
  throw error
}

/**
 * 把 axios 抛出的异常转换为统一传输异常。
 *
 * 取消请求与超时、连接失败、代理错误同属传输层失败，
 * 都不能伪造 code=0 或伪装成业务失败。
 *
 * @param error axios 抛出的原始异常
 * @returns 统一传输异常
 */
function toTransportError(error: unknown): ApiError {
  if (error instanceof AxiosError) {
    const timeout = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
    const message = timeout ? '请求超时，请稍后重试' : '网络连接失败，请检查网络后重试'
    return new ApiError('transport', 0, message)
  }
  return new ApiError('transport', 0, '网络连接失败，请检查网络后重试')
}
