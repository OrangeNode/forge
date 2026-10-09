import type { adminComponents, PageQuery, PageResponse } from '@orange-forge/api-client'
import { API_PREFIX, request } from './request'

type Schemas = adminComponents['schemas']
export type Source = Required<Schemas['SourceResponse']>
export type SourceCategory = Omit<Required<Schemas['SourceCategoryResponse']>, 'categoryId'> & { categoryId?: string | null }
export type Category = Omit<Required<Schemas['CategoryResponse']>, 'children'> & { children: Category[] }
export type Video = Required<Schemas['VideoResponse']>
export type Origin = Required<Schemas['OriginResponse']>
export type Episode = Required<Schemas['EpisodeResponse']>
export type PlayLine = Omit<Required<Schemas['PlayLineResponse']>, 'episodes'> & { episodes: Episode[] }
export type VideoDetail = { video: Video; origins: Origin[]; playLines: PlayLine[] }
export type Settings = Required<Schemas['SettingsResponse']>
export type Task = Required<Schemas['TaskResponse']>
export type Run = Required<Schemas['RunResponse']>
export type Pending = Omit<Required<Schemas['PendingResponse']>, 'candidates' | 'incoming'> & { candidates: Video[]; incoming: Required<Schemas['PendingVideoResponse']> }
export type SourceInput = Schemas['SourceRequest']
export type CategoryInput = Schemas['CategoryRequest']
export type VideoInput = Schemas['VideoEditRequest']
export type TaskInput = Schemas['TaskRequest']
export type CollectInput = Schemas['CollectRequest']
export type SettingsInput = Schemas['SettingsRequest']
export type ResolveInput = Schemas['PendingResolveRequest']
export type BatchInput = Schemas['CollectionBatchRequest']
export type SearchItem = Required<Schemas['SearchItemResponse']>
export type SourceSearch = Omit<Required<Schemas['SourceSearchResponse']>, 'items' | 'error'> & { items: SearchItem[]; error?: string | null }
export type SearchCollectItem = Schemas['SearchCollectItem']
export type BatchResult = Omit<Required<Schemas['CollectionBatchResponse']>, 'failures'> & { failures: { id: string; message: string }[] }
type Query = PageQuery & Record<string, string | number | boolean | undefined>
const base = `${API_PREFIX}/video`

/**
 * 视频模块接口：类型来自后端 OpenAPI，业务资源与操作明确列出。
 */
export const videoApi = {
  sources: () => request.get<Source[]>(`${base}/sources`),
  saveSource: (id: string | undefined, body: SourceInput) => id
    ? request.put<Source>(`${base}/sources/${id}`, { body })
    : request.post<Source>(`${base}/sources`, { body }),
  deleteSource: (id: string) => request.delete<void>(`${base}/sources/${id}`),
  sourceCategories: (id: string) => request.get<SourceCategory[]>(`${base}/sources/${id}/categories`),
  refreshSource: (id: string) => request.post<SourceCategory[]>(`${base}/sources/${id}/categories/refresh`),
  autoMapSource: (id: string, createMissing: boolean) =>
    request.post<Required<Schemas['AutoMappingResponse']>>(`${base}/sources/${id}/categories/auto-map`, { body: { createMissing } }),
  mapCategory: (id: string, upstreamId: string, categoryId: string | null) =>
    request.put<void>(`${base}/sources/${id}/mapping`, { body: { upstreamId, categoryId } }),
  categories: () => request.get<Category[]>(`${base}/categories`),
  saveCategory: (id: string | undefined, body: CategoryInput) => id
    ? request.put<Category>(`${base}/categories/${id}`, { body })
    : request.post<Category>(`${base}/categories`, { body }),
  deleteCategory: (id: string) => request.delete<void>(`${base}/categories/${id}`),
  batchDeleteCategories: (ids: string[]) => request.post<BatchResult>(`${base}/categories/batch-delete`, { body: { ids } }),
  videos: (query: Query) => request.get<PageResponse<Video>>(`${base}/videos`, { query }),
  detail: (id: string) => request.get<VideoDetail>(`${base}/videos/${id}`),
  editVideo: (id: string, body: VideoInput) => request.put<Video>(`${base}/videos/${id}`, { body }),
  publishVideo: (id: string, enabled: boolean) =>
    request.patch<void>(`${base}/videos/${id}/status`, { body: { enabled } }),
  deleteVideo: (id: string) => request.delete<void>(`${base}/videos/${id}`),
  primary: (id: string, originId: string) =>
    request.put<void>(`${base}/videos/${id}/primary-origin`, { body: { originId } }),
  editLine: (id: string, body: { name: string; sortNo: number; enabled: boolean }) =>
    request.put<void>(`${base}/videos/lines/${id}`, { body }),
  settings: () => request.get<Settings>(`${base}/settings`),
  saveSettings: (body: SettingsInput) => request.put<Settings>(`${base}/settings`, { body }),
  tasks: () => request.get<Task[]>(`${base}/tasks`),
  saveTask: (id: string | undefined, body: TaskInput) => id
    ? request.put<Task>(`${base}/tasks/${id}`, { body })
    : request.post<Task>(`${base}/tasks`, { body }),
  deleteTask: (id: string) => request.delete<void>(`${base}/tasks/${id}`),
  collect: (body: CollectInput) => request.post<Run>(`${base}/tasks/collect`, { body }),
  preview: (body: CollectInput) => request.post<{ url: string }>(`${base}/tasks/preview`, { body }),
  runs: (query: Query) => request.get<PageResponse<Run>>(`${base}/runs`, { query }),
  run: (id: string) => request.get<Run>(`${base}/runs/${id}`),
  stop: (id: string) => request.post<void>(`${base}/runs/${id}/stop`),
  resume: (id: string) => request.post<void>(`${base}/runs/${id}/resume`),
  pending: (query: Query) => request.get<PageResponse<Pending>>(`${base}/runs/pending`, { query }),
  resolve: (id: string, body: ResolveInput) => request.post<void>(`${base}/pending/${id}/resolve`, { body }),
  batchRuns: (body: BatchInput) => request.post<BatchResult>(`${base}/runs/batch`, { body }),
  batchPending: (body: BatchInput) => request.post<BatchResult>(`${base}/pending/batch`, { body }),
  searchAll: (keyword: string) => request.get<SourceSearch[]>(`${base}/search`, { query: { keyword } }),
  searchSource: (id: string, keyword: string, page: number) =>
    request.get<SourceSearch>(`${base}/search/sources/${id}`, { query: { keyword, page } }),
  collectSearch: (items: SearchCollectItem[]) => request.post<BatchResult>(`${base}/search/collect`, { body: { items } }),
}
