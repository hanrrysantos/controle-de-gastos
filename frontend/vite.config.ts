import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    proxy: { '/api': { target: process.env.CDG_API_PROXY ?? 'http://localhost:8080', rewrite: (path) => path.replace(/^\/api/, '') } },
  },
})
