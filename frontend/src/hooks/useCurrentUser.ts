import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/api/auth'

export const currentUserKey = ['current-user'] as const
export function useCurrentUser() {
  return useQuery({ queryKey: currentUserKey, queryFn: authApi.me, retry: false, staleTime: 15000 })
}
