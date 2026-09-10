import { Link, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useCurrentUser } from '@/hooks/useCurrentUser'
import { ErrorState, LoadingState } from './QueryState'

export function RequireStaff() {
  const user = useCurrentUser()
  const location = useLocation()
  if (user.isPending) return <LoadingState />
  if (user.isError) return <ErrorState error={user.error} retry={() => void user.refetch()} />
  if (!user.data) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.data.role !== 'STAFF') return <div className="empty-state" role="alert">
    <h1>Staff access required</h1><p>This account can browse cats, but cannot access the staff workspace.</p>
    <Link className="button secondary" to="/">Meet the cats</Link>
  </div>
  return <Outlet />
}
