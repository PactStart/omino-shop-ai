import { createRouter, createWebHistory } from 'vue-router'
import { apiGet, goLogin, isUnauthorized } from '../api/http'
import AdminShell from '../layouts/AdminShell.vue'
import LoginCallback from '../views/login-callback.vue'
import Dashboard from '../views/dashboard.vue'
import Orders from '../views/orders.vue'
import OrderSync from '../views/order-sync.vue'
import OrderDetail from '../views/order-detail.vue'
import Users from '../views/users.vue'
import UserDetail from '../views/user-detail.vue'
import GodView from '../views/god-view.vue'
import Conversations from '../views/conversations.vue'
import ConversationDetail from '../views/conversation-detail.vue'
import Tickets from '../views/tickets.vue'
import TicketDetail from '../views/ticket-detail.vue'
import Withdrawals from '../views/withdrawals.vue'
import CreditPackages from '../views/credit-packages.vue'
import CreditOrders from '../views/credit-orders.vue'
import CreditLedger from '../views/credit-ledger.vue'
import InvitePosters from '../views/invite-posters.vue'
import InvitePosterEdit from '../views/invite-poster-edit.vue'
import Releases from '../views/releases.vue'
import RuntimeConfigs from '../views/runtime-configs.vue'
import Announcements from '../views/announcements.vue'
import Inbox from '../views/inbox.vue'
import ChatSuggestions from '../views/chat-suggestions.vue'
import AgentPrompts from '../views/agent-prompts.vue'
import LlmModels from '../views/llm-models.vue'
import Kb from '../views/kb.vue'
import Audit from '../views/audit.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login/callback', name: 'loginCallback', component: LoginCallback },
    {
      path: '/',
      component: AdminShell,
      children: [
        { path: '', name: 'dashboard', component: Dashboard },
        { path: 'orders', name: 'orders', component: Orders },
        { path: 'orders/sync', name: 'orderSync', component: OrderSync },
        { path: 'orders/:id', name: 'orderDetail', component: OrderDetail },
        { path: 'users', name: 'users', component: Users },
        { path: 'users/:id', name: 'userDetail', component: UserDetail },
        { path: 'god-view', name: 'godView', component: GodView },
        { path: 'conversations', name: 'conversations', component: Conversations },
        { path: 'conversations/:id', name: 'conversationDetail', component: ConversationDetail },
        { path: 'tickets', name: 'tickets', component: Tickets },
        { path: 'tickets/:id', name: 'ticketDetail', component: TicketDetail },
        { path: 'withdrawals', name: 'withdrawals', component: Withdrawals },
        { path: 'credits/packages', name: 'creditPackages', component: CreditPackages },
        { path: 'credits/orders', name: 'creditOrders', component: CreditOrders },
        { path: 'credits/ledger', name: 'creditLedger', component: CreditLedger },
        { path: 'invite-posters', name: 'invitePosters', component: InvitePosters },
        { path: 'invite-posters/:id', name: 'invitePosterEdit', component: InvitePosterEdit },
        { path: 'releases', name: 'releases', component: Releases },
        { path: 'runtime-configs', name: 'runtimeConfigs', component: RuntimeConfigs },
        { path: 'announcements', name: 'announcements', component: Announcements },
        { path: 'inbox', name: 'inbox', component: Inbox },
        { path: 'chat-suggestions', name: 'chatSuggestions', component: ChatSuggestions },
        { path: 'agent-prompts', name: 'agentPrompts', component: AgentPrompts },
        { path: 'llm-models', name: 'llmModels', component: LlmModels },
        { path: 'kb', name: 'kb', component: Kb },
        { path: 'audit', name: 'audit', component: Audit },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  if (to.name === 'loginCallback') return true
  try {
    await apiGet('/v1/dashboard')
    return true
  } catch (error) {
    if (isUnauthorized(error)) {
      goLogin()
      return false
    }
    return true
  }
})
