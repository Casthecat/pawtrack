import type { AdoptionStatus, CatStatus } from '@/api/types'

const labels: Record<CatStatus | AdoptionStatus, string> = {
  NORMAL: 'Ready to meet', ADOPTABLE: 'Ready to meet', UNDER_OBSERVATION: 'Under observation',
  SICK: 'Receiving care', ADOPTED: 'Found a home', PENDING: 'Pending review',
  APPROVED: 'Approved', REJECTED: 'Not approved',
}
export function StatusBadge({ status }: { status: CatStatus | AdoptionStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}><span />{labels[status] || status}</span>
}
