import type { adminComponents } from '@orange-forge/api-client'
import { API_BASE_URL, API_PREFIX, request } from './request'

type Schemas = adminComponents['schemas']
export type Splash = Required<Schemas['AppSplashResponse']>
export type Banner = Required<Schemas['AppBannerResponse']>
export type Section = Omit<Required<Schemas['AppSectionResponse']>, 'videos'> & { videos: { id: string; name?: string }[] }
export type Features = Required<Schemas['AppFeatureResponse']>
export type HotSearch = Required<Schemas['AppHotSearchResponse']>
export type SplashInput = Schemas['AppSplashRequest']
export type BannerInput = Schemas['AppBannerRequest']
export type SectionInput = Schemas['AppSectionRequest']
export type FeatureInput = Schemas['AppFeatureRequest']
const base = `${API_PREFIX}/app`

/**
 * App 管理接口：开屏、轮播、推荐位、热搜与功能开关。
 */
export const appConfigApi = {
  splashes: () => request.get<Splash[]>(`${base}/splashes`),
  saveSplash: (id: string | undefined, body: SplashInput) => id
    ? request.put<Splash>(`${base}/splashes/${id}`, { body })
    : request.post<Splash>(`${base}/splashes`, { body }),
  deleteSplash: (id: string) => request.delete<void>(`${base}/splashes/${id}`),
  banners: () => request.get<Banner[]>(`${base}/banners`),
  saveBanner: (id: string | undefined, body: BannerInput) => id
    ? request.put<Banner>(`${base}/banners/${id}`, { body })
    : request.post<Banner>(`${base}/banners`, { body }),
  deleteBanner: (id: string) => request.delete<void>(`${base}/banners/${id}`),
  sections: () => request.get<Section[]>(`${base}/sections`),
  saveSection: (id: string | undefined, body: SectionInput) => id
    ? request.put<Section>(`${base}/sections/${id}`, { body })
    : request.post<Section>(`${base}/sections`, { body }),
  deleteSection: (id: string) => request.delete<void>(`${base}/sections/${id}`),
  features: () => request.get<Features>(`${base}/features`),
  saveFeatures: (body: FeatureInput) => request.put<Features>(`${base}/features`, { body }),
  hotSearch: () => request.get<HotSearch>(`${base}/hot-search`),
  saveHotSearch: (body: HotSearch) => request.put<HotSearch>(`${base}/hot-search`, { body }),
}

/**
 * 把后端返回的 App 图片相对地址拼成浏览器可访问的地址。
 *
 * @param imageUrl 后端返回的相对地址
 * @returns 完整地址，空值返回空字符串
 */
export function imageSrc(imageUrl?: string | null): string {
  return imageUrl ? `${API_BASE_URL}${imageUrl}` : ''
}

/**
 * 判断开始结束时间窗口描述。
 *
 * @param startAt 开始时间
 * @param endAt 结束时间
 * @returns 中文时间窗口
 */
export function windowText(startAt?: string | null, endAt?: string | null): string {
  const start = startAt ? new Date(startAt).toLocaleString() : '立即'
  const end = endAt ? new Date(endAt).toLocaleString() : '长期'
  return `${start} 至 ${end}`
}
