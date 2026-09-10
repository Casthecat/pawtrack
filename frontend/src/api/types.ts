export type CatHealthStatus = 'NORMAL' | 'UNDER_OBSERVATION' | 'SICK'
export type UserRole = 'STAFF' | 'ADOPTER'
export type CurrentUser = { id: number; email: string; displayName: string; role: UserRole }
export type CatAdoptionStatus = 'AVAILABLE' | 'ADOPTED'
export type AdoptionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type Cat = {
  id: number; name: string; healthStatus: CatHealthStatus; adoptionStatus: CatAdoptionStatus; imageUrl: string | null
  streamUrl: string | null; createdAt: string; updatedAt: string
}
export type CatDetail = Cat & { temperatureC: number | null; hasActiveAlert: boolean }
export type Application = {
  id: number; catId: number; catName: string; adopterName: string; adopterEmail: string
  status: AdoptionStatus; notes: string | null; createdAt: string; updatedAt: string
}
export type ApplicationInput = { catId: number; notes: string }

export type AlertStatus = 'OPEN' | 'CLOSED'
export type AlertSeverity = 'LOW' | 'MEDIUM' | 'HIGH'
export type Alert = {
  id: number; catId: number; catName: string; type: string; severity: AlertSeverity
  status: AlertStatus; message: string | null; createdAt: string; resolvedAt: string | null
}
export type CareRecordType = 'CHECKUP' | 'MEDICATION' | 'FEEDING' | 'OTHER'
export type HealthTimelineEventKind = 'HEALTH_OBSERVATION' | 'ALERT' | 'CARE_RECORD'
export type HealthTimelineEvent = {
  eventKind: HealthTimelineEventKind; sourceId: number; occurredAt: string; eventType: string
  description: string | null; temperatureC: number | null; activityLevel: number | null
  alertStatus: AlertStatus | null; alertSeverity: AlertSeverity | null; relatedAlertId: number | null
}
export type HealthTimeline = {
  catId: number; catName: string; order: 'NEWEST_FIRST'; events: HealthTimelineEvent[]
}
export type AlertResolutionInput = { careType: CareRecordType; note: string }
export type AlertResolutionResult = {
  alertId: number; catId: number; status: 'CLOSED'; resolvedAt: string; careRecordId: number
}
