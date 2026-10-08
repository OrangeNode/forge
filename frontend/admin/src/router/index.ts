import SourceListView from '@/views/video/SourceListView.vue'
import CategoryListView from '@/views/video/CategoryListView.vue'
import VideoListView from '@/views/video/VideoListView.vue'
import CollectionSettingsView from '@/views/video/CollectionSettingsView.vue'
import CollectionTaskView from '@/views/video/CollectionTaskView.vue'
import CollectionRunView from '@/views/video/CollectionRunView.vue'
import MemberUserView from '@/views/member/MemberUserView.vue'
import MemberDeviceView from '@/views/member/MemberDeviceView.vue'
import AppSplashView from '@/views/app/AppSplashView.vue'
import AppBannerView from '@/views/app/AppBannerView.vue'
import AppSectionView from '@/views/app/AppSectionView.vue'
import AppHotSearchView from '@/views/app/AppHotSearchView.vue'
import AppFeatureView from '@/views/app/AppFeatureView.vue'
import AppFeedbackView from '@/views/app/AppFeedbackView.vue'
import AppReleaseView from '@/views/app/AppReleaseView.vue'
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import { useSessionStore } from '@/stores/session'
import { registerUnauthorizedHandler } from '@/utils/navigation'
import DashboardView from '@/views/DashboardView.vue'
import FileListView from '@/views/file/FileListView.vue'
import StorageConfigView from '@/views/file/StorageConfigView.vue'
import LoginView from '@/views/LoginView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import LoginLogView from '@/views/audit/LoginLogView.vue'
import OperationLogView from '@/views/audit/OperationLogView.vue'
import AdminListView from '@/views/system/AdminListView.vue'
import MenuListView from '@/views/system/MenuListView.vue'
import RoleListView from '@/views/system/RoleListView.vue'

/**
 * 路由元信息。
 */
declare module 'vue-router' {
  /**
   * 管理端路由元信息字段。
   */
  interface RouteMeta {
    /**
     * 是否允许未登录访问，只有登录页为 true。
     */
    public?: boolean
    /**
     * 页面标题，同时用于菜单高亮与顶栏展示。
     */
    title?: string
    /**
     * 顶栏副标题，用于说明页面用途。
     */
    description?: string
  }
}

/**
 * 管理端静态路由表。
 *
 * 路由名与后端菜单的 routeKey 一一对应，菜单只引用这里的标识；
 * 页面按业务分组放在 views 下的同名目录中。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: AdminLayout,
    children: [
      { path: 'video/sources', name: 'video-source', component: SourceListView, meta: { title: '资源站管理' } },
      { path: 'video/categories', name: 'video-category', component: CategoryListView, meta: { title: '视频分类' } },
      { path: 'video/videos', name: 'video-videos', component: VideoListView, meta: { title: '视频管理' } },
      { path: 'video/settings', name: 'video-settings', component: CollectionSettingsView, meta: { title: '视频采集设置' } },
      { path: 'video/tasks', name: 'video-task', component: CollectionTaskView, meta: { title: '采集任务' } },
      { path: 'video/runs', name: 'video-run', component: CollectionRunView, meta: { title: '采集记录' } },
      { path: 'member/users', name: 'member-user', component: MemberUserView, meta: { title: '用户列表', description: 'App 普通用户、VIP 与登录设备' } },
      { path: 'member/devices', name: 'member-device', component: MemberDeviceView, meta: { title: '登录设备', description: '普通用户登录设备与强制下线' } },
      { path: 'app/splash', name: 'app-splash', component: AppSplashView, meta: { title: '开屏设置', description: 'App 开屏广告' } },
      { path: 'app/banners', name: 'app-banner', component: AppBannerView, meta: { title: '首页轮播', description: 'App 首页顶部轮播' } },
      { path: 'app/sections', name: 'app-section', component: AppSectionView, meta: { title: '首页推荐位', description: 'App 首页推荐行' } },
      { path: 'app/hot-search', name: 'app-hot-search', component: AppHotSearchView, meta: { title: '热搜设置', description: '热搜词与屏蔽词' } },
      { path: 'app/features', name: 'app-feature', component: AppFeatureView, meta: { title: '功能开关', description: 'VIP、注册、同时登录台数与客服公告' } },
      { path: 'app/feedbacks', name: 'app-feedback', component: AppFeedbackView, meta: { title: '反馈处理', description: '意见反馈与播放报错' } },
      { path: 'app/release', name: 'app-release', component: AppReleaseView, meta: { title: '版本更新', description: 'App 安装包发布与强制更新' } },
      {
        path: '',
        name: 'home',
        component: DashboardView,
        meta: { title: '工作台', description: '系统概览与最近动态' },
      },
      {
        path: 'system/admins',
        name: 'system-admin',
        component: AdminListView,
        meta: { title: '用户管理', description: '后台用户的查询、新建、启停与角色分配' },
      },
      {
        path: 'system/roles',
        name: 'system-role',
        component: RoleListView,
        meta: { title: '角色管理', description: '角色的维护与菜单、权限授权' },
      },
      {
        path: 'system/menus',
        name: 'system-menu',
        component: MenuListView,
        meta: { title: '菜单与权限', description: '菜单树与本节点声明的接口权限标识' },
      },
      {
        path: 'files',
        name: 'file-list',
        component: FileListView,
        meta: { title: '文件列表', description: '上传、下载与文件记录' },
      },
      {
        path: 'storage',
        name: 'storage-config',
        component: StorageConfigView,
        meta: { title: '存储配置', description: '存储方案版本、连接检测与默认切换' },
      },
      {
        path: 'audit/operations',
        name: 'audit-operation',
        component: OperationLogView,
        meta: { title: '操作日志', description: '管理操作的身份、对象与结果' },
      },
      {
        path: 'audit/logins',
        name: 'audit-login',
        component: LoginLogView,
        meta: { title: '登录日志', description: '登录成功与失败记录' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
    meta: { title: '页面不存在' },
  },
]

/**
 * 管理端路由实例，使用 HTML5 历史模式。
 */
export const router = createRouter({
  history: createWebHistory(),
  routes,
})

registerUnauthorizedHandler(() => {
  void router.push({ name: 'login' })
})

/**
 * 受保护页面的会话检查。
 *
 * 本地有令牌但尚未读取身份时先读取一次：令牌已失效（code=401）就回到登录页，
 * 并带上原地址，登录成功后可以回到原页面。
 */
router.beforeEach(async (to) => {
  const session = useSessionStore()
  if (to.meta.public === true) {
    return session.isAuthenticated() ? { name: 'home' } : true
  }
  if (!session.isAuthenticated()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!session.profile) {
    try {
      await session.loadIdentity()
    } catch {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  return true
})
