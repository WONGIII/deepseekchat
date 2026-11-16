import { create } from 'zustand'
import { clearNetworkSettings } from './settings.js'

export type AuthUser = {
  username: string
}

type AuthState = {
  status: 'loading' | 'anonymous' | 'authenticated' | 'error'
  user: AuthUser | null
  error: string | null
}

export const useAuth = create<AuthState>(() => ({
  status: 'loading',
  user: null,
  error: null,
}))

const readError = async (response: Response) => {
  try {
    const body = (await response.json()) as { error?: unknown }
    return typeof body.error === 'string' ? body.error : `HTTP ${response.status}`
  } catch {
    return `HTTP ${response.status}`
  }
}

const credentials = { credentials: 'same-origin' as RequestCredentials }

export const loadCurrentUser = async () => {
  useAuth.setState({ status: 'loading', error: null })
  try {
    const response = await fetch('/api/auth/me', credentials)
    if (response.status === 401) {
      useAuth.setState({ status: 'anonymous', user: null, error: null })
      return
    }
    if (!response.ok) {
      throw new Error(await readError(response))
    }
    const user = (await response.json()) as AuthUser
    useAuth.setState({ status: 'authenticated', user, error: null })
  } catch (error) {
    useAuth.setState({
      status: 'error',
      user: null,
      error: error instanceof Error ? error.message : String(error),
    })
  }
}

const submit = async (path: 'login' | 'register', username: string, password: string) => {
  const response = await fetch(`/api/auth/${path}`, {
    ...credentials,
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  const user = (await response.json()) as AuthUser
  useAuth.setState({ status: 'authenticated', user, error: null })
}

export const login = (username: string, password: string) =>
  submit('login', username, password)

export const register = (username: string, password: string) =>
  submit('register', username, password)

export const logout = async () => {
  clearNetworkSettings()
  await fetch('/api/auth/logout', { ...credentials, method: 'POST' }).catch(() => {})
  useAuth.setState({ status: 'anonymous', user: null, error: null })
}
