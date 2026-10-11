/**
 * 公告 HTML 样例模板。
 *
 * 只使用公告白名单允许、且 App 弹窗渲染器支持的标签：标题、段落、加粗、小字、列表、引用、分隔线与 font 颜色；
 * 不使用 style、class 与表格，颜色取 App 渲染器认识的十六进制值，在手机与电视的深色弹窗上都清晰可读。
 */
export interface AnnouncementTemplate {
  /** 模板标识 */
  key: string
  /** 模板名称 */
  name: string
  /** 选用模板且标题为空时填入的公告标题 */
  title: string
  /** 模板 HTML */
  html: string
}

/** 内置的公告样例，管理员选用后在编辑框里改文字。 */
export const ANNOUNCEMENT_TEMPLATES: AnnouncementTemplate[] = [
  {
    key: 'update',
    name: '版本更新',
    title: '新版本已发布',
    html: `<h3><font color="#FF8A00">新版本 1.0.6 已发布</font></h3>
<p>本次更新内容：</p>
<ul>
<li><b>电视首页</b>轮播图更清晰</li>
<li>修复部分影片播放卡顿</li>
<li>优化搜索速度</li>
</ul>
<hr>
<p><small><font color="#9AA4B2">打开“我的 → 检查更新”即可升级。</font></small></p>`,
  },
  {
    key: 'holiday',
    name: '节日祝福',
    title: '节日快乐',
    html: `<h2><font color="#FF3B30">国庆快乐</font></h2>
<p>感谢一路陪伴！假期期间影片<b><font color="#FFCC00">照常更新</font></b>，热门新剧第一时间上线。</p>
<hr>
<p><small><font color="#9AA4B2">祝您和家人假期愉快</font></small></p>`,
  },
  {
    key: 'maintenance',
    name: '维护通知',
    title: '系统维护通知',
    html: `<h3><font color="#FFCC00">【系统维护通知】</font></h3>
<p>为提供更稳定的服务，我们将进行系统升级：</p>
<blockquote><b>维护时间：10 月 12 日 02:00 — 04:00</b></blockquote>
<p>维护期间可能无法播放或登录，给您带来不便敬请谅解。</p>`,
  },
  {
    key: 'new-titles',
    name: '新片上线',
    title: '本周新片',
    html: `<h3><font color="#FF8A00">本周新片抢先看</font></h3>
<ol>
<li><b>影片名称一</b> <small><font color="#9AA4B2">· 剧情 · 全 40 集</font></small></li>
<li><b>影片名称二</b> <small><font color="#9AA4B2">· 动作 · 电影</font></small></li>
<li><b>影片名称三</b> <small><font color="#9AA4B2">· 喜剧 · 每周更新</font></small></li>
</ol>
<p>点击下方“去了解”立即观看。</p>`,
  },
  {
    key: 'promotion',
    name: '活动福利',
    title: '限时福利',
    html: `<h2><font color="#FF8A00">限时福利</font></h2>
<p>活动期间使用激活码续费，额外赠送 <b><font color="#FFCC00">7 天 VIP</font></b>！</p>
<ol>
<li>进入“我的 → 激活码续费”</li>
<li>输入激活码并确认</li>
<li>赠送天数自动到账</li>
</ol>
<p><small><font color="#9AA4B2">活动截止：10 月 31 日</font></small></p>`,
  },
  {
    key: 'warning',
    name: '防骗提醒',
    title: '防骗提醒',
    html: `<h3><font color="#FF3B30">防骗提醒</font></h3>
<p>影片中出现的<b>任何广告、二维码、联系方式</b>均与本平台无关，请勿相信，谨防上当受骗。</p>
<blockquote>本平台不会以任何形式向您索要密码或转账。</blockquote>`,
  },
]
