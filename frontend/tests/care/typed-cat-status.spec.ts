import { test, expect } from '@playwright/test'
import type { Cat, CatDetail, CatHealthStatus, CatAdoptionStatus } from '../../src/api/types'

const cats: Cat[] = (['AVAILABLE', 'ADOPTED'] as CatAdoptionStatus[]).flatMap((adoptionStatus, index) =>
  (['NORMAL', 'UNDER_OBSERVATION', 'SICK'] as CatHealthStatus[]).map((healthStatus, offset) => ({
    id: index * 3 + offset + 1, name: `${adoptionStatus} ${healthStatus}`, healthStatus, adoptionStatus,
    imageUrl: null, streamUrl: null, createdAt: '2026-09-10T10:00:00Z', updatedAt: '2026-09-10T10:00:00Z',
  })))

test('gallery filters and counts respect both independent dimensions', async ({ page }) => {
  await page.route('**/api/cats', route => route.fulfill({ json: cats }))
  await page.goto('/')
  await expect(page.getByText('1 ready to meet · 6 in our family', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Ready to meet', exact: true }).click()
  await expect(page.locator('.cat-card')).toHaveCount(1)
  await expect(page.locator('.cat-card')).toContainText('AVAILABLE NORMAL')
  await page.getByRole('button', { name: 'Found a home', exact: true }).click()
  await expect(page.locator('.cat-card')).toHaveCount(3)
  const observed = page.getByRole('link', { name: 'Meet ADOPTED UNDER_OBSERVATION', exact: true })
  await expect(observed.getByText('Found a home', { exact: true })).toBeVisible()
  await expect(observed.getByText('Under observation', { exact: true })).toBeVisible()
  const sick = page.getByRole('link', { name: 'Meet ADOPTED SICK', exact: true })
  await expect(sick.getByText('Found a home', { exact: true })).toBeVisible()
  await expect(sick.getByText('Sick · receiving care', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('detail shows both dimensions and gates applications with active alerts', async ({ page }) => {
  let activeAlert = false
  await page.route('**/api/cats/*/dashboard', route => {
    const id = Number(new URL(route.request().url()).pathname.split('/')[3])
    const detail: CatDetail = { ...cats[id - 1], temperatureC: null, hasActiveAlert: activeAlert }
    return route.fulfill({ json: detail })
  })
  const labels = { NORMAL: 'Health: normal', UNDER_OBSERVATION: 'Under observation', SICK: 'Sick · receiving care' }
  for (const cat of cats) {
    await page.goto(`/cats/${cat.id}`)
    await expect(page.getByText(labels[cat.healthStatus], { exact: true })).toBeVisible()
    const form = page.getByRole('button', { name: 'Send adoption application', exact: true })
    if (cat.adoptionStatus === 'AVAILABLE' && cat.healthStatus === 'NORMAL') await expect(form).toBeVisible()
    else await expect(form).toHaveCount(0)
    if (cat.adoptionStatus === 'ADOPTED') {
      await expect(page.getByText('Found a home', { exact: true })).toBeVisible()
      await expect(page.getByText('A home has been found.', { exact: true })).toBeVisible()
    }
  }
  activeAlert = true
  await page.goto('/cats/1')
  await expect(page.getByText('Our team is reviewing a care alert. Adoption is paused until the review is complete.', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Send adoption application', exact: true })).toHaveCount(0)
  await page.goto('/cats/5')
  await expect(page.getByText('Found a home', { exact: true })).toBeVisible()
  await expect(page.getByText('Under observation', { exact: true })).toBeVisible()
  await expect(page.getByText('Our team is reviewing a care alert. This cat has already found a home.', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})
