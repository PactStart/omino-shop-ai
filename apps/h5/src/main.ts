import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './style.css'
import { useSession } from './session'
import SplashPage from './views/SplashPage.vue'
import LoginPage from './views/LoginPage.vue'
import LoginPhonePage from './views/LoginPhonePage.vue'
import ForceUpdatePage from './views/ForceUpdatePage.vue'
import ChatPage from './views/ChatPage.vue'
import OrdersPage from './views/OrdersPage.vue'
import MePage from './views/MePage.vue'
import GoodsDetailPage from './views/GoodsDetailPage.vue'
import CollectionPage from './views/CollectionPage.vue'
import GoodsListPage from './views/GoodsListPage.vue'
import ProfilePage from './views/ProfilePage.vue'
import CreditsPage from './views/CreditsPage.vue'
import CreditPackagesPage from './views/CreditPackagesPage.vue'
import CreditLedgerPage from './views/CreditLedgerPage.vue'
import CreditOrderPage from './views/CreditOrderPage.vue'
import WalletPage from './views/WalletPage.vue'
import WithdrawPage from './views/WithdrawPage.vue'
import WithdrawRecordsPage from './views/WithdrawRecordsPage.vue'
import AuthPage from './views/AuthPage.vue'
import InvitePage from './views/InvitePage.vue'
import TicketsPage from './views/TicketsPage.vue'
import InboxPage from './views/InboxPage.vue'
import SettingsPage from './views/SettingsPage.vue'
import DisplayPage from './views/DisplayPage.vue'
import PushPage from './views/PushPage.vue'
import AboutPage from './views/AboutPage.vue'

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
    { path: '/', component: SplashPage, meta: { public: true } },
    { path: '/login', component: LoginPage, meta: { public: true } },
    { path: '/login/phone', component: LoginPhonePage, meta: { public: true } },
    { path: '/update', component: ForceUpdatePage, meta: { public: true } },
    { path: '/chat', component: ChatPage },
    { path: '/orders', component: OrdersPage },
    { path: '/me', component: MePage },
    { path: '/me/profile', component: ProfilePage },
    { path: '/me/history', component: GoodsListPage, props: { kind: 'history' } },
    { path: '/me/favorites', component: GoodsListPage, props: { kind: 'favorites' } },
    { path: '/me/credits', component: CreditsPage },
    { path: '/me/credits/packages', component: CreditPackagesPage },
    { path: '/me/credits/ledger', component: CreditLedgerPage },
    { path: '/me/credits/orders/:id', component: CreditOrderPage },
    { path: '/me/wallet', component: WalletPage },
    { path: '/me/wallet/withdraw', component: WithdrawPage },
    { path: '/me/wallet/withdrawals', component: WithdrawRecordsPage },
    { path: '/me/auth', component: AuthPage },
    { path: '/me/invite', component: InvitePage },
    { path: '/me/tickets', component: TicketsPage },
    { path: '/me/tickets/:id', component: TicketsPage },
    { path: '/me/inbox', component: InboxPage },
    { path: '/me/settings', component: SettingsPage },
    { path: '/me/settings/display', component: DisplayPage },
    { path: '/me/settings/push', component: PushPage },
    { path: '/me/settings/about', component: AboutPage },
    { path: '/goods/collections/:id', component: CollectionPage },
    { path: '/goods/:goodsId', component: GoodsDetailPage },
    { path: '/mall/:pathMatch(.*)*', redirect: '/chat' },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('accessToken')
  if (!token && !to.meta.public) return '/login'
  if (token && (to.path === '/login' || to.path === '/login/phone')) return '/chat'
  return true
})

const pinia = createPinia()
const app = createApp(App)
app.use(pinia).use(router)
useSession(pinia).applyAppearance()
app.mount('#app')
