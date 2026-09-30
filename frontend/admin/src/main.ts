import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import { router } from './router'
import './styles/base.css'

/**
 * 创建并挂载后台管理应用。
 *
 * 路由、状态与身份令牌由本应用自己维护，公共请求包不保存运行时实例。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')
