const TOKEN_KEY = 'token'
const USER_KEY = 'user'

function cleanupLegacyLocalStorage() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getStoredToken() {
  const sessionToken = sessionStorage.getItem(TOKEN_KEY)
  if (sessionToken) return sessionToken

  const legacyToken = localStorage.getItem(TOKEN_KEY)
  const legacyUser = localStorage.getItem(USER_KEY)
  if (legacyToken) {
    sessionStorage.setItem(TOKEN_KEY, legacyToken)
    if (legacyUser) sessionStorage.setItem(USER_KEY, legacyUser)
    cleanupLegacyLocalStorage()
  }
  return legacyToken || ''
}

export function getStoredUser() {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    sessionStorage.removeItem(USER_KEY)
    return null
  }
}

export function setStoredAuth(token, user) {
  sessionStorage.setItem(TOKEN_KEY, token)
  sessionStorage.setItem(USER_KEY, JSON.stringify(user))
  cleanupLegacyLocalStorage()
}

export function clearStoredAuth() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
  cleanupLegacyLocalStorage()
}
