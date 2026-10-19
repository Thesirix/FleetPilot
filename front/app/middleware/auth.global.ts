import { currentUser, login } from '~/utils/auth'

/** Pages a garage may open: its space, its quotes and the reference tariff. */
const GARAGE_PAGES = ['/espace-garage', '/devis/', '/tarif']

/**
 * Every page requires a signed-in user. Drivers and garages are sent to their own space; fleet managers to the fleet
 * pages.
 * The services enforce the same rules on their side: hiding a link is a convenience, never the protection.
 */
export default defineNuxtRouteMiddleware(async (to) => {
  // Unit tests mount components without a Keycloak session
  if (import.meta.test || to.path.startsWith('/auth/')) {
    return
  }
  if (!(await currentUser())) {
    await login(to.fullPath)
    return abortNavigation()
  }
  const session = useSessionStore()
  if (!session.viewer) {
    await session.load()
  }
  // An account without a space in the portal (a lessor) is told where to go instead of meeting refusals
  if (!session.hasPortal) {
    return to.path === '/acces' ? undefined : navigateTo('/acces')
  }
  if (to.path === '/acces') {
    return navigateTo('/')
  }
  const driverSpace = to.path.startsWith('/conducteur') && !to.path.startsWith('/conducteurs')
  if (session.isDriver && !session.isManager && !driverSpace) {
    return navigateTo('/conducteur')
  }
  if (!session.isDriver && driverSpace) {
    return navigateTo('/')
  }
  if (session.isGarage && !session.isManager && !GARAGE_PAGES.some(page => to.path.startsWith(page))) {
    return navigateTo('/espace-garage')
  }
  if (!session.isGarage && to.path.startsWith('/espace-garage')) {
    return navigateTo('/')
  }
})
