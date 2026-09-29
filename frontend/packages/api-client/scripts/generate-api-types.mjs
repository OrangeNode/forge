#!/usr/bin/env node
/**
 * 从运行中的后端导出 OpenAPI 规范并生成前端接口类型。
 *
 * 约定：
 * - 规范来源为后端分组文档 `/v3/api-docs/admin` 与 `/v3/api-docs/app`，与管理端、用户端分组一致。
 * - 生成结果写入 `src/generated/`，由脚本重新生成，不手工修改。
 * - 后端地址通过 `FORGE_OPENAPI_BASE_URL` 覆盖，默认本机 8080，不写死域名。
 * - 生成直接调用 `openapi-typescript` 的编程接口，不经由子进程执行 pnpm：
 *   Node 20.12 起在 Windows 上不使用 shell 调用 `.cmd` 会直接失败（spawn EINVAL），
 *   受限环境也不允许创建带管道 stdio 的子进程；版本仍由 `package.json` 固定。
 *
 * 用法（工作目录 frontend/packages/api-client）：
 *   node scripts/generate-api-types.mjs
 */
import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import openapiTS, { COMMENT_HEADER, astToString } from 'openapi-typescript'

/** 当前脚本所在包目录。 */
const packageDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/** 后端基础地址，来自环境变量，默认本机开发端口。 */
const baseUrl = process.env.FORGE_OPENAPI_BASE_URL ?? 'http://127.0.0.1:8080'

/** 需要生成类型的分组，与后端 OpenAPI 分组名称一致。 */
const groups = ['admin', 'app']

/**
 * 读取单个分组的 OpenAPI 规范。
 *
 * @param {string} group 分组名称
 * @returns {Promise<object>} 规范内容
 */
async function fetchDocument(group) {
  const response = await fetch(`${baseUrl}/v3/api-docs/${group}`)
  if (!response.ok) {
    throw new Error(`读取 ${group} 分组文档失败：HTTP ${response.status}`)
  }
  return response.json()
}

/**
 * 在进程内把分组规范转换为类型声明文本。
 *
 * 与 `openapi-typescript` 命令行默认行为一致：先转换语法树，再拼接文件头注释。
 *
 * @param {object} document 分组 OpenAPI 规范
 * @returns {Promise<string>} 类型声明文件内容
 */
async function generateTypes(document) {
  return `${COMMENT_HEADER}${astToString(await openapiTS(document))}`
}

/**
 * 顺序导出各分组规范并生成对应类型文件。
 */
async function main() {
  const outputDirectory = resolve(packageDirectory, 'src/generated')
  await mkdir(outputDirectory, { recursive: true })

  for (const group of groups) {
    const document = await fetchDocument(group)
    const documentPath = resolve(outputDirectory, `${group}.openapi.json`)
    await writeFile(documentPath, `${JSON.stringify(document, null, 2)}\n`, 'utf8')
    await writeFile(resolve(outputDirectory, `${group}.ts`), await generateTypes(document), 'utf8')
    console.log(`已生成 ${group} 分组类型：src/generated/${group}.ts`)
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error)
  process.exitCode = 1
})
