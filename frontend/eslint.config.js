import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueTsEslintConfig from '@vue/eslint-config-typescript'
import globals from 'globals'

/**
 * 前端统一 ESLint 平坦配置。
 *
 * 覆盖后台管理前端（admin/src）与共享包（packages 下的 src）；共享包为纯 TypeScript，不强制 Vue 规则。
 * 生成代码与构建产物不参与检查。
 *
 * 注意：平坦配置只会作用于被 files 模式匹配到的文件。只写 eslint . 时，
 * 未被任何 files 模式覆盖的目录会被静默跳过（表现为“检查通过但其实没检查”），
 * 因此这里显式列出两个源码根目录，并在根 lint 脚本里传同样的路径。
 */
export default [
  {
    ignores: ['**/dist/**', '**/node_modules/**', '**/*.d.ts', '**/coverage/**'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueTsEslintConfig(),
  {
    files: ['admin/src/**/*.{ts,vue}', 'packages/*/src/**/*.ts'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
    rules: {
      // 未使用参数允许以 _ 前缀显式忽略
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  },
  {
    // 构建与代码生成脚本在 Node 环境运行，使用 Node 全局变量而不是浏览器全局变量
    files: ['**/*.mjs', 'admin/*.ts', 'admin/vite.config.ts'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.node,
      },
    },
  },
]
