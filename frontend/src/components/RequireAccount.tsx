import { Link, Navigate, Outlet, useLocation } from 'react-router-dom'
import { useCurrentUser } from '@/hooks/useCurrentUser'
import { ErrorState, LoadingState } from './QueryState'

export function RequireAccount({ adopterOnly = false }: { adopterOnly?: boolean }) {
  const user = useCurrentUser()
  const location = useLocation()
  if (user.isPending) return <LoadingState />
  if (user.isError) return <ErrorState error={user.error} retry={() => void user.refetch()} />
  if (!user.data) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (adopterOnly && user.data.role !== 'ADOPTER') return <div className="empty-state">
    <h1>Adopter account required</h1><p>Staff review applications in the staff workspace.</p><Link to="/staff">Open staff review →</Link>
  </div>
  return <Outlet />
}
