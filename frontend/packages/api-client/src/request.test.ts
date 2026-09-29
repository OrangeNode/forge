import { AxiosError, AxiosHeaders } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from './error'
import { RequestFactory } from './request'

/**
 * axios 实例的模拟请求方法。
 *
 * 使用 vi.hoisted 提前定义，保证 vi.mock 工厂执行时已经存在。
 */
const mockRequest = vi.hoisted(() => vi.fn())

vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>()
  return {
    ...actual,
    default: {
      create: () => ({ request: mockRequest }),
    },
  }
})

/**
 * 构造请求工厂的测试实例。
 *
 * @returns 未配置处理钩子的请求工厂
 */
function createFactory(): RequestFactory {
  return new RequestFactory({ baseUrl: '/api/admin/v1' })
}

describe('RequestFactory 结果判定', () => {
  beforeEach(() => {
    mockRequest.mockReset()
  })

  it('HTTP 200 且 code=0 时返回业务数据', async () => {
    mockRequest.mockResolvedValue({
      status: 200,
      data: { code: 0, message: '操作成功', data: { id: '1' }, traceId: 't-1' },
    })

    const result = await createFactory().get<{ id: string }>('/example/hello')

    expect(result).toEqual({ id: '1' })
  })

  it('code=0 且 data 为 null 时返回 null，不当作协议异常', async () => {
    mockRequest.mockResolvedValue({
      status: 200,
      data: { code: 0, message: '操作成功', data: null, traceId: 't-2' },
    })

    const result = await createFactory().post<null>('/auth/logout')

    expect(result).toBeNull()
  })

  it('HTTP 200 且 code=401 时抛出业务异常并触发未认证钩子', async () => {
    const onUnauthorized = vi.fn()
    const onError = vi.fn()
    const factory = new RequestFactory({ baseUrl: '' }, { onUnauthorized, onError })
    mockRequest.mockResolvedValue({
      status: 200,
      data: { code: 401, message: '登录已失效，请重新登录', data: null, traceId: 't-3' },
    })

    const failure = await factory.get('/auth/me').catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(ApiError)
    const error = failure as ApiError
    expect(error.kind).toBe('business')
    expect(error.code).toBe(401)
    expect(error.traceId).toBe('t-3')
    expect(onUnauthorized).toHaveBeenCalledWith(error)
    expect(onError).not.toHaveBeenCalled()
  })

  it('code=403 时触发无权限钩子', async () => {
    const onForbidden = vi.fn()
    const factory = new RequestFactory({ baseUrl: '' }, { onForbidden })
    mockRequest.mockResolvedValue({
      status: 200,
      data: { code: 403, message: '没有该操作权限', data: null, traceId: 't-4' },
    })

    await expect(factory.get('/system/admins')).rejects.toBeInstanceOf(ApiError)

    expect(onForbidden).toHaveBeenCalledTimes(1)
  })

  it('code=400 时从 data.fieldErrors 读取字段错误并触发统一错误钩子', async () => {
    const onError = vi.fn()
    const factory = new RequestFactory({ baseUrl: '' }, { onError })
    mockRequest.mockResolvedValue({
      status: 200,
      data: {
        code: 400,
        message: '参数校验失败',
        data: {
          fieldErrors: [
            { field: 'username', message: '用户名不能为空' },
            { field: 12, message: '结构非法，应被忽略' },
          ],
        },
        traceId: 't-5',
      },
    })

    const failure = await factory.post('/auth/register', { body: {} }).catch((error: unknown) => error)

    const error = failure as ApiError
    expect(error.code).toBe(400)
    expect(error.fieldErrors).toEqual([{ field: 'username', message: '用户名不能为空' }])
    expect(onError).toHaveBeenCalledWith(error)
  })

  it('字段错误结构非法时返回空列表而不是抛出协议异常', async () => {
    const factory = new RequestFactory({ baseUrl: '' })
    mockRequest.mockResolvedValue({
      status: 200,
      data: { code: 400, message: '参数校验失败', data: { fieldErrors: 'not-an-array' }, traceId: 't-7' },
    })

    const failure = await factory.get('/example/validation/1').catch((error: unknown) => error)

    const error = failure as ApiError
    expect(error.code).toBe(400)
    expect(error.fieldErrors).toEqual([])
  })

  it('响应体不是统一结构时抛出协议异常，不触发业务钩子', async () => {
    const onError = vi.fn()
    const onUnauthorized = vi.fn()
    const factory = new RequestFactory({ baseUrl: '' }, { onError, onUnauthorized })
    mockRequest.mockResolvedValue({ status: 200, data: { message: '没有 code 字段' } })

    const failure = await factory.get('/example/hello').catch((error: unknown) => error)

    const error = failure as ApiError
    expect(error.kind).toBe('protocol')
    expect(onError).not.toHaveBeenCalled()
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('传输超时转换为传输异常', async () => {
    mockRequest.mockRejectedValue(
      new AxiosError('timeout of 15000ms exceeded', 'ECONNABORTED', undefined, undefined, {
        status: 0,
        statusText: '',
        headers: new AxiosHeaders(),
        config: { headers: new AxiosHeaders() },
        data: undefined,
      }),
    )

    const failure = await createFactory().get('/example/hello').catch((error: unknown) => error)

    const error = failure as ApiError
    expect(error.kind).toBe('transport')
    expect(error.message).toBe('请求超时，请稍后重试')
  })

  it('网络错误转换为传输异常', async () => {
    mockRequest.mockRejectedValue(
      new AxiosError('Network Error', 'ERR_NETWORK', undefined, undefined, {
        status: 0,
        statusText: '',
        headers: new AxiosHeaders(),
        config: { headers: new AxiosHeaders() },
        data: undefined,
      }),
    )

    const failure = await createFactory().get('/example/hello').catch((error: unknown) => error)

    const error = failure as ApiError
    expect(error.kind).toBe('transport')
    expect(error.message).toBe('网络连接失败，请检查网络后重试')
  })

  it('查询参数忽略 undefined 值', async () => {
    mockRequest.mockResolvedValue({
      status: 200,
      data: { code: 0, message: '操作成功', data: [], traceId: 't-6' },
    })

    await createFactory().get('/members', { query: { pageNum: 1, pageSize: undefined } })

    expect(mockRequest).toHaveBeenCalledWith(
      expect.objectContaining({ params: { pageNum: 1 }, method: 'GET', url: '/members' }),
    )
  })
})
