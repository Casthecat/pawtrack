import type { Cat, CatAdoptionStatus, CatHealthStatus } from '@/api/types'

const adoptionLabels: Record<CatAdoptionStatus, string> = {
  AVAILABLE: 'Awaiting a home', ADOPTED: 'Found a home',
}
const healthLabels: Record<CatHealthStatus, string> = {
  NORMAL: 'Health: normal', UNDER_OBSERVATION: 'Under observation', SICK: 'Sick · receiving care',
}

export function CatStatusBadges({ cat }: { cat: Pick<Cat, 'healthStatus' | 'adoptionStatus'> }) {
  return <div className="cat-statuses">
    <span className={`status status-${cat.adoptionStatus.toLowerCase()}`}><span />{adoptionLabels[cat.adoptionStatus]}</span>
    <span className={`status status-${cat.healthStatus.toLowerCase()}`}><span />{healthLabels[cat.healthStatus]}</span>
  </div>
}
