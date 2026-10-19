import { UserManager, WebStorageStateStore, type User } from 'oidc-client-ts'

let manager: UserManager | null = null

/**
 * The OpenID Connect client of the portal (authorization code + PKCE with Keycloak). The tokens stay in the session
 * storage of the tab; Keycloak's own session cookie signs the user back in silently when the tab is reopened.
 */
export function userManager(): UserManager {
  if (!manager) {
    const config = useRuntimeConfig().public
    const origin = window.location.origin
    manager = new UserManager({
      authority: config.oidcAuthority,
      client_id: config.oidcClientId,
      redirect_uri: `${origin}/auth/callback`,
      post_logout_redirect_uri: `${origin}/`,
      response_type: 'code',
      scope: 'openid profile email',
      automaticSilentRenew: true,
      userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    })
  }
  return manager
}

export async function currentUser(): Promise<User | null> {
  const user = await userManager().getUser()
  return user && !user.expired ? user : null
}

/** Sends the browser to the Keycloak login page, then back to the page the user wanted. */
export async function login(returnTo = window.location.pathname + window.location.search): Promise<void> {
  await userManager().signinRedirect({ state: { returnTo } })
}

export async function logout(): Promise<void> {
  const user = await userManager().getUser()
  await userManager().signoutRedirect({ id_token_hint: user?.id_token })
}

export async function accessToken(): Promise<string | null> {
  return (await currentUser())?.access_token ?? null
}
