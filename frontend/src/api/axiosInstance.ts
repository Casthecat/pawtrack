import axios from 'axios'

export const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000,
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
