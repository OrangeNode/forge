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
import PermissionListView from '@/views/system/PermissionListView.vue'
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
        meta: { title: '管理员账号', description: '账号的查询、新增、启停与角色分配' },
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
        meta: { title: '菜单管理', description: '菜单树与前端路由白名单标识' },
      },
      {
        path: 'system/permissions',
        name: 'system-permission',
        component: PermissionListView,
        meta: { title: '权限管理', description: '接口权限代码的维护' },
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
