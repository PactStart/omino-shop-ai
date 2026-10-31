import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5183,
    proxy: {
      '/api/admin': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true,
      },
    },
  },
})
