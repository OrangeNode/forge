/**
 * 首页轮播图模板：模糊海报做背景，右侧放清晰海报，左侧放角标、片名、宣传语与“立即观看”。
 * 尺寸 1280×600，与 App 首页 16:7.5 的轮播区域一致；App 会在底部再叠加一行标题。
 */
export interface BannerTemplateInput {
  /** 海报图片 */
  poster: Blob
  /** 片名 */
  title: string
  /** 宣传语，可为空 */
  tagline: string
  /** 分类与年份等补充信息，可为空 */
  meta: string
}

const WIDTH = 1280
const HEIGHT = 600
const ORANGE = '#ff8c1a'
const FONT = '"Microsoft YaHei", "PingFang SC", "Noto Sans SC", "Source Han Sans SC", sans-serif'

/**
 * 按模板绘制轮播图并导出 JPEG。
 *
 * @param input 海报与文案
 * @returns 1280×600 的 JPEG 图片
 */
export async function renderBanner(input: BannerTemplateInput): Promise<Blob> {
  const poster = await createImageBitmap(input.poster)
  const canvas = document.createElement('canvas')
  canvas.width = WIDTH
  canvas.height = HEIGHT
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('浏览器不支持画布，无法生成图片')

  drawBackground(ctx, poster)
  drawPoster(ctx, poster)
  drawText(ctx, input)
  poster.close()

  return new Promise((resolve, reject) => {
    canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('图片导出失败')), 'image/jpeg', 0.9)
  })
}

/**
 * 模糊放大的海报铺满背景并压暗，左侧再叠加深色渐变保证文字可读。
 *
 * @param ctx 画布上下文
 * @param poster 海报
 */
function drawBackground(ctx: CanvasRenderingContext2D, poster: ImageBitmap) {
  const scale = WIDTH / poster.width
  const height = poster.height * scale
  const top = (height - HEIGHT) / 3
  ctx.fillStyle = '#0a0a0e'
  ctx.fillRect(0, 0, WIDTH, HEIGHT)
  ctx.save()
  ctx.filter = 'blur(28px)'
  ctx.drawImage(poster, -40, -top - 40, WIDTH + 80, height + 80)
  ctx.restore()
  ctx.fillStyle = 'rgba(0, 0, 0, 0.45)'
  ctx.fillRect(0, 0, WIDTH, HEIGHT)
  const shade = ctx.createLinearGradient(0, 0, WIDTH * 0.75, 0)
  shade.addColorStop(0, 'rgba(10, 10, 14, 0.8)')
  shade.addColorStop(1, 'rgba(10, 10, 14, 0)')
  ctx.fillStyle = shade
  ctx.fillRect(0, 0, WIDTH, HEIGHT)
}

/**
 * 右侧绘制带阴影的圆角清晰海报。
 *
 * @param ctx 画布上下文
 * @param poster 海报
 */
function drawPoster(ctx: CanvasRenderingContext2D, poster: ImageBitmap) {
  const height = 500
  const width = Math.min(Math.round(poster.width * height / poster.height), 520)
  const x = WIDTH - width - 70
  const y = (HEIGHT - height) / 2 - 20
  ctx.save()
  ctx.shadowColor = 'rgba(0, 0, 0, 0.65)'
  ctx.shadowBlur = 28
  ctx.shadowOffsetY = 8
  ctx.fillStyle = '#000'
  roundRect(ctx, x, y, width, height, 16)
  ctx.fill()
  ctx.restore()
  ctx.save()
  roundRect(ctx, x, y, width, height, 16)
  ctx.clip()
  ctx.drawImage(poster, x, y, width, height)
  ctx.restore()
}

/**
 * 左侧绘制“热播推荐”角标、片名、宣传语、补充信息与“立即观看”按钮。
 *
 * @param ctx 画布上下文
 * @param input 文案
 */
function drawText(ctx: CanvasRenderingContext2D, input: BannerTemplateInput) {
  const x = 70
  const maxWidth = 560
  ctx.textBaseline = 'middle'

  ctx.fillStyle = ORANGE
  roundRect(ctx, x, 90, 150, 46, 23)
  ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.textAlign = 'center'
  ctx.font = `bold 24px ${FONT}`
  ctx.fillText('热播推荐', x + 75, 113)

  ctx.textAlign = 'left'
  ctx.font = `bold ${fitSize(ctx, input.title, maxWidth, 64, 40)}px ${FONT}`
  ctx.fillText(ellipsis(ctx, input.title, maxWidth), x, 212)

  ctx.font = `36px ${FONT}`
  ctx.fillStyle = '#ebebeb'
  if (input.tagline) ctx.fillText(ellipsis(ctx, input.tagline, maxWidth), x, 298)

  ctx.font = `28px ${FONT}`
  ctx.fillStyle = '#ffbe78'
  if (input.meta) ctx.fillText(ellipsis(ctx, input.meta, maxWidth), x, 352)

  ctx.strokeStyle = ORANGE
  ctx.lineWidth = 3
  roundRect(ctx, x, 395, 190, 56, 28)
  ctx.stroke()
  ctx.fillStyle = ORANGE
  ctx.beginPath()
  ctx.moveTo(x + 34, 411)
  ctx.lineTo(x + 34, 435)
  ctx.lineTo(x + 54, 423)
  ctx.closePath()
  ctx.fill()
  ctx.textAlign = 'center'
  ctx.font = `bold 26px ${FONT}`
  ctx.fillText('立即观看', x + 110, 424)
}

/**
 * 片名过长时逐步缩小字号，最小到 min。
 *
 * @param ctx 画布上下文
 * @param text 文本
 * @param maxWidth 最大宽度
 * @param max 起始字号
 * @param min 最小字号
 * @returns 能放下（或已到最小）的字号
 */
function fitSize(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, max: number, min: number): number {
  let size = max
  for (; size > min; size -= 2) {
    ctx.font = `bold ${size}px ${FONT}`
    if (ctx.measureText(text).width <= maxWidth) break
  }
  return size
}

/**
 * 超出宽度时截断并补省略号。
 *
 * @param ctx 已设置字体的画布上下文
 * @param text 文本
 * @param maxWidth 最大宽度
 * @returns 能放下的文本
 */
function ellipsis(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string {
  if (ctx.measureText(text).width <= maxWidth) return text
  let end = text.length
  while (end > 0 && ctx.measureText(`${text.slice(0, end)}…`).width > maxWidth) end--
  return `${text.slice(0, end)}…`
}

/**
 * 生成圆角矩形路径。
 *
 * @param ctx 画布上下文
 * @param x 左上角横坐标
 * @param y 左上角纵坐标
 * @param width 宽
 * @param height 高
 * @param radius 圆角半径
 */
function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number) {
  ctx.beginPath()
  ctx.roundRect(x, y, width, height, radius)
}

/**
 * 从简介中取第一句作为默认宣传语，最多 18 个字。
 *
 * @param description 影片简介
 * @returns 宣传语，简介为空时返回空字符串
 */
export function defaultTagline(description?: string | null): string {
  const first = (description ?? '').replace(/\s+/g, ' ').trim().split(/[。！？!?\n]/)[0]?.trim() ?? ''
  return first.length > 18 ? `${first.slice(0, 18)}…` : first
}
