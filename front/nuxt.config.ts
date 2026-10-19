// FleetPilot web portal. A single-page application (the data is private, behind Keycloak) that installs as a PWA,
// including on iPhone where there is no native app.
export default defineNuxtConfig({
  compatibilityDate: '2026-09-01',
  ssr: false,
  devtools: { enabled: false },

  // The PWA module relies on virtual modules that the unit test runner cannot load: it is left out under Vitest.
  modules: ['@pinia/nuxt', '@nuxtjs/i18n', ...(process.env.VITEST ? [] : ['@vite-pwa/nuxt'])],

  // Components are named after their file (UiDataTable, FleetBadge...), whatever their folder.
  components: [{ path: '~/components', pathPrefix: false }],

  css: ['@fontsource-variable/inter', '~/assets/css/tokens.css', '~/assets/css/base.css'],

  app: {
    // Pages come in with a short fade (assets/css/base.css, section "motion")
    pageTransition: { name: 'page', mode: 'out-in' },
    head: {
      title: 'FleetPilot',
      htmlAttrs: { lang: 'fr' },
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1, viewport-fit=cover' },
        { name: 'description', content: 'FleetPilot : gestion de flotte, devis garages, véhicules connectés et transition énergétique.' },
        { name: 'theme-color', content: '#0f766e' },
        { name: 'apple-mobile-web-app-capable', content: 'yes' },
        { name: 'apple-mobile-web-app-status-bar-style', content: 'default' },
      ],
      link: [
        { rel: 'icon', type: 'image/svg+xml', href: '/favicon.svg' },
        { rel: 'apple-touch-icon', href: '/apple-touch-icon-180x180.png' },
      ],
    },
  },

  runtimeConfig: {
    public: {
      // Overridden at build time for other environments (NUXT_PUBLIC_OIDC_AUTHORITY...).
      oidcAuthority: 'http://localhost:8180/realms/fleetpilot',
      oidcClientId: 'fleetpilot-portal',
      graphqlUrl: '/graphql',
      // Dashboards of the observability profile (docker compose --profile observability up -d)
      kibanaUrl: 'http://localhost:5601/app/dashboards',
      // Lessor extranet (legacy WAR behind Apache), where the lessor accounts work
      extranetUrl: 'http://localhost:8095/extranet',
    },
  },

  // In development the GraphQL gateway runs on its own port; Nginx does the same routing in Docker.
  nitro: {
    devProxy: {
      '/graphql': { target: 'http://localhost:8082/graphql', changeOrigin: true },
      // REST endpoints used directly by the portal (CSV upload, report download); same routing as Nginx
      '/api/v1/reports': { target: 'http://localhost:8085/api/v1/reports', changeOrigin: true },
      '/api/v1/telemetry': { target: 'http://localhost:8086/api/v1/telemetry', changeOrigin: true },
      '/api/v1/alerts': { target: 'http://localhost:8086/api/v1/alerts', changeOrigin: true },
      '/api/v1/integration': { target: 'http://localhost:8088/api/v1/integration', changeOrigin: true },
      '/api/v1/fuel': { target: 'http://localhost:8088/api/v1/fuel', changeOrigin: true },
      '/api/v1/energy': { target: 'http://localhost:8090/api/v1/energy', changeOrigin: true },
      '/api': { target: 'http://localhost:8081/api', changeOrigin: true },
    },
  },

  i18n: {
    strategy: 'no_prefix',
    defaultLocale: 'fr',
    langDir: 'locales',
    locales: [
      { code: 'fr', language: 'fr-FR', name: 'Français', file: 'fr.json' },
      { code: 'en', language: 'en-GB', name: 'English', file: 'en.json' },
    ],
    detectBrowserLanguage: { useCookie: true, cookieKey: 'fleetpilot_locale', fallbackLocale: 'fr' },
  },

  pwa: {
    registerType: 'autoUpdate',
    manifest: {
      name: 'FleetPilot',
      short_name: 'FleetPilot',
      description: 'Gestion de flotte : véhicules, échéances, devis garages, véhicules connectés.',
      lang: 'fr',
      theme_color: '#0f766e',
      background_color: '#f5f6f8',
      display: 'standalone',
      start_url: '/',
      icons: [
        { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
        { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
        { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
        { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
      ],
    },
    workbox: {
      navigateFallback: '/',
      // The application shell works offline; the data of pages already visited is kept by useGraphql().
      globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
      // Opens the driver app on the notifications when a phone notification is tapped
      importScripts: ['/sw-notifications.js'],
    },
    client: { installPrompt: true },
    devOptions: { enabled: false },
  },

  typescript: { strict: true },
})
