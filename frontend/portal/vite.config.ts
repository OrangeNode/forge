import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 用户端开发与构建配置。
 *
 * 与实现端口的默认值区分开：管理端 5173、用户端 5174；
 * 开发服务器只把 `/api` 代理到本机后端。
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)))
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src/', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_DEV_PORT),
      strictPort: true,
      proxy: {
        [env.VITE_API_PROXY_PREFIX]: {
          target: env.VITE_API_PROXY_TARGET,
          changeOrigin: true,
        },
      },
    },
  }
})
