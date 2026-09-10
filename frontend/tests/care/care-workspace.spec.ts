import { test, expect, type Page } from '@playwright/test'
import type { Alert, HealthTimeline, HealthTimelineEvent } from '../../src/api/types'

const nori: Alert = {
  id: 901, catId: 4, catName: 'Nori', type: 'FEVER', severity: 'HIGH',
  status: 'OPEN', message: 'High temperature detected.',
  createdAt: '2026-09-10T09:00:00Z', resolvedAt: null,
}
const cleo: Alert = { ...nori, id: 902, catId: 5, catName: 'Cleo', createdAt: '2026-09-10T10:00:00Z' }
const draft = 'Temperature rechecked; resting comfortably. Fresh water offered.'

function deferred() {
  let resolve!: () => void
  const promise = new Promise<void>(done => { resolve = done })
  return { promise, resolve }
}

function timeline(alert: Alert, closed: boolean, note: string | null): HealthTimeline {
  const events: HealthTimelineEvent[] = [{
    eventKind: 'ALERT', sourceId: alert.id, occurredAt: alert.createdAt, eventType: 'FEVER',
    description: alert.message, temperatureC: null, activityLevel: null,
    alertStatus: closed ? 'CLOSED' : 'OPEN', alertSeverity: 'HIGH', relatedAlertId: null,
  }, {
    eventKind: 'HEALTH_OBSERVATION', sourceId: 801, occurredAt: '2026-09-10T08:59:00Z', eventType: 'VITALS',
    description: null, temperatureC: 40, activityLevel: 1,
    alertStatus: null, alertSeverity: null, relatedAlertId: null,
  }]
  if (note) events.unshift({
    eventKind: 'CARE_RECORD', sourceId: 1001, occurredAt: '2026-09-10T11:00:00Z', eventType: 'CHECKUP',
    description: note, temperatureC: null, activityLevel: null,
    alertStatus: null, alertSeverity: null, relatedAlertId: alert.id,
  })
  return { catId: alert.catId, catName: alert.catName, order: 'NEWEST_FIRST', events }
}

async function mockCareApi(page: Page) {
  const state = {
    queue: [nori], closed: false, careNote: null as string | null,
    timelineGate: null as ReturnType<typeof deferred> | null,
    patchGate: null as ReturnType<typeof deferred> | null,
    timelineFails: false, patchOutcome: 'success' as 'success' | 'network' | 'conflict',
    queueRequests: 0, timelineRequests: 0, patchRequests: 0,
  }
  // Every API request is intercepted; these checks never mutate a running demo backend.
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 1, email: 'staff@example.com', displayName: 'Demo Staff', role: 'STAFF' } })
    if (path === '/api/auth/csrf') return route.fulfill({ json: { headerName: 'X-XSRF-TOKEN', token: 'care-csrf' } })
    if (path === '/api/alerts' && request.method() === 'GET') {
      state.queueRequests++
      return route.fulfill({ json: state.queue })
    }
    if (path.endsWith('/health-timeline') && request.method() === 'GET') {
      state.timelineRequests++
      const alert = path.includes('/cats/4/') ? nori : cleo
      // Capture the response before waiting, reproducing an old in-flight GET.
      const response = timeline(alert, alert.id === nori.id && state.closed, state.careNote)
      if (state.timelineGate) await state.timelineGate.promise
      if (state.timelineFails) return route.fulfill({ status: 503, json: { message: 'Timeline unavailable.' } })
      return route.fulfill({ json: response })
    }
    if (path === '/api/alerts/901/resolve' && request.method() === 'PATCH') {
      expect(request.headers()['x-xsrf-token']).toBe('care-csrf')
      state.patchRequests++
      if (state.patchGate) await state.patchGate.promise
      if (state.patchOutcome === 'network') return route.abort('failed')
      state.closed = true
      state.queue = state.queue.filter(alert => alert.id !== nori.id)
      if (state.patchOutcome === 'conflict') {
        state.careNote = 'Already checked by another staff member.'
        return route.fulfill({ status: 409, json: { message: 'This alert is already closed. Please refresh the cat\'s health timeline.' } })
      }
      state.careNote = request.postDataJSON().note
      return route.fulfill({ json: {
        alertId: nori.id, catId: nori.catId, status: 'CLOSED',
        resolvedAt: '2026-09-10T11:00:00Z', careRecordId: 1001,
      } })
    }
    throw new Error(`Unexpected API request: ${request.method()} ${path}`)
  })
  return state
}

