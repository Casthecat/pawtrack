import { Link, useLocation, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, ArrowLeft, RefreshCw } from 'lucide-react'
import { api } from '@/api/pawtrack'
import { StatusBadge } from '@/components/StatusBadge'
import { LoadingState, ErrorState } from '@/components/QueryState'

export function ApplicationPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const query = useQuery({ queryKey: ['application', id], queryFn: () => api.application(id), refetchInterval: 10000 })
  if (query.isPending) return <LoadingState />
  if (query.isError) return <ErrorState error={query.error} retry={() => void query.refetch()} />
  const app = query.data
  return <div className="receipt">
    <CheckCircle2 size={44} strokeWidth={1.4} className="receipt-icon" />
    <p className="eyebrow">APPLICATION #{app.id}</p>
    <h1>{location.state?.submitted && app.status === 'PENDING' ? 'Your introduction is in.' : 'Your adoption journey.'}</h1>
    <p>For {app.catName}, from {app.adopterName}.</p><StatusBadge status={app.status} />
    <div className="receipt-message">{app.status === 'PENDING' ? 'The shelter team will review your application. Save this page to follow its progress.' : app.status === 'APPROVED' ? 'Your application is approved. This demo completes the adoption here; an actual shelter would arrange the next steps with you.' : 'This application was not approved. Another application may have been selected, or the shelter made a different decision.'}</div>
    <dl><div><dt>Submitted</dt><dd>{new Date(app.createdAt).toLocaleString()}</dd></div><div><dt>Last updated</dt><dd>{new Date(app.updatedAt).toLocaleString()}</dd></div></dl>
    <button className="text-button" disabled={query.isFetching} onClick={() => void query.refetch()}><RefreshCw size={15} />{query.isFetching ? 'Checking…' : 'Refresh status'}</button>
    <div className="receipt-links"><Link className="button secondary" to="/"><ArrowLeft size={16} />Keep exploring</Link><Link to="/staff">Open demo staff review →</Link></div>
  </div>
}
