import { test, expect, type Page } from '@playwright/test'
import type { Application, CurrentUser } from '../../src/api/types'

const owner: CurrentUser = { id: 2, email: 'owner@example.com', displayName: 'Owner Name', role: 'ADOPTER' }
const other: CurrentUser = { id: 3, email: 'other@example.com', displayName: 'Other Name', role: 'ADOPTER' }
const staff: CurrentUser = { id: 1, email: 'staff@example.com', displayName: 'Staff', role: 'STAFF' }
const cat = { id: 4, name: 'Mochi', healthStatus: 'NORMAL', adoptionStatus: 'AVAILABLE', imageUrl: null, temperatureC: 38.5, hasActiveAlert: false }
const receipt: Application = { id: 7, catId: 4, catName: 'Mochi', adopterName: owner.displayName, adopterEmail: owner.email, notes: 'A quiet home.', status: 'PENDING', createdAt: '2026-09-10T12:00:00Z', updatedAt: '2026-09-10T12:00:00Z' }

async function setup(page: Page, initial: CurrentUser | null = null, submitted = false) {
  const state = { user: initial, submitted, token: '', csrfCount: 0, body: null as unknown, privateReads: 0 }
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const req = route.request(), path = new URL(req.url()).pathname
    if (path === '/api/auth/me') return route.fulfill(state.user ? { json: state.user } : { status: 401, json: { message: 'Authentication required.' } })
    if (path === '/api/auth/csrf') { state.token = `token-${++state.csrfCount}`; return route.fulfill({ json: { headerName: 'X-XSRF-TOKEN', token: state.token } }) }
    if (req.method() !== 'GET') expect(req.headers()['x-xsrf-token']).toBe(state.token)
    if (path === '/api/auth/login') {
      const email = new URLSearchParams(req.postData() || '').get('email')
      state.user = email === owner.email ? owner : email === staff.email ? staff : other
      state.token = 'requires-new-token'
      return route.fulfill({ json: state.user })
    }
    if (path === '/api/auth/logout') { state.user = null; return route.fulfill({ status: 204 }) }
    if (path === '/api/cats') return route.fulfill({ json: [cat] })
    if (path === '/api/cats/4/dashboard') return route.fulfill({ json: cat })
    state.privateReads++
    if (!state.user) return route.fulfill({ status: 401, json: { message: 'Authentication required.' } })
    if (path === '/api/adoptions' && req.method() === 'POST') {
      expect(state.user.id).toBe(owner.id)
      state.body = req.postDataJSON(); state.submitted = true
      return route.fulfill({ status: 201, json: receipt })
    }
    if (path === '/api/me/adoptions') return route.fulfill({ json: state.user.id === owner.id && state.submitted ? [receipt] : [] })
    if (path === '/api/adoptions/7') return route.fulfill(state.submitted && (state.user.id === owner.id || state.user.role === 'STAFF')
      ? { json: receipt } : { status: 404, json: { message: 'Adoption application not found: 7' } })
    throw new Error(`Unexpected ${req.method()} ${path}`)
  })
  return state
}
async function login(page: Page, email = owner.email) {
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('test-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
}

test('anonymous apply returns to Cat after login and submits account identity with central CSRF', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/cats/4')
  await expect(page.getByLabel('Your name', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('Email address', { exact: true })).toHaveCount(0)
  await page.getByRole('link', { name: 'Sign in to apply', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await login(page)
  await expect(page).toHaveURL(/\/cats\/4$/)
  await expect(page.locator('.applicant-identity')).toContainText('Owner Name')
  await expect(page.locator('.applicant-identity')).toContainText(owner.email)
  await expect(page.locator('.applicant-identity input')).toHaveCount(0)
  await page.getByLabel('A little about your home', { exact: false }).fill('A quiet home.')
  await page.getByRole('button', { name: 'Send adoption application', exact: true }).click()
  await expect(page).toHaveURL(/\/applications\/7$/)
  await expect(page.locator('.receipt')).toContainText('Owner Name')
  expect(state.body).toEqual({ catId: 4, notes: 'A quiet home.' })
  expect(state.csrfCount).toBe(2)
  await page.getByRole('link', { name: 'My applications', exact: true }).first().click()
  await expect(page.getByRole('list', { name: 'My applications, newest first' })).toContainText('Mochi')
  await expect(page.getByText('Pending review', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'View application #7 →', exact: true }).click()
  await expect(page.locator('.receipt')).toContainText('Owner Name')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)
})

test('logout removes private access and another adopter cannot see cached receipt or owner list', async ({ page }) => {
  await setup(page, owner, true)
  await page.goto('/applications/7')
  await expect(page.locator('.receipt')).toContainText('Owner Name')
  await page.getByRole('link', { name: 'My applications', exact: true }).first().click()
  await expect(page.getByRole('list', { name: 'My applications, newest first' })).toContainText('Mochi')
  await page.getByRole('link', { name: 'View application #7 →', exact: true }).click()
  await expect(page.locator('.receipt')).toContainText('Owner Name')
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
  await page.goto('/applications/7')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.locator('.receipt')).toHaveCount(0)
  await login(page, other.email)
  await expect(page).toHaveURL(/\/applications\/7$/)
  await expect(page.getByRole('heading', { name: 'Application unavailable' })).toBeVisible()
  await expect(page.getByText('Owner Name', { exact: false })).toHaveCount(0)
  await page.getByRole('link', { name: 'My applications', exact: true }).first().click()
  await expect(page.getByRole('heading', { name: 'No applications yet' })).toBeVisible()
})

test('expired receipt requires login and recovers the same receipt without treating 401 as missing', async ({ page }) => {
  const state = await setup(page, owner, true)
  await page.goto('/applications/7')
  await expect(page.locator('.receipt')).toContainText('Owner Name')
  state.user = null
  await page.getByRole('button', { name: 'Refresh status', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Application unavailable' })).toHaveCount(0)
  await expect(page.locator('.receipt')).toHaveCount(0)
  await login(page)
  await expect(page).toHaveURL(/\/applications\/7$/)
  await expect(page.locator('.receipt')).toContainText('Owner Name')
})

test('STAFF may inspect receipts but cannot use adopter submit or My applications workspace', async ({ page }) => {
  await setup(page, staff, true)
  await page.goto('/cats/4')
  await expect(page.getByRole('heading', { name: 'Adopter account required' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Send adoption application', exact: true })).toHaveCount(0)
  await page.goto('/applications')
  await expect(page.getByRole('heading', { name: 'Adopter account required' })).toBeVisible()
  await page.goto('/applications/7')
  await expect(page.locator('.receipt')).toContainText('Owner Name')
  await expect(page.getByRole('link', { name: 'Open staff review →', exact: true })).toBeVisible()
})

test('anonymous My applications requires login and returns to the list', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/applications')
  await expect(page).toHaveURL(/\/login$/)
  expect(state.privateReads).toBe(0)
  await login(page)
  await expect(page).toHaveURL(/\/applications$/)
  await expect(page.getByRole('heading', { name: 'No applications yet' })).toBeVisible()
})
