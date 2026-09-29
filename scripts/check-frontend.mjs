import assert from 'node:assert/strict'
import { once } from 'node:events'
import { writeFile, unlink } from 'node:fs/promises'
import { createServer as createHttpServer } from 'node:http'
import { createRequire } from 'node:module'
import { fileURLToPath, pathToFileURL } from 'node:url'

// 只影响本次检查子进程，避免终端变量覆盖用于验证的临时环境文件。
for (const name of ['VITE_DEV_PORT', 'VITE_API_PROXY_PREFIX', 'VITE_API_PROXY_TARGET']) {
  delete process.env[name]
}

const upstream = createHttpServer((_request, response) => {
  response.setHeader('Content-Type', 'application/json')
  response.end(JSON.stringify({ code: 0, message: '代理验证成功', data: null, traceId: 'm1' }))
})
upstream.listen(0, '127.0.0.1')
await once(upstream, 'listening')
const target = `http://127.0.0.1:${upstream.address().port}`

try {
  for (const app of ['admin']) {
    const directory = new URL(`../frontend/${app}/`, import.meta.url)
    const require = createRequire(new URL('package.json', directory))
    const { createServer } = await import(pathToFileURL(require.resolve('vite')).href)
    const mode = `m1-check-${process.pid}`
    const envFile = new URL(`.env.${mode}.local`, directory)
    // 排他创建，任何现有文件都不会被覆盖。
    await writeFile(envFile,
      `VITE_DEV_PORT=0\nVITE_API_PROXY_PREFIX=/m1-probe\nVITE_API_PROXY_TARGET=${target}\n`,
      { flag: 'wx' })
    let server
    try {
      server = await createServer({
        root: fileURLToPath(directory),
        configFile: fileURLToPath(new URL('vite.config.ts', directory)),
        mode,
        optimizeDeps: { noDiscovery: true, include: [] },
        server: { host: '127.0.0.1' },
      })
      assert.equal(server.config.server.port, 0, `${app} 应从环境文件读取端口`)
      assert.equal(server.config.server.proxy['/m1-probe'].target, target)
      assert.equal(server.config.server.strictPort, true)
      await server.listen()
      const address = `http://127.0.0.1:${server.httpServer.address().port}`
      for (const path of ['/', '/src/main.ts', '/src/views/HomeView.vue']) {
        const response = await fetch(address + path)
        assert.equal(response.status, 200, `${app} ${path} 必须成功加载`)
        assert.ok((await response.text()).length > 0)
      }
      const response = await fetch(`${address}/m1-probe`)
      assert.equal(response.status, 200)
      assert.equal((await response.json()).message, '代理验证成功')
      console.log(`${app}: 环境覆盖、页面与模块加载、真实 HTTP 代理通过`)
    } finally {
      try {
        await server?.close()
      } finally {
        await unlink(envFile)
      }
    }
  }
} finally {
  upstream.close()
  upstream.closeAllConnections()
}
