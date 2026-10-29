import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div></div>' } },
    { path: '/i/:inviteCode', component: { template: '<div></div>' } },
    { path: '/download', component: { template: '<div></div>' } },
  ],
})

createApp(App).use(router).mount('#app')
