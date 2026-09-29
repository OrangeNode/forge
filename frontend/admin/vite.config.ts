import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 管理端开发与构建配置。
 *
 * 开发服务器只把 `/api` 代理到本机后端，接口地址不写死域名；
 * 端口可通过环境变量覆盖，冲突时调整本项目配置。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': new URL('./src/', import.meta.url).pathname,
    },
  },
  server: {
    port: Number(process.env.VITE_DEV_PORT ?? 5173),
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
