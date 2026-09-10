import { test, expect, type Page } from '@playwright/test'
import type { CurrentUser } from '../../src/api/types'

const staff: CurrentUser = { id: 1, email: 'staff@example.com', displayName: 'Demo Staff', role: 'STAFF' }
const adopter: CurrentUser = { id: 2, email: 'adopter@example.com', displayName: 'Demo Adopter', role: 'ADOPTER' }

async function mockAuth(page: Page, initial: CurrentUser | null = null) {
  const state = { user: initial, csrfCount: 0, token: '', loginCount: 0, writes: 0, staffReads: 0 }
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path === '/api/auth/me') return route.fulfill(state.user ? { json: state.user } : { status: 401, json: { message: 'Authentication required.' } })
    if (path === '/api/auth/csrf') {
      state.token = `csrf-${++state.csrfCount}`
      return route.fulfill({ json: { headerName: 'X-XSRF-TOKEN', token: state.token } })
    }
    if (request.method() !== 'GET') expect(request.headers()['x-xsrf-token']).toBe(state.token)
    if (path === '/api/auth/login') {
      state.loginCount++
      const form = new URLSearchParams(request.postData() || '')
      if (form.get('password') !== 'demo-password') return route.fulfill({ status: 401, json: { message: 'Invalid email or password.' } })
      state.user = form.get('email') === 'staff@example.com' ? staff : adopter
      state.token = 'must-refresh-after-login'
      return route.fulfill({ json: state.user })
    }
    if (path === '/api/auth/logout') { state.user = null; return route.fulfill({ status: 204 }) }
    if (path === '/api/cats' && request.method() === 'POST') {
      state.writes++
      return route.fulfill({ json: { id: 7, name: request.postDataJSON().name, healthStatus: 'NORMAL', adoptionStatus: 'AVAILABLE' } })
    }
    if (path === '/api/cats') return route.fulfill({ json: [] })
    if (path === '/api/alerts' || path === '/api/adoptions') {
      state.staffReads++
      return route.fulfill(state.user?.role === 'STAFF' ? { json: [] } : { status: state.user ? 403 : 401, json: { message: state.user ? 'Staff access required.' : 'Authentication required.' } })
    }
    throw new Error(`Unexpected request ${request.method()} ${path}`)
  })
  return state
}

async function signIn(page: Page, email = 'staff@example.com', password = 'demo-password') {
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
}

test('anonymous care entry goes to login and STAFF returns to the intended route with fresh CSRF', async ({ page }) => {
  const state = await mockAuth(page)
  await page.goto('/staff/care')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('Sign in to continue to the staff workspace.', { exact: true })).toBeVisible()
  expect(state.staffReads).toBe(0)
  await signIn(page)
  await expect(page).toHaveURL(/\/staff\/care$/)
  await expect(page.getByText('No open care alerts.', { exact: true })).toBeVisible()
  expect(state.csrfCount).toBe(2)
  await page.getByRole('link', { name: 'Adoption review', exact: true }).click()
  await page.getByLabel("Cat's name", { exact: true }).fill('New friend')
  await page.getByRole('button', { name: 'Add cat', exact: true }).click()
  await expect(page.getByText('New friend has joined the PawTrack family.', { exact: true })).toBeVisible()
  expect(state.writes).toBe(1)
  expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }))).toEqual({ local: 0, session: 0 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('invalid credentials remain on login and ADOPTER gets staff-required without a redirect loop', async ({ page }) => {
  const state = await mockAuth(page)
  await page.goto('/staff')
  await signIn(page, 'staff@example.com', 'wrong')
  await expect(page.getByRole('alert')).toContainText('Invalid email or password.')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue('')
  await signIn(page, 'adopter@example.com')
  await expect(page).toHaveURL(/\/staff$/)
  await expect(page.getByRole('heading', { name: 'Staff access required' })).toBeVisible()
  expect(state.staffReads).toBe(0)
  expect(state.loginCount).toBe(2)
  await page.getByRole('link', { name: 'Meet the cats', exact: true }).first().click()
  await expect(page).toHaveURL(/\/$/)
})

test('existing session reload reacquires CSRF; logout removes access and private cache', async ({ page }) => {
  const state = await mockAuth(page, staff)
  await page.goto('/staff')
  await page.reload()
  await page.getByLabel("Cat's name", { exact: true }).fill('Reloaded friend')
  await page.getByRole('button', { name: 'Add cat', exact: true }).click()
  await expect(page.getByText('Reloaded friend has joined the PawTrack family.', { exact: true })).toBeVisible()
  expect(state.csrfCount).toBe(1)
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Sign in', exact: true })).toBeVisible()
  await page.goto('/staff/care')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: 'Open care alerts', exact: false })).toHaveCount(0)
})

test('expired staff request shows sign-in flow instead of an empty queue and can recover', async ({ page }) => {
  const state = await mockAuth(page, staff)
  await page.goto('/staff/care')
  await expect(page.getByText('No open care alerts.', { exact: true })).toBeVisible()
  state.user = null
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByText('Sign in to continue to the staff workspace.', { exact: true })).toBeVisible()
  await expect(page.getByText('No open care alerts.', { exact: true })).toHaveCount(0)
  await signIn(page)
  await expect(page).toHaveURL(/\/staff\/care$/)
  await expect(page.getByText('No open care alerts.', { exact: true })).toBeVisible()
})
