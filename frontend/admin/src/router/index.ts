import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import HomeView from '@/views/HomeView.vue'

/**
 * 管理端静态路由表。
 *
 * 后端菜单只引用这里的路由标识，不执行远程传来的组件代码；
 * 页面白名单与权限数据在 M3、M5 按真实接口接入。
 */
const routes: RouteRecordRaw[] = [
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
