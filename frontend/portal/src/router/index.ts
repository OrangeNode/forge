import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import PortalLayout from '@/layouts/PortalLayout.vue'
import HomeView from '@/views/HomeView.vue'

/**
 * 用户端静态路由表。
 *
 * 注册与登录入口在 M3 按真实接口接入；当前只保留首页骨架，
 * 不建立假的登录状态或跳转。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: PortalLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: HomeView,
        meta: { title: '首页' },
      },
    ],
  },
]

/**
 * 用户端路由实例，使用 HTML5 历史模式。
 */
export const router = createRouter({
  history: createWebHistory(),
  routes,
})
