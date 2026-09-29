import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueTsEslintConfig from '@vue/eslint-config-typescript'
import globals from 'globals'

/**
 * 前端统一 ESLint 平坦配置。
 *
 * 覆盖后台管理前端与共享包；共享包为纯 TypeScript，不强制 Vue 规则。
 * 生成代码与构建产物不参与检查。
 */
export default [
  {
    ignores: ['**/dist/**', '**/node_modules/**', '**/*.d.ts', '**/coverage/**'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueTsEslintConfig(),
  {
    files: ['**/*.{ts,vue}'],
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
    files: ['**/*.mjs'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.node,
      },
    },
  },
]
