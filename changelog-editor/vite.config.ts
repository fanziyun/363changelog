import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'

// https://vite.dev/config/
export default defineConfig({
  // autoImport 只打包模板里真正用到的 Vuetify 组件与指令；
  // 原先在 plugins/vuetify.ts 里 `import * as components` 会把整个组件库塞进产物
  plugins: [vue(), vuetify({ autoImport: true })],
})
