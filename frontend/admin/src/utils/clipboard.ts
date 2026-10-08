/**
 * 复制文本到剪贴板。
 *
 * 测试服务器通过 http 访问时浏览器不提供 navigator.clipboard（只在安全上下文可用），
 * 这时退回到临时文本框加 execCommand 的方式。
 *
 * @param text 要复制的文本
 * @returns 是否复制成功
 */
export async function copyText(text: string): Promise<boolean> {
  if (window.isSecureContext && navigator.clipboard) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 权限被拒绝时继续尝试兼容方式
    }
  }
  const input = document.createElement('textarea')
  input.value = text
  input.setAttribute('readonly', '')
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  try {
    return document.execCommand('copy')
  } finally {
    document.body.removeChild(input)
  }
}
