import axios from 'axios'

export const API_BASE_URL = import.meta.env.VITE_API_URL || '/api'

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120_000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('kp_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && localStorage.getItem('kp_token')) {
      localStorage.removeItem('kp_token')
      localStorage.removeItem('kp_user')
      window.location.assign('/login')
    }
    return Promise.reject(error)
  },
)

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const correlationId = error.response?.data?.correlationId as string | undefined
    const withReference = (message: string) =>
      correlationId ? `${message} · Support ID: ${correlationId}` : message
    const fields = error.response?.data?.validationErrors as Record<string, string> | undefined
    if (fields && Object.keys(fields).length) {
      return withReference(Object.entries(fields).map(([field, message]) => `${field}: ${message}`).join(' · '))
    }
    if (error.response?.status === 429) {
      return withReference(error.response.data?.message
        || 'You have reached the request limit. Please wait before trying again.')
    }
    return withReference(error.response?.data?.message || error.message)
  }
  return error instanceof Error ? error.message : 'Something went wrong'
}

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}
