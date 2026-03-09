import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/cp': {
        target: 'http://localhost:8181',
        changeOrigin: true
      },
      '/ih': {
        target: 'http://localhost:8183',
        changeOrigin: true
      },
      '/is': {
        target: 'http://localhost:8184',
        changeOrigin: true
      },
      '/fc': {
        target: 'http://localhost:8185',
        changeOrigin: true
      },
      '/op': {
        target: 'http://localhost:8186',
        changeOrigin: true
      },
      '/dp1': {
        target: 'http://localhost:8182',
        changeOrigin: true
      },
      '/dp2': {
        target: 'http://localhost:8187',
        changeOrigin: true
      }
    }
  }
})
