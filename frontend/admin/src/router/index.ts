import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import { useSessionStore } from '@/stores/session'
import { registerUnauthorizedHandler } from '@/utils/navigation'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'

/**
 * 路由元信息。
 */
declare module 'vue-router' {
  /**
   * 管理端路由元信息字段。
   */
  interface RouteMeta {
    /**
     * 是否允许未登录访问，只有登录页等匿名入口为 true。
     */
    public?: boolean
    /**
     * 浏览器标题。
     */
    title?: string
  }
}

/**
 * 管理端静态路由表。
 *
 * 只有登录页是匿名入口；页面白名单与权限数据保持分离，
 * 后端菜单只引用这里的路由标识。
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
        component: HomeView,
        meta: { title: '概览' },
      },
    ],
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
