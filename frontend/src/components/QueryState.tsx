import { AlertCircle, LoaderCircle } from 'lucide-react'
import axios from 'axios'
import { Link, useLocation } from 'react-router-dom'
import { apiError } from '@/api/axiosInstance'

export function LoadingState() {
  return <div className="empty-state" role="status"><LoaderCircle className="spin" size={26} /><p>Getting things ready…</p></div>
}
export function ErrorState({ error, retry }: { error: unknown; retry: () => void }) {
  const location = useLocation()
  if (axios.isAxiosError(error) && error.response?.status === 401) return <div className="empty-state" role="alert">
    <h2>Sign in required</h2><p>Your session is no longer available. Sign in to continue.</p>
    <Link className="button primary" to="/login" state={{ from: location.pathname }}>Sign in</Link>
  </div>
  if (axios.isAxiosError(error) && error.response?.status === 403) return <div className="empty-state" role="alert">
    <h2>Staff access required</h2><p>{apiError(error)}</p><Link to="/">Meet the cats</Link>
  </div>
  return <div className="empty-state" role="alert"><AlertCircle size={28} /><h2>Unable to load this page</h2><p>{apiError(error)}</p><button className="button secondary" onClick={retry}>Try again</button></div>
}
