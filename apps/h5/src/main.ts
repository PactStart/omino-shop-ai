import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './style.css'
import MallHome from './views/MallHome.vue'
import MallSearch from './views/MallSearch.vue'
import MallWeb from './views/MallWeb.vue'
import PlaceholderPage from './views/PlaceholderPage.vue'

const incoming = new URLSearchParams(window.location.search)
const handedToken = incoming.get('access_token')
if (handedToken) {
  localStorage.setItem('accessToken', handedToken)
  incoming.delete('access_token')
  const rest = incoming.toString()
  const next = `${window.location.pathname}${rest ? `?${rest}` : ''}${window.location.hash}`
  window.history.replaceState(null, '', next)
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/chat', component: PlaceholderPage, props: { title: '对话' } },
    { path: '/mall', component: MallHome },
    { path: '/mall/search', component: MallSearch },
    { path: '/mall/web', component: MallWeb },
    { path: '/orders', component: PlaceholderPage, props: { title: '订单' } },
    { path: '/me', component: PlaceholderPage, props: { title: '我的' } },
    { path: '/me/invite', component: PlaceholderPage, props: { title: '邀请' } },
    { path: '/me/credits', component: PlaceholderPage, props: { title: '积分' } },
    { path: '/goods/:goodsId', component: PlaceholderPage, props: { title: '商品详情' } },
  ],
})

createApp(App).use(createPinia()).use(router).mount('#app')
