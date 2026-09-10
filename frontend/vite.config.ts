import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  server: {
    host: '127.0.0.1',
    proxy: {
      '/api': { target: process.env.PAWTRACK_API_TARGET || 'http://127.0.0.1:9090', changeOrigin: true },
      '/uploads': { target: process.env.PAWTRACK_API_TARGET || 'http://127.0.0.1:9090', changeOrigin: true },
    },
  },
  plugins: [tailwindcss(), react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
