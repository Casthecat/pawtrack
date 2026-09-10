import { useMutation, useQueryClient } from '@tanstack/react-query'
import { NavLink, useNavigate } from 'react-router-dom'
import { authApi } from '@/api/auth'
import { apiError } from '@/api/axiosInstance'
import { currentUserKey, useCurrentUser } from '@/hooks/useCurrentUser'

export function AccountNavigation() {
  const user = useCurrentUser()
  const cache = useQueryClient()
  const navigate = useNavigate()
  const logout = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      cache.removeQueries({ predicate: query => query.queryKey[0] !== 'current-user' })
      cache.getMutationCache().clear()
      cache.setQueryData(currentUserKey, null)
      navigate('/', { replace: true })
    },
  })
  return <>
    {user.data?.role === 'ADOPTER' && <NavLink to="/applications">My applications</NavLink>}
    {user.data?.role === 'STAFF' && <NavLink to="/staff">Staff workspace</NavLink>}
    {user.data ? <>
      <span className="account-label">{user.data.displayName} · {user.data.role === 'STAFF' ? 'Staff' : 'Adopter'}</span>
      <button className="text-button" disabled={logout.isPending} onClick={() => logout.mutate()}>{logout.isPending ? 'Signing out…' : 'Sign out'}</button>
    </> : user.isPending ? <span className="account-label">Checking account…</span> : user.isError
      ? <button className="text-button" onClick={() => void user.refetch()}>Retry account check</button>
      : <NavLink to="/login">Sign in</NavLink>}
    {logout.isError && <span className="form-error" role="alert">{apiError(logout.error)}</span>}
  </>
}
