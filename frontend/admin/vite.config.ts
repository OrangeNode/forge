import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 管理端开发与构建配置。
 *
 * 开发服务器只把 `/api` 代理到本机后端，接口地址不写死域名；
 * 端口可通过环境变量覆盖，冲突时调整本项目配置。
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
