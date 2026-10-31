export type AdminTheme = 'light' | 'dark'

const KEY = 'admin.theme'

/** 读出上次选择的主题，没有记录时用浅色。 */
export function readTheme(): AdminTheme {
  return localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light'
}

/**
 * 把主题写到文档根节点和本机，使 Element Plus 的 dark 变量生效。
 *
 * @param theme 浅色或深色
 */
export function applyTheme(theme: AdminTheme): void {
  document.documentElement.classList.toggle('dark', theme === 'dark')
  localStorage.setItem(KEY, theme)
}