const noteInput = (page: Page) => page.getByLabel('Care note', { exact: false })
const resolveButton = (page: Page) => page.getByRole('button', { name: 'Resolve care alert', exact: true })
const events = (page: Page) => page.getByRole('list', { name: 'Health events, newest first' })

async function assertLayout(page: Page) {
  await expect(page.getByRole('heading', { name: 'Nori', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
}

test('automatic selection and draft survive a new queue head and reordering', async ({ page }) => {
  const state = await mockCareApi(page)
  await page.goto('/staff/care')
  await noteInput(page).fill(draft)
  state.queue = [cleo, nori]
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page.locator('.care-alert-item').first()).toContainText('Cleo')
  await expect(page.locator('.care-alert-item[aria-pressed="true"]')).toContainText('Nori')
  await expect(noteInput(page)).toHaveValue(draft)
  state.queue = [nori, cleo]
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page.locator('.care-alert-item').first()).toContainText('Nori')
  await expect(noteInput(page)).toHaveValue(draft)
  await assertLayout(page)
  // Explicit selection is still allowed to leave the current draft.
  await page.locator('.care-alert-item').filter({ hasText: 'Cleo' }).click()
  await expect(page.getByRole('heading', { name: 'Cleo', exact: true })).toBeVisible()
  await expect(noteInput(page)).toHaveValue('')
})

test('externally resolved selection retains context and unsubmitted draft', async ({ page }) => {
  const state = await mockCareApi(page)
  await page.goto('/staff/care')
  await noteInput(page).fill(draft)
  state.queue = [cleo]
  state.closed = true
  state.careNote = 'Already checked by another staff member.'
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page.locator('.care-alert-item')).toHaveCount(1)
  await expect(page.locator('.care-alert-item')).toContainText('Cleo')
  await expect(page.getByLabel('Unsubmitted care note', { exact: false })).toHaveValue(draft)
  await expect(events(page).getByText('CLOSED', { exact: true })).toBeVisible()
  await assertLayout(page)
  state.queue = []
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page.getByText('No open care alerts.', { exact: true })).toBeVisible()
  await expect(page.getByLabel('Unsubmitted care note', { exact: false })).toHaveValue(draft)
  expect(state.patchRequests).toBe(0)
})

test('initial timeline must load before resolution; success refreshes closed alert and care', async ({ page }) => {
  const state = await mockCareApi(page)
  const initial = deferred()
  state.timelineGate = initial
  await page.goto('/staff/care')
  await expect.poll(() => state.timelineRequests).toBe(1)
  await expect(resolveButton(page)).toBeDisabled()
  await expect(noteInput(page)).toBeDisabled()
  await expect(page.getByLabel('Care type', { exact: true })).toBeDisabled()
  // Exercise the handler as well as the disabled button, with a nonempty draft.
  await noteInput(page).evaluate((element: HTMLTextAreaElement) => { element.disabled = false })
  await noteInput(page).fill(draft)
  await page.getByRole('form', { name: 'Resolve selected care alert' }).dispatchEvent('submit')
  expect(state.patchRequests).toBe(0)
  state.timelineGate = null
  initial.resolve()
  await expect(resolveButton(page)).toBeEnabled()
  await expect(events(page).getByText('OPEN', { exact: true })).toBeVisible()
  const patch = deferred()
  state.patchGate = patch
  await resolveButton(page).click()
  await expect.poll(() => state.patchRequests).toBe(1)
  await expect(page.getByRole('button', { name: 'Saving care action…' })).toBeDisabled()
  await expect(noteInput(page)).toBeDisabled()
  await page.getByRole('form', { name: 'Resolve selected care alert' }).dispatchEvent('submit')
  expect(state.patchRequests).toBe(1)
  patch.resolve()
  await expect(page.getByText('Alert #901 resolved. Your care note has been added to Nori\'s timeline.', { exact: true })).toBeVisible()
  await expect(events(page).getByText('CLOSED', { exact: true })).toBeVisible()
  await expect(events(page).getByText(draft, { exact: true })).toBeVisible()
  await expect(events(page).getByText('OPEN', { exact: true })).toHaveCount(0)
  await expect(page.getByText('No open care alerts.', { exact: true })).toBeVisible()
  expect(state.timelineRequests).toBeGreaterThanOrEqual(2)
  expect(state.patchRequests).toBe(1)
  await assertLayout(page)
})

