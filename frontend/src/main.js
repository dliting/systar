import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/layout.scss'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import hasPermi from './directives/hasPermi'
import modalPlugin from './plugins/modal'
import { parseTime } from '@/utils/formatters'
import Pagination from '@/components/Pagination'
import RightToolbar from '@/components/RightToolbar'

const app = createApp(App)
app.use(createPinia())
app.use(ElementPlus)
app.use(router)
app.use(modalPlugin)
app.config.globalProperties.parseTime = parseTime
app.directive('hasPermi', hasPermi)
app.component('Pagination', Pagination)
app.component('RightToolbar', RightToolbar)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.mount('#app')
