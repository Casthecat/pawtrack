import { Activity, ClipboardCheck, TriangleAlert } from 'lucide-react'
import type { HealthTimelineEvent } from '@/api/types'
import { careLabel } from '@/lib/careLabel'

export function EventTime({ value }: { value: string }) {
  return <time dateTime={value}>{new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}</time>
}

export function HealthTimeline({ events }: { events: HealthTimelineEvent[] }) {
  if (!events.length) return <div className="empty-state"><h3>No care history yet.</h3><p>Observations, alerts, and care notes will appear here.</p></div>
  return <ol className="health-timeline" aria-label="Health events, newest first">
    {events.map(event => {
      const observation = event.eventKind === 'HEALTH_OBSERVATION'
      const alert = event.eventKind === 'ALERT'
      const Icon = observation ? Activity : alert ? TriangleAlert : ClipboardCheck
      return <li key={`${event.eventKind}-${event.sourceId}`} className={`timeline-event timeline-${event.eventKind.toLowerCase()}`}>
        <span className="timeline-icon"><Icon size={17} aria-hidden="true" /></span>
        <article>
          <div className="timeline-meta"><span>{observation ? 'Health observation' : alert ? `Alert #${event.sourceId}` : 'Care record'}</span><EventTime value={event.occurredAt} /></div>
          <div className="timeline-title"><h3>{observation ? 'Recorded observations' : careLabel(event.eventType)}</h3>
            {alert && <><span className={`care-badge care-${event.alertStatus?.toLowerCase()}`}>{event.alertStatus}</span><span className="care-severity">{careLabel(event.alertSeverity || '')} severity</span></>}
          </div>
          {observation && <div className="observation-values"><span>Temperature <strong>{event.temperatureC == null ? 'Not recorded' : `${event.temperatureC.toFixed(2)} °C`}</strong></span><span>Activity level <strong>{event.activityLevel ?? 'Not recorded'}</strong></span></div>}
          {event.description && <p className="timeline-note">{event.description}</p>}
          {event.relatedAlertId != null && <p className="care-reference">Care action for alert #{event.relatedAlertId}</p>}
        </article>
      </li>
    })}
  </ol>
}
