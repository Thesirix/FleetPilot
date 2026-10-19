import { defineStore } from 'pinia'
import { graphql } from '~/utils/graphql'

export interface Viewer {
  username: string
  name: string | null
  email: string | null
  roles: string[]
  staff: boolean
  companyId: string | null
  driverId: string | null
  garageId: string | null
}

/** The connected user, loaded once from the GraphQL gateway after the login. */
export const useSessionStore = defineStore('session', {
  state: () => ({ viewer: null as Viewer | null }),

  getters: {
    isAdmin: state => state.viewer?.roles.includes('platform-admin') ?? false,
    isStaff: state => state.viewer?.staff ?? false,
    isManager: state => ['platform-admin', 'fleet-manager', 'client-manager'].some(r => state.viewer?.roles.includes(r)),
    isDriver: state => state.viewer?.roles.includes('driver') ?? false,
    isGarage: state => state.viewer?.roles.includes('garage') ?? false,
    /** A lessor works in the lessor extranet, not in the portal */
    isLessor: state => state.viewer?.roles.includes('api-supplier') ?? false,
    /** Whether the account has a space in the portal: fleet manager, driver or garage */
    hasPortal(): boolean {
      return this.isManager || this.isDriver || this.isGarage
    },
    displayName: state => state.viewer?.name || state.viewer?.username || '',
    initials(): string {
      return this.displayName.split(/[\s.@]+/).filter(Boolean).slice(0, 2).map(p => p[0]!.toUpperCase()).join('')
    },
    mainRole: state => state.viewer?.roles[0] ?? null,
  },

  actions: {
    async load() {
      const { data } = await graphql<{ viewer: Viewer }>(
        '{ viewer { username name email roles staff companyId driverId garageId } }', {}, { cacheKey: 'viewer' })
      this.viewer = data.viewer
    },
  },
})
