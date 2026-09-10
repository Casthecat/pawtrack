import { axiosInstance, clearCsrf, initializeCsrf } from './axiosInstance'
import type { CurrentUser } from './types'
import axios from 'axios'

export const authApi = {
  csrf: initializeCsrf,
  me: async (): Promise<CurrentUser | null> => {
    try { return (await axiosInstance.get<CurrentUser>('/api/auth/me')).data }
    catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) return null
      throw error
    }
  },
  login: async (input: { email: string; password: string }) => {
    clearCsrf()
    const user = (await axiosInstance.post<CurrentUser>('/api/auth/login', new URLSearchParams(input))).data
    clearCsrf()
    await initializeCsrf()
    return user
  },
  logout: async () => {
    await axiosInstance.post('/api/auth/logout')
    clearCsrf()
  },
}
