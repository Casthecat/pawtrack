export const careQueryKeys = {
  openAlerts: ['alerts', 'OPEN'] as const,
  timeline: (catId: number) => ['health-timeline', catId] as const,
}
