import { createContext, ReactNode, useContext, useMemo, useState } from 'react'
import { AuthResponse, User } from '../types'
import { api } from '../lib/api'

interface AuthContextValue {
  user: User | null
  login: (email: string, password: string) => Promise<void>
  signup: (name: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function storedUser(): User | null {
  const value = localStorage.getItem('kp_user')
  if (!value) return null
  try { return JSON.parse(value) as User } catch { return null }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(storedUser)

  const accept = (response: AuthResponse) => {
    localStorage.setItem('kp_token', response.token)
    localStorage.setItem('kp_user', JSON.stringify(response.user))
    setUser(response.user)
  }

  const value = useMemo<AuthContextValue>(() => ({
    user,
    login: async (email, password) => accept((await api.post<AuthResponse>('/auth/login', { email, password })).data),
    signup: async (name, email, password) => accept((await api.post<AuthResponse>('/auth/signup', { name, email, password })).data),
    logout: () => {
      localStorage.removeItem('kp_token')
      localStorage.removeItem('kp_user')
      setUser(null)
    },
  }), [user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

