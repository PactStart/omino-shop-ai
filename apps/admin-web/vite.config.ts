import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    port: 5183,
    proxy: {
      '/api/admin': {
        target: 'http://127.0.0.1:8182',
        changeOrigin: true,
      },
    },
  },
})
