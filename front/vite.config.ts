import Components from 'unplugin-vue-components/vite'
import Vue from '@vitejs/plugin-vue'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import AutoImport from 'unplugin-auto-import/vite'
import { defineConfig } from 'vite'

const isMock = process.env.VITE_APP_ENV === 'mock'

export default defineConfig({
  plugins: [
    Vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [
        AntDesignVueResolver({
          importStyle: false,
        }),
      ],
      dts: 'src/components.d.ts',
    }),
  ],
  server: {
    port: 5173,
    proxy: isMock ? {} : {
      '/api/aigc-eval': {
        target: 'http://127.0.0.1:8800',
        changeOrigin: true,
      },
    },
  },
})
