import { defineConfig, configDefaults } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
    extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
  },
  test: {
    globals: true,
    environment: 'jsdom',
    // e2e/*.spec.js are Playwright specs — vitest's default *.spec.js
    // collection would load them and Playwright's test() throws outside its runner
    exclude: [...configDefaults.exclude, 'e2e/**'],
    server: {
      deps: {
        inline: [/element-plus/]
      }
    }
  },
})
