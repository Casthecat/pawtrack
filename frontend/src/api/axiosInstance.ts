import axios from 'axios'
import { queryClient } from '@/hooks/queryClient'

export const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000,
})

type Csrf = { headerName: string; token: string }
let csrf: Csrf | null = null
let initializing: Promise<Csrf> | null = null

export function clearCsrf() { csrf = null; initializing = null }

export function initializeCsrf(): Promise<Csrf> {
  if (csrf) return Promise.resolve(csrf)
  if (initializing) return initializing
  const request = axiosInstance.get<Csrf>('/api/auth/csrf').then(response => {
    if (initializing === request) csrf = response.data
    return response.data
  }).finally(() => { if (initializing === request) initializing = null })
  initializing = request
  return request
}

// One mechanism covers every feature, including public submission from a signed-in user.
axiosInstance.interceptors.request.use(async config => {
  if (!['get', 'head', 'options', 'trace'].includes((config.method || 'get').toLowerCase())) {
    const token = await initializeCsrf()
    config.headers.set(token.headerName, token.token)
  }
  return config
})

axiosInstance.interceptors.response.use(response => response, error => {
  if (axios.isAxiosError(error) && error.response?.status === 401 && error.config?.url !== '/api/auth/login') {
    clearCsrf()
    queryClient.setQueryData(['current-user'], null)
    // Remove private cached data immediately when a session is no longer usable.
    queryClient.removeQueries({ predicate: query => ['applications', 'application', 'my-applications', 'alerts', 'health-timeline'].includes(String(query.queryKey[0])) })
  }
  if (axios.isAxiosError(error) && error.response?.status === 403) clearCsrf()
  // Never automatically replay a mutation: callers explicitly retry after refreshing credentials.
  return Promise.reject(error)
})

export function apiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) return 'We could not reach PawTrack. Check that the server is running, then try again.'
    const data = error.response.data
    if (data?.errors) return Object.entries(data.errors).map(([key, value]) => `${key}: ${value}`).join(' · ')
    return data?.message || 'Something went wrong. Please try again.'
  }
  return 'Something went wrong. Please try again.'
}

export function isConflict(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 409
}

export function imageSource(path: string | null): string | undefined {
  if (!path) return undefined
  if (/^https?:\/\//i.test(path)) return path
  return `${axiosInstance.defaults.baseURL}/${path.replace(/^\/+/, '')}`
}
