/**
 * 管理端会话存储。
 *
 * 令牌只保存在本应用的 `sessionStorage`，键名包含项目标识；
 * 键名只用于本应用隔离，不作为服务端身份依据。存储不可用（如隐私模式）时按未登录处理。
 */

/**
 * 会话令牌的存储键名。
 */
export const SESSION_TOKEN_KEY = 'orange-forge:admin:token'

/**
 * 读取当前会话令牌。
 *
 * @returns 令牌；未登录或存储不可用时返回 undefined
 */
export function readSessionToken(): string | undefined {
  try {
    return window.sessionStorage.getItem(SESSION_TOKEN_KEY) ?? undefined
  } catch {
    return undefined
  }
}

/**
 * 保存会话令牌。
 *
 * @param token 登录接口返回的令牌
 */
export function writeSessionToken(token: string): void {
  try {
    window.sessionStorage.setItem(SESSION_TOKEN_KEY, token)
  } catch {
    // 存储不可用时保持未登录状态，不把令牌退回内存或 URL
  }
}

/**
 * 清除会话令牌。
 */
export function clearSessionToken(): void {
  try {
    window.sessionStorage.removeItem(SESSION_TOKEN_KEY)
  } catch {
    // 存储不可用时无需处理：令牌本来就没有落盘
  }
}
