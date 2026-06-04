export const AUTH_TOKEN_STORAGE_KEY = 'aigc-eval-satoken'
export const AUTH_TOKEN_HEADER_NAME = 'satoken'

export function getAuthToken() {
  return localStorage.getItem(AUTH_TOKEN_STORAGE_KEY) || ''
}

export function setAuthToken(token: string) {
  localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token)
}

export function clearAuthToken() {
  localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
}

export function isAuthenticated() {
  return Boolean(getAuthToken())
}
