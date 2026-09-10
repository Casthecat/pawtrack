import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/api/pawtrack'
import { useCurrentUser } from '@/hooks/useCurrentUser'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { StatusBadge } from '@/components/StatusBadge'

export function MyApplicationsPage() {
  const user = useCurrentUser()
  const query = useQuery({ queryKey: ['my-applications', user.data?.id], queryFn: api.myApplications, staleTime: 0 })
  if (query.isPending) return <LoadingState />
  if (query.isError) return <ErrorState error={query.error} retry={() => void query.refetch()} />
  return <section className="my-applications-page"><div className="review-heading"><div><p className="eyebrow">YOUR ADOPTION JOURNEY</p><h1>My applications</h1>
    <p>Follow your applications, newest first.</p></div></div>
    {query.data.length === 0 ? <div className="empty-state"><h2>No applications yet</h2><Link to="/">Meet the cats →</Link></div> :
      <ul className="my-applications" aria-label="My applications, newest first">{query.data.map(app => <li key={app.id} className="application-item">
        <div className="application-top"><h2>{app.catName}</h2><StatusBadge status={app.status} /></div><p>Submitted {new Date(app.createdAt).toLocaleString()}</p>
        <Link className="text-button" to={`/applications/${app.id}`}>View application #{app.id} →</Link>
      </li>)}</ul>}
  </section>
}