test('failed timeline keeps resolution unavailable until successful retry', async ({ page }) => {
  const state = await mockCareApi(page)
  state.timelineFails = true
  await page.goto('/staff/care')
  await expect(page.getByText('Timeline unavailable.', { exact: true })).toBeVisible()
  await expect(resolveButton(page)).toBeDisabled()
  await expect(noteInput(page)).toBeDisabled()
  expect(state.patchRequests).toBe(0)
  state.timelineFails = false
  await page.getByRole('button', { name: 'Try again', exact: true }).click()
  await noteInput(page).fill(draft)
  await expect(resolveButton(page)).toBeEnabled()
  await assertLayout(page)
})

test('PATCH network failure preserves form; retry conflict refreshes without claiming success', async ({ page }) => {
  const state = await mockCareApi(page)
  state.patchOutcome = 'network'
  await page.goto('/staff/care')
  await noteInput(page).fill(draft)
  await page.getByLabel('Care type', { exact: true }).selectOption('FEEDING')
  await resolveButton(page).click()
  await expect(page.getByRole('alert')).toContainText('We could not reach PawTrack')
  await expect(noteInput(page)).toHaveValue(draft)
  await expect(page.getByLabel('Care type', { exact: true })).toHaveValue('FEEDING')
  await expect(resolveButton(page)).toBeEnabled()
  state.patchOutcome = 'conflict'
  await resolveButton(page).click()
  await expect(page.getByRole('alert')).toContainText('This alert is already closed.')
  await expect(events(page).getByText('CLOSED', { exact: true })).toBeVisible()
  await expect(page.getByLabel('Unsubmitted care note', { exact: false })).toHaveValue(draft)
  await expect(page.locator('.success-message')).toHaveCount(0)
  expect(state.patchRequests).toBe(2)
  await assertLayout(page)
})

test('temperature precision agrees in timeline and public profile', async ({ page }) => {
  await mockCareApi(page)
  const measurements = [38.40, 39.50, 39.51, 40.00]
  const history = timeline(nori, false, null)
  const observation = history.events.find(event => event.eventKind === 'HEALTH_OBSERVATION')!
  history.events = measurements.map((temperatureC, index) => ({ ...observation, sourceId: 810 + index, temperatureC }))
  await page.route('**/api/cats/4/health-timeline', route => route.fulfill({ json: history }))
  await page.route('**/api/cats/4/dashboard', route => route.fulfill({ json: {
    id: 4, name: 'Nori', healthStatus: 'UNDER_OBSERVATION', adoptionStatus: 'AVAILABLE', imageUrl: null, streamUrl: null,
    createdAt: nori.createdAt, updatedAt: nori.createdAt, temperatureC: 39.51, hasActiveAlert: true,
  } }))
  await page.goto('/staff/care')
  for (const temperature of measurements) {
    await expect(events(page).getByText(`${temperature.toFixed(2)} °C`, { exact: true })).toBeVisible()
  }
  await expect(events(page).getByText('39.5 °C', { exact: true })).toHaveCount(0)
  await page.getByRole('link', { name: 'View cat profile', exact: true }).click()
  await expect(page.getByText('39.51 °C', { exact: true })).toBeVisible()
  await expect(page.getByText('39.5 °C', { exact: true })).toHaveCount(0)
})
