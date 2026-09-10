import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, X, Plus, Inbox, RefreshCw } from 'lucide-react'
import { api } from '@/api/pawtrack'
import { apiError } from '@/api/axiosInstance'
import type { AdoptionStatus, Application } from '@/api/types'
import { StatusBadge } from '@/components/StatusBadge'
import { LoadingState, ErrorState } from '@/components/QueryState'

export function ReviewPage() {
  const cache = useQueryClient()
  const [filter, setFilter] = useState<AdoptionStatus | 'ALL'>('PENDING')
  const [decision, setDecision] = useState<{ app: Application; action: 'approve' | 'reject' } | null>(null)
  const [name, setName] = useState('')
  const [message, setMessage] = useState('')
  const query = useQuery({ queryKey: ['applications'], queryFn: () => api.applications(), refetchInterval: 15000 })
  const review = useMutation({
    mutationFn: ({ id, action }: { id: number; action: 'approve' | 'reject' }) => api.review(id, action),
    onSuccess: (_, variables) => { setDecision(null); setMessage(variables.action === 'approve' ? 'Application approved. The cat has found a home; other pending applications for this cat were declined.' : 'Application declined. The cat’s availability is unchanged.') },
    onSettled: async () => { await Promise.all([cache.invalidateQueries({ queryKey: ['applications'] }), cache.invalidateQueries({ queryKey: ['cats'] }), cache.invalidateQueries({ queryKey: ['cat'] }), cache.invalidateQueries({ queryKey: ['application'] })]) },
  })
  const create = useMutation({
    mutationFn: api.createCat,
    onSuccess: async cat => { setName(''); setMessage(`${cat.name} has joined the PawTrack family.`); await cache.invalidateQueries({ queryKey: ['cats'] }) },
  })
  const applications = query.data || []
  const visible = applications.filter(a => filter === 'ALL' || a.status === filter)
  function addCat(e: FormEvent) { e.preventDefault(); if (name.trim()) create.mutate(name.trim()) }
  return <>
    <div className="review-heading"><div><p className="eyebrow">SHELTER WORKSPACE</p><h1>Good homes start here.</h1><p>Review introductions and help each cat find the right next chapter.</p></div><span className="staff-tag">Staff demo</span></div>
    <div className="review-stats">{[['PENDING', 'Waiting for review'], ['APPROVED', 'Applications approved'], ['REJECTED', 'Applications declined']].map(([status, label]) => <div key={status}><span>{label}</span><strong>{query.isSuccess ? applications.filter(a => a.status === status).length : '—'}</strong></div>)}</div>
    <div className="review-layout"><section className="review-queue">
      <div className="queue-heading"><h2>Adoption applications</h2><button className="text-button" disabled={query.isFetching} onClick={() => void query.refetch()}><RefreshCw size={15} />Refresh</button></div>
      <div className="filter-group review-filters" aria-label="Filter applications">{(['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as const).map(s => <button key={s} aria-pressed={filter === s} onClick={() => { setFilter(s); setDecision(null); review.reset() }}>{s === 'ALL' ? 'All' : s === 'PENDING' ? 'Pending' : s === 'APPROVED' ? 'Approved' : 'Declined'}</button>)}</div>
      {message && <p className="success-message" role="status">{message}</p>}
      {review.isError && <p className="form-error" role="alert">{apiError(review.error)}</p>}
      {query.isPending ? <LoadingState /> : query.isError ? <ErrorState error={query.error} retry={() => void query.refetch()} /> : !visible.length ? <div className="empty-state"><Inbox size={30} /><h3>You're all caught up.</h3><p>No applications in this view. New introductions will appear here.</p></div> :
        <div className="application-list">{visible.map(app => <article className="application-item" key={app.id}>
          <div className="application-top"><div><Link to={`/cats/${app.catId}`} className="application-cat">{app.catName}</Link><p>Application #{app.id} · {new Date(app.createdAt).toLocaleDateString()}</p></div><StatusBadge status={app.status} /></div>
          <h3>{app.adopterName}</h3><p className="app-email">{app.adopterEmail}</p>{app.notes && <p className="app-notes">{app.notes}</p>}
          <div className="application-actions"><Link to={`/applications/${app.id}`}>View application →</Link>{app.status === 'PENDING' && <div><button className="button secondary small" disabled={review.isPending} onClick={() => { setDecision({ app, action: 'reject' }); review.reset(); setMessage('') }}><X size={15} />Decline</button><button className="button primary small" disabled={review.isPending} onClick={() => { setDecision({ app, action: 'approve' }); review.reset(); setMessage('') }}><Check size={15} />Approve</button></div>}</div>
          {decision?.app.id === app.id && app.status === 'PENDING' && <div className="decision-box"><p>{decision.action === 'approve' ? `Approve ${app.adopterName} for ${app.catName}? This marks the cat as adopted and declines other pending applications for this cat.` : `Decline ${app.adopterName}'s application? This decision is final for this application.`}</p><div><button className="button primary small" disabled={review.isPending} onClick={() => review.mutate({ id: app.id, action: decision.action })}>{review.isPending ? 'Saving…' : 'Confirm decision'}</button><button className="text-button" disabled={review.isPending} onClick={() => setDecision(null)}>Cancel</button></div></div>}
        </article>)}</div>}
    </section><aside className="review-aside"><form onSubmit={addCat}><span className="aside-icon"><Plus size={21} /></span><h2>A new arrival?</h2><p>Add a cat to start their story. New arrivals are ready for introductions by default in this demo.</p><label>Cat's name<input required maxLength={100} value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Mochi" /></label><button className="button primary full-width" disabled={create.isPending || !name.trim()}>{create.isPending ? 'Adding…' : 'Add cat'}<Plus size={16} /></button>{create.isError && <p className="form-error" role="alert">{apiError(create.error)}</p>}</form><div className="review-note"><h3>A thoughtful match matters.</h3><p>Check the introduction before approving. Cats with an open care alert or an existing adoption cannot be approved.</p></div></aside></div>
  </>
}
