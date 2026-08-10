import {defineConfig} from 'vite'
import {resolve} from 'path'
import handlebars from 'vite-plugin-handlebars'

export default defineConfig({
  plugins: [
    handlebars({
      partialDirectory: resolve(__dirname, 'partials'),
      helpers: {
        year: () => new Date().getFullYear(),
      },
    }),
  ],

  build: {
    rolldownOptions: {
      input: {
        index: resolve(__dirname, 'index.html'),
        login: resolve(__dirname, 'pages/login.html'),
        register: resolve(__dirname, 'pages/register.html'),
        dashboard: resolve(__dirname, 'pages/dashboard.html'),
        users: resolve(__dirname, 'pages/users.html'),
        todo: resolve(__dirname, 'pages/todo.html'),
        wsinspect: resolve(__dirname, 'pages/ws-inspect.html'),
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  css: {
    preprocessorOptions: {
      scss: {
        quietDeps: true,
      },
    },
  },
})
