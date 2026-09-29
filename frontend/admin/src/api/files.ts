import type { adminComponents, PageQuery, PageResponse } from '@orange-forge/api-client'

import { API_PREFIX, request } from './request'

/**
 * 文件与存储接口的类型别名，类型来自后端 OpenAPI 生成结果。
 */
export type FileRecord = adminComponents['schemas']['FileRecordResponse']
export type FileUploadResult = adminComponents['schemas']['FileUploadResponse']
export type StorageConfig = adminComponents['schemas']['StorageConfigResponse']
export type StorageConfigCreatePayload = adminComponents['schemas']['StorageConfigCreateRequest']
export type StorageConfigUpdatePayload = adminComponents['schemas']['StorageConfigUpdateRequest']
export type StorageTestResult = adminComponents['schemas']['StorageTestResponse']

/**
 * 文件接口路径前缀。
 */
const FILE_PATH = `${API_PREFIX}/files`

/**
 * 存储配置接口路径前缀。
 */
const STORAGE_PATH = `${API_PREFIX}/storage-configs`

/**
 * 分页查询文件。
 *
 * @param query 页码、条数与文件名筛选
 * @returns 文件分页结果
 */
export async function fetchFiles(query: PageQuery & { originalName?: string }): Promise<
  PageResponse<FileRecord>
> {
  return request.get<PageResponse<FileRecord>>(FILE_PATH, { query })
}

/**
 * 上传文件到当前默认存储方案。
 *
 * @param file 浏览器选择的文件
 * @returns 上传后的文件元数据
 */
export async function uploadFile(file: File): Promise<FileUploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<FileUploadResult>(FILE_PATH, { body: formData })
}

/**
 * 删除文件记录并清理对象。
 *
 * @param id 文件 ID
 */
export async function deleteFile(id: string): Promise<void> {
  await request.delete<null>(`${FILE_PATH}/${id}`)
}

/**
 * 查询全部存储方案（含历史版本）。
 *
 * @returns 存储方案列表
 */
export async function fetchStorageConfigs(): Promise<StorageConfig[]> {
  const configs = await request.get<StorageConfig[] | null>(STORAGE_PATH)
  return configs ?? []
}

/**
 * 查询当前默认存储方案。
 *
 * @returns 默认存储方案；尚未设置时返回 null
 */
export async function fetchDefaultStorageConfig(): Promise<StorageConfig | null> {
  return request.get<StorageConfig | null>(`${STORAGE_PATH}/default`)
}

/**
 * 创建存储方案的新版本。
 *
 * @param payload 创建入参
 * @returns 创建后的存储方案
 */
export async function createStorageConfig(payload: StorageConfigCreatePayload): Promise<StorageConfig> {
  return request.post<StorageConfig>(STORAGE_PATH, { body: payload })
}

/**
 * 修改未被文件引用的存储方案。
 *
 * @param id 存储方案 ID
 * @param payload 修改入参
 * @returns 修改后的存储方案
 */
export async function updateStorageConfig(id: string, payload: StorageConfigUpdatePayload): Promise<StorageConfig> {
  return request.put<StorageConfig>(`${STORAGE_PATH}/${id}`, { body: payload })
}

/**
 * 删除未被文件引用的存储方案。
 *
 * @param id 存储方案 ID
 */
export async function deleteStorageConfig(id: string): Promise<void> {
  await request.delete<null>(`${STORAGE_PATH}/${id}`)
}

/**
 * 检测存储方案连通性。
 *
 * @param id 存储方案 ID
 * @returns 检测结果
 */
export async function testStorageConfig(id: string): Promise<StorageTestResult> {
  return request.post<StorageTestResult>(`${STORAGE_PATH}/${id}/test`)
}

/**
 * 切换默认存储方案，只影响新上传。
 *
 * @param id 存储方案 ID
 */
export async function setDefaultStorageConfig(id: string): Promise<void> {
  await request.put<null>(`${STORAGE_PATH}/${id}/default`)
}
