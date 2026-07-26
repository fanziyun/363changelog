import { createVuetify } from 'vuetify'
import 'vuetify/styles'
import { md3 } from 'vuetify/blueprints'
import '@mdi/font/css/materialdesignicons.css'

// 组件与指令由 vite-plugin-vuetify 按需注入，见 vite.config.ts
export default createVuetify({
  blueprint: md3,
  icons: {
    defaultSet: 'mdi',
  },
})
