import { ApiError } from '@orange-forge/api-client'
import { describe, expect, it } from 'vitest'

import { isBusinessCode, readFieldErrors, resolveErrorMessage } from './error-message'

describe('失败提示与字段错误', () => {
  it('业务失败优先展示第一条字段错误', () => {
    const error = new ApiError('business', 400, '请求参数校验失败', {
      fieldErrors: [
        { field: 'username', message: '用户名长度不符合要求' },
        { field: 'password', message: '密码长度不符合要求' },
      ],
    })

    expect(resolveErrorMessage(error)).toBe('用户名长度不符合要求')
  })

  it('业务失败没有字段错误时展示服务端提示', () => {
    const error = new ApiError('business', 409, '数据已存在，请勿重复提交')

    expect(resolveErrorMessage(error)).toBe('数据已存在，请勿重复提交')
  })

  it('传输与协议异常直接使用自身说明', () => {
    expect(resolveErrorMessage(new ApiError('transport', 0, '请求超时，请稍后重试'))).toBe('请求超时，请稍后重试')
    expect(resolveErrorMessage(new ApiError('protocol', 0, '服务返回数据格式异常，请稍后重试'))).toBe(
      '服务返回数据格式异常，请稍后重试',
    )
  })

  it('非请求异常返回兜底文案', () => {
    expect(resolveErrorMessage(new Error('boom'))).toBe('操作失败，请稍后重试')
    expect(resolveErrorMessage('boom')).toBe('操作失败，请稍后重试')
  })

  it('字段错误按字段名归集且保留第一条', () => {
    const error = new ApiError('business', 400, '请求参数校验失败', {
      fieldErrors: [
        { field: 'name', message: '第一次提示' },
        { field: 'name', message: '第二次提示' },
        { field: 'status', message: '状态不合法' },
      ],
    })

    expect(readFieldErrors(error)).toEqual({ name: '第一次提示', status: '状态不合法' })
    expect(readFieldErrors(new Error('boom'))).toEqual({})
  })

  it('按业务结果码判断失败类型', () => {
    expect(isBusinessCode(new ApiError('business', 403, '没有权限'), 403)).toBe(true)
    expect(isBusinessCode(new ApiError('business', 403, '没有权限'), 401)).toBe(false)
    expect(isBusinessCode(new ApiError('transport', 0, '网络错误'), 0)).toBe(false)
  })
})
