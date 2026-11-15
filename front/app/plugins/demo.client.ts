import { installDemoFetch, isDemo } from '~/demo/demo'

export default defineNuxtPlugin(() => {
  if (isDemo()) {
    installDemoFetch()
  }
})
