/// <reference types="vite/client" />

/**
 * 管理端环境变量声明。
 *
 * 基础地址来自各端自己的环境文件，不在代码中写死域名。
 */
interface ImportMetaEnv {
  /** 接口基础地址；开发环境留空时经 Vite 代理访问本机后端 */
  readonly VITE_API_BASE_URL: string
  /** 当前环境标识，用于展示与排查 */
  readonly VITE_APP_ENV: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/**
 * 允许在 TypeScript 中导入 Vue 单文件组件。
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
