/**
 * 未认证跳转的注册点。
 *
 * 请求工厂不能直接依赖路由实例：路由会加载页面，页面会加载请求，直接互相引用会形成循环。
 * 因此这里只保留一个注册好的跳转函数，由路由入口在创建路由后注册。
 */

/**
 * 已注册的未认证跳转动作。
 */
let unauthorizedHandler: (() => void) | undefined

/**
 * 注册未认证时执行的跳转动作。
 *
 * @param handler 跳转动作，通常跳转到登录页
 */
export function registerUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

/**
 * 执行未认证跳转；未注册时不抛错，只清理会话。
 */
export function redirectToLogin(): void {
  unauthorizedHandler?.()
}
