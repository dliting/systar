import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'fs'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'pid-file',
      configureServer(server) {
        const pidFile = path.resolve(__dirname, '../temp/frontend.pid')
        fs.mkdirSync(path.dirname(pidFile), { recursive: true })
        fs.writeFileSync(pidFile, String(process.pid))
        console.log(`[pid-file] PID ${process.pid} written to ${pidFile}`)
        server.httpServer.on('close', () => {
          try { fs.unlinkSync(pidFile) } catch {}
        })
      }
    }
  ],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
    extensions: ['.mjs', '.js', '.vue', '.json']
  },
  server: {
    port: 5173,
    host: true,
    open: true,
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
      '/ws':  { target: 'http://localhost:8081', ws: true, changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    rollupOptions: {
      output: {
        chunkFileNames: 'static/js/[name]-[hash].js',
        entryFileNames: 'static/js/[name]-[hash].js',
        assetFileNames: 'static/[ext]/[name]-[hash].[ext]'
      }
    }
  }
})
