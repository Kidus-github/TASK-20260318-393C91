import { expect, test } from '@playwright/test';

test('passenger happy path search and messages', async ({ page }) => {
  await page.goto('/login');
  await page.fill('input[formcontrolname="username"]', 'passenger');
  await page.fill('input[formcontrolname="password"]', 'Passenger123!');
  await page.click('button[type="submit"]');
  await expect(page.getByRole('heading', { name: 'Passenger Search' })).toBeVisible();
  await page.fill('input[placeholder*="Search route"]', '101');
  await expect(page.getByRole('button', { name: 'Reserve First Result' })).toBeVisible();
  await page.click('text=Reserve First Result');
  await page.click('text=Message Center');
  await expect(page.getByRole('heading', { name: 'Message Center' })).toBeVisible();
});

test('ui shows a friendly error when search returns 500', async ({ page }) => {
  await page.goto('/login');
  await page.fill('input[formcontrolname="username"]', 'passenger');
  await page.fill('input[formcontrolname="password"]', 'Passenger123!');
  await page.click('button[type="submit"]');
  await page.route('**/api/passenger/search/results**', route => route.fulfill({
    status: 500,
    contentType: 'application/json',
    body: JSON.stringify({ message: 'An unexpected error occurred' })
  }));
  await page.fill('input[placeholder*="Search route"]', '101');
  await expect(page.locator('.error-banner')).toContainText('An unexpected error occurred');
});

test('login failure path renders backend error', async ({ page }) => {
  await page.goto('/login');
  await page.fill('input[formcontrolname="username"]', 'passenger');
  await page.fill('input[formcontrolname="password"]', 'wrong-pass');
  await page.click('button[type="submit"]');
  await expect(page.locator('.error-banner')).toContainText('Invalid credentials');
});

test('backend failure paths return expected statuses', async ({ request, page }) => {
  const unauthorized = await request.get('/api/passenger/search/results?q=101');
  expect(unauthorized.status()).toBe(401);

  const login = await request.post('/api/auth/login', {
    data: { username: 'passenger', password: 'Passenger123!' }
  });
  const tokens = await login.json();
  const headers = { Authorization: `Bearer ${tokens.accessToken}` };

  const forbidden = await request.get('/api/admin/templates', { headers });
  expect(forbidden.status()).toBe(403);

  const notFound = await request.post('/api/passenger/messages/00000000-0000-0000-0000-000000000000/read', { headers });
  expect(notFound.status()).toBe(404);

  const validation = await request.put('/api/passenger/reminders/preferences', {
    headers: { ...headers, 'Content-Type': 'application/json' },
    data: { enabled: true, leadMinutes: 0, dndStart: '2200', dndEnd: '07:00' }
  });
  expect(validation.status()).toBe(422);

  await page.goto('/missing-route');
  await expect(page.locator('h1')).toContainText('Page Not Found');
});

test('dispatcher can return and resubmit a task', async ({ page }) => {
  await page.goto('/login');
  await page.fill('input[formcontrolname="username"]', 'dispatcher');
  await page.fill('input[formcontrolname="password"]', 'Dispatch123!');
  await page.click('button[type="submit"]');
  await expect(page.getByRole('heading', { name: 'Dispatcher Task Dashboard' })).toBeVisible();
  await page.click('text=Open');
  await page.click('text=Return');
  await page.click('text=Resubmit');
  await expect(page.locator('text=resubmissions')).toBeVisible();
});

test('admin can inspect settings and alerts', async ({ page }) => {
  await page.goto('/login');
  await page.fill('input[formcontrolname="username"]', 'admin');
  await page.fill('input[formcontrolname="password"]', 'Admin123!');
  await page.click('button[type="submit"]');
  await expect(page.getByRole('heading', { name: 'Administrator Control Surface' })).toBeVisible();
  await expect(page.locator('text=Local Alerts')).toBeVisible();
});
