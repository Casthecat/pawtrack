import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowUpRight, Check, ClipboardCheck, RefreshCw, TriangleAlert } from 'lucide-react'
import { api } from '@/api/pawtrack'
import { apiError, isConflict } from '@/api/axiosInstance'
import { careQueryKeys } from '@/api/careQueryKeys'
import type { Alert, AlertResolutionInput, CareRecordType } from '@/api/types'
import { ErrorState, LoadingState } from '@/components/QueryState'
import { HealthTimeline, EventTime } from '@/components/HealthTimeline'
import { careLabel } from '@/lib/careLabel'

export function CarePage() {
  const cache = useQueryClient()
  // Retain a selection after it leaves the OPEN queue; server state stays in React Query.
  const [selection, setSelection] = useState<Alert | null>(null)
  const queue = useQuery({ queryKey: careQueryKeys.openAlerts, queryFn: api.openAlerts, staleTime: 0 })
  // Commit the first automatic choice once; queue refreshes must not replace a draft.
  if (selection === null && queue.isSuccess && queue.data.length > 0) {
    setSelection(queue.data[0])
  }
  const selected = selection

  async function refreshCat(catId: number) {
    await Promise.all([
      cache.invalidateQueries({ queryKey: careQueryKeys.openAlerts }),
      cache.invalidateQueries({ queryKey: careQueryKeys.timeline(catId) }),
      cache.invalidateQueries({ queryKey: ['cat', String(catId)] }),
      cache.invalidateQueries({ queryKey: ['cats'] }),
    ])
  }
  const resolution = useMutation({
    mutationFn: ({ alert, input }: { alert: Alert; input: AlertResolutionInput }) => api.resolveAlert(alert.id, input),
    onSuccess: async (_, { alert }) => { await refreshCat(alert.catId) },
    onError: async (error, { alert }) => { if (isConflict(error)) await refreshCat(alert.catId) },
  })
  function choose(alert: Alert) {
    if (resolution.isPending) return
    setSelection(alert)
    resolution.reset()
  }

  return <>
    <div className="review-heading"><div><p className="eyebrow">SHELTER WORKSPACE</p><h1>A little care goes a long way.</h1><p>Review observations, record a care action, and help each cat move forward.</p></div><span className="staff-tag">Staff demo</span></div>
    <div className="care-layout">
      <section className="care-queue" aria-labelledby="care-queue-title">
        <div className="queue-heading"><h2 id="care-queue-title">Open care alerts <span className="care-count">{queue.isSuccess ? queue.data.length : '—'}</span></h2><button className="text-button" disabled={queue.isFetching || resolution.isPending} onClick={() => { if (selected) void refreshCat(selected.catId); else void queue.refetch() }}><RefreshCw size={15} aria-hidden="true" />Refresh</button></div>
        <p className="muted care-queue-intro">Select an alert to review the cat's care history.</p>
        {queue.isPending ? <LoadingState /> : queue.isError ? <ErrorState error={queue.error} retry={() => void queue.refetch()} /> : !queue.data.length ? <div className="empty-state care-empty" role="status"><ClipboardCheck size={30} aria-hidden="true" /><h3>No open care alerts.</h3><p>You're up to date. New alerts will appear here when observations need review.</p></div> :
          <ul className="care-alert-list">{queue.data.map(alert => <li key={alert.id}><button className="care-alert-item" aria-pressed={selected?.id === alert.id} disabled={resolution.isPending} onClick={() => choose(alert)}>
            <span className="care-alert-top"><strong>{alert.catName}</strong><span className={`care-badge care-severity-${alert.severity.toLowerCase()}`}>{careLabel(alert.severity)} severity</span></span>
            <span className="care-alert-type"><TriangleAlert size={14} aria-hidden="true" />{careLabel(alert.type)} <span>· OPEN · #{alert.id}</span></span>
            <EventTime value={alert.createdAt} />
            {alert.message && <span className="care-alert-message">{alert.message}</span>}
            {selected?.id === alert.id && <span className="care-selected-label">Selected</span>}
          </button></li>)}</ul>}
      </section>
      {selected ? <CareDetail key={selected.id} alert={selected} pending={resolution.isPending} succeeded={resolution.isSuccess} error={resolution.isError ? resolution.error : null}
        onResolve={input => {
          if (resolution.isPending) return
          setSelection(selected)
          resolution.mutate({ alert: selected, input })
        }} /> : <section className="care-panel empty-state"><ClipboardCheck size={32} aria-hidden="true" /><h2>Care, with context.</h2><p>Select an open alert to see observations, previous alerts, and the team's care notes.</p></section>}
    </div>
  </>
}

function CareDetail({ alert, pending, succeeded, error, onResolve }: {
  alert: Alert; pending: boolean; succeeded: boolean; error: unknown; onResolve: (input: AlertResolutionInput) => void
}) {
  const [careType, setCareType] = useState<CareRecordType>('CHECKUP')
  const [note, setNote] = useState('')
  const timeline = useQuery({ queryKey: careQueryKeys.timeline(alert.catId), queryFn: () => api.healthTimeline(alert.catId), staleTime: 0 })
  const event = timeline.data?.events.find(item => item.eventKind === 'ALERT' && item.sourceId === alert.id)
  const closed = succeeded || event?.alertStatus === 'CLOSED'
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!timeline.isSuccess || !note.trim() || note.length > 2000 || pending || closed) return
    onResolve({ careType, note: note.trim() })
  }

  return <section className="care-panel" aria-labelledby="selected-cat-title">
    <div className="care-cat-heading"><div><p className="eyebrow">CARE HISTORY</p><h2 id="selected-cat-title">{timeline.data?.catName ?? alert.catName}</h2></div><Link className="button secondary small" to={`/cats/${alert.catId}`}>View cat profile<ArrowUpRight size={15} aria-hidden="true" /></Link></div>
    {succeeded && <p className="success-message" role="status">Alert #{alert.id} resolved. Your care note has been added to {alert.catName}'s timeline.</p>}
    {error != null && <p className="form-error" role="alert">{apiError(error)}</p>}
    <div className="care-resolution">
      <div className="queue-heading"><h3>{careLabel(alert.type)} · Alert #{alert.id}</h3><span className={`care-badge ${closed ? 'care-closed' : 'care-open'}`}>{closed ? 'CLOSED' : 'OPEN'}</span></div>
      {closed ? <>
        <p className="muted"><Check size={15} aria-hidden="true" /> This alert is closed. The timeline stays here so you can review the care action.</p>
        {!succeeded && note && <><label htmlFor="unsent-care-note">Unsubmitted care note · {careLabel(careType)}</label><textarea id="unsent-care-note" value={note} readOnly rows={3} /></>}
      </> : <form onSubmit={submit} aria-label="Resolve selected care alert">
        <p className="muted">Record what you did. Resolving this alert adds your care note to the timeline.</p>
        {!timeline.isSuccess && <p className="muted">Load the health timeline before resolving this alert.</p>}
        <label htmlFor="care-type">Care type</label><select id="care-type" value={careType} required disabled={pending || !timeline.isSuccess} onChange={e => setCareType(e.target.value as CareRecordType)}>
          {(['CHECKUP', 'MEDICATION', 'FEEDING', 'OTHER'] as const).map(type => <option value={type} key={type}>{careLabel(type)}</option>)}
        </select>
        <label htmlFor="care-note">Care note <span className="optional">(required)</span></label><textarea id="care-note" required maxLength={2000} rows={3} value={note} disabled={pending || !timeline.isSuccess} onChange={e => setNote(e.target.value)} aria-describedby="care-note-limit" placeholder="e.g. Temperature rechecked; resting comfortably." />
        <div className="care-form-footer"><small id="care-note-limit">{note.length} / 2000 characters</small><button className="button primary" disabled={pending || !timeline.isSuccess || !note.trim()}>{pending ? 'Saving care action…' : 'Resolve care alert'}<Check size={16} aria-hidden="true" /></button></div>
      </form>}
    </div>
    <div className="queue-heading timeline-heading"><h2>Health timeline</h2><span className="muted">Newest first</span><button className="text-button" disabled={timeline.isFetching || pending} onClick={() => void timeline.refetch()} aria-label="Refresh health timeline"><RefreshCw size={15} aria-hidden="true" /></button></div>
    {timeline.isPending ? <LoadingState /> : timeline.isError ? <ErrorState error={timeline.error} retry={() => void timeline.refetch()} /> : <HealthTimeline events={timeline.data.events} />}
  </section>
}
