import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import { router } from './router'

/**
 * 创建并挂载用户端应用。
 *
 * 用户端与管理端各自维护路由、状态与身份，令牌不混用。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
