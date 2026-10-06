import type { Category } from '@/api/video'

export const videoPageSize = Number(import.meta.env.VITE_VIDEO_PAGE_SIZE)
export const videoPageSizeOptions = String(import.meta.env.VITE_VIDEO_PAGE_SIZE_OPTIONS).split(',').map(Number)
export const videoRefreshMillis = Number(import.meta.env.VITE_VIDEO_REFRESH_MILLIS)
export const videoTaskTime = String(import.meta.env.VITE_VIDEO_DEFAULT_TASK_TIME)
export const videoIntervalHours = Number(import.meta.env.VITE_VIDEO_DEFAULT_INTERVAL_HOURS)
export const videoZone = String(import.meta.env.VITE_VIDEO_DEFAULT_ZONE)
export const ranges = [
  { label: '日 · 最近24小时', value: 'DAY' },
  { label: '周 · 最近7天', value: 'WEEK' },
  { label: '全部', value: 'ALL' },
]
export const fieldLabels: Record<string, string> = {
  name: '名称', category: '本地分类', year: '年份', area: '地区', language: '语言',
  director: '导演', actors: '演员', posterUrl: '封面', description: '简介',
  remarks: '更新说明', declaredTotal: '源站总集数', ended: '源站完结标志',
}
export const statusLabels: Record<string, string> = {
  QUEUED: '排队中', RUNNING: '运行中', STOP_REQUESTED: '正在停止', COMPLETED: '已完成',
  FAILED: '失败', STOPPED: '已停止', INTERRUPTED: '已中断',
  PENDING: '待确认', RESOLVED: '已确认', IGNORED: '已忽略',
}

/**
 * 将真实分类树展开成表单选项，主键始终保持字符串。
 */
export function categoryOptions(tree: Category[], parent = ''): { label: string; value: string }[] {
  return tree.flatMap(item => {
    const label = parent ? `${parent} / ${item.name}` : item.name
    return [{ label, value: item.id }, ...categoryOptions(item.children ?? [], label)]
  })
}
