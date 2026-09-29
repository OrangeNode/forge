import { ApiError, isApiResponse, readFieldErrors } from '@orange-forge/api-client'

import { API_PREFIX, urlWithQuery } from '@/api/request'
import { readSessionToken } from '@/utils/session'

/**
 * 文件下载。
 *
 * 下载成功是二进制流，失败同样是 HTTP 200 但响应体是统一 JSON：
 * 因此先按内容类型判断，识别出 JSON 错误体时抛出与请求工厂一致的业务异常，
 * 绝不把错误 JSON 保存成文件。
 *
 * @param fileId 文件 ID（字符串）
 * @param fallbackName 服务端未给出文件名时使用的名称
 * @returns 无返回值；成功后触发浏览器下载
 * @throws ApiError 业务失败、协议异常或传输失败
 */
export async function downloadFile(fileId: string, fallbackName: string): Promise<void> {
  const path = `/files/${fileId}/download`
  const token = readSessionToken()
  const headers: Record<string, string> = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(urlWithQuery(`${API_PREFIX}${path}`), { headers })
  } catch {
    throw new ApiError('transport', 0, '网络连接失败，请检查网络后重试')
  }

  if (!response.ok) {
    throw new ApiError('transport', 0, '下载失败，请稍后重试')
  }

  const contentType = response.headers.get('Content-Type') ?? ''
  if (contentType.includes('application/json')) {
    const body: unknown = await response.json()
    if (isApiResponse(body)) {
      const responseBody = body as { code: number; message: string; traceId?: string | null }
      throw new ApiError('business', responseBody.code, responseBody.message, {
        traceId: responseBody.traceId ?? null,
        fieldErrors: readFieldErrors(body),
      })
    }
    throw new ApiError('protocol', 0, '服务返回数据格式异常，请稍后重试')
  }

  const blob = await response.blob()
  const fileName = resolveFileName(response.headers.get('Content-Disposition'), fallbackName)
  saveBlob(blob, fileName)
}

/**
 * 从 Content-Disposition 解析文件名。
 *
 * 优先使用 RFC 5987 的 filename*，其次是普通 filename，都取不到时用调用方给出的名称。
 *
 * @param contentDisposition 响应头
 * @param fallbackName 兜底文件名
 * @returns 文件名
 */
export function resolveFileName(contentDisposition: string | null, fallbackName: string): string {
  if (!contentDisposition) {
    return fallbackName
  }
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(contentDisposition)
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1])
    } catch {
      return fallbackName
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(contentDisposition)
  return plain?.[1] ?? fallbackName
}

/**
 * 触发浏览器保存二进制内容。
 *
 * @param blob 文件内容
 * @param fileName 保存使用的文件名
 */
function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
