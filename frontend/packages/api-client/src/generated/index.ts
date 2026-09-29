/**
 * 接口类型入口。
 *
 * 内容由 `pnpm run generate:api-types` 从后端 OpenAPI 分组文档生成，不手工修改；
 * 生成结果与后端管理端分组一一对应，业务代码只从本文件导入。
 *
 * 重新生成步骤见 `docs/本地开发指南.md` 的接口类型生成一节。
 */
export type { components as adminComponents, operations as adminOperations, paths as adminPaths } from './admin'
