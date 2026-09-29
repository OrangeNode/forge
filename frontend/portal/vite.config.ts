import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 用户端开发与构建配置。
 *
 * 与实现端口的默认值区分开：管理端 5173、用户端 5174；
 * 开发服务器只把 `/api` 代理到本机后端。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': new URL('./src/', import.meta.url).pathname,
    },
  },
  server: {
    port: Number(process.env.VITE_DEV_PORT ?? 5174),
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
