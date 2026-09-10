import { axiosInstance } from './axiosInstance'
import type { Application, ApplicationInput, AdoptionStatus, Cat, CatDetail, Alert, HealthTimeline, AlertResolutionInput, AlertResolutionResult } from './types'

export const api = {
  openAlerts: async () => (await axiosInstance.get<Alert[]>('/api/alerts', { params: { status: 'OPEN' } })).data,
  healthTimeline: async (catId: number) => (await axiosInstance.get<HealthTimeline>(`/api/cats/${catId}/health-timeline`)).data,
  resolveAlert: async (alertId: number, input: AlertResolutionInput) => (await axiosInstance.patch<AlertResolutionResult>(`/api/alerts/${alertId}/resolve`, input)).data,
  cats: async () => (await axiosInstance.get<Cat[]>('/api/cats')).data,
  cat: async (id: string) => (await axiosInstance.get<CatDetail>(`/api/cats/${id}/dashboard`)).data,
  createCat: async (name: string) => (await axiosInstance.post<Cat>('/api/cats', { name })).data,
  myApplications: async () => (await axiosInstance.get<Application[]>('/api/me/adoptions')).data,
  apply: async (input: ApplicationInput) => (await axiosInstance.post<Application>('/api/adoptions', input)).data,
  applications: async (status?: AdoptionStatus) => (await axiosInstance.get<Application[]>('/api/adoptions', { params: { status } })).data,
  application: async (id: string) => (await axiosInstance.get<Application>(`/api/adoptions/${id}`)).data,
  review: async (id: number, decision: 'approve' | 'reject') => (await axiosInstance.patch<Application>(`/api/adoptions/${id}/${decision}`)).data,
}
