import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import { router } from './router'

/**
 * 创建并挂载管理端应用。
 *
 * 管理端与用户端各自维护路由、状态与身份，不共享运行时实例。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
