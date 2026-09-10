import { AlertCircle, LoaderCircle } from 'lucide-react'
import { apiError } from '@/api/axiosInstance'

export function LoadingState() {
  return <div className="empty-state" role="status"><LoaderCircle className="spin" size={26} /><p>Getting things ready…</p></div>
}
export function ErrorState({ error, retry }: { error: unknown; retry: () => void }) {
  return <div className="empty-state" role="alert"><AlertCircle size={28} /><h2>Unable to load this page</h2><p>{apiError(error)}</p><button className="button secondary" onClick={retry}>Try again</button></div>
}
