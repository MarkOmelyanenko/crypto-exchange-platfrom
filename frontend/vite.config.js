import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
      "/actuator": "http://localhost:8080",
    },
  },
  resolve: {
    alias: {
      '@crypto-icons': resolve(__dirname, 'node_modules/cryptocurrency-icons/svg/color'),
    },
  },
});
