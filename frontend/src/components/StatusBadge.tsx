import type { AdoptionStatus } from '@/api/types'

const labels: Record<AdoptionStatus, string> = {
  PENDING: 'Pending review',
  APPROVED: 'Approved', REJECTED: 'Not approved',
}
export function StatusBadge({ status }: { status: AdoptionStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}><span />{labels[status] || status}</span>
}
