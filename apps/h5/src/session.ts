import { defineStore } from 'pinia'

export type ThemeId = 'v1' | 'v2' | 'v3' | 'v4' | 'v5'
export type FontId = 'sm' | 'md' | 'lg'

const THEMES: ThemeId[] = ['v1', 'v2', 'v3', 'v4', 'v5']
const FONTS: FontId[] = ['sm', 'md', 'lg']

function readTheme(): ThemeId {
  const value = localStorage.getItem('theme')
  return THEMES.includes(value as ThemeId) ? (value as ThemeId) : 'v1'
}

function readFont(): FontId {
  const value = localStorage.getItem('font')
  return FONTS.includes(value as FontId) ? (value as FontId) : 'md'
}

export function tokenPayload(token: string): Record<string, unknown> {
  const part = token.split('.')[1]
  if (!part) return {}
  try {
    const padded = part.replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(padded)) as Record<string, unknown>
  } catch {
    return {}
  }
}

export function isGodView(token: string): boolean {
  return tokenPayload(token).gv === 1
}

let toastTimer = 0

export const useSession = defineStore('session', {
  state: () => ({
    token: localStorage.getItem('accessToken') || '',
    theme: readTheme(),
    font: readFont(),
    inviteCode: sessionStorage.getItem('inviteCode') || '',
    hideInvite: false,
    toast: '',
  }),
  getters: {
    godView: (state) => isGodView(state.token),
    userId: (state) => String(tokenPayload(state.token).sub || ''),
  },
  actions: {
    applyAppearance() {
      document.documentElement.dataset.theme = this.theme
      document.documentElement.dataset.font = this.font
    },
    setToken(token: string) {
      this.token = token
      if (token) localStorage.setItem('accessToken', token)
      else localStorage.removeItem('accessToken')
    },
    setTheme(theme: ThemeId) {
      this.theme = theme
      localStorage.setItem('theme', theme)
      this.applyAppearance()
    },
    setFont(font: FontId) {
      this.font = font
      localStorage.setItem('font', font)
      this.applyAppearance()
    },
    keepInvite(code: string) {
      this.inviteCode = code
      if (code) sessionStorage.setItem('inviteCode', code)
      else sessionStorage.removeItem('inviteCode')
    },
    notify(text: string) {
      this.toast = text
      window.clearTimeout(toastTimer)
      toastTimer = window.setTimeout(() => {
        this.toast = ''
      }, 2000)
    },
    logout() {
      this.setToken('')
      localStorage.removeItem('h5.chat')
    },
  },
})
