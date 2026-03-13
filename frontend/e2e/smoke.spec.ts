import { expect, test } from '@playwright/test';

test('submits high-risk transaction and shows alert', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Risk Operations Dashboard' })).toBeVisible();

  const txId = `tx-e2e-${Date.now()}`;

  await page.locator('input[name="externalTransactionId"]').fill(txId);
  await page.locator('input[name="customerId"]').fill(`cust-${Date.now()}`);
  await page.locator('input[name="amount"]').fill('9000');
  await page.locator('input[name="currencyCode"]').fill('USD');
  await page.locator('input[name="countryCode"]').fill('US');

  await page.getByRole('button', { name: 'Submit & Score' }).click();

  await expect(page.locator('.status.success')).toContainText('as HIGH');

  const transactionsPanel = page.locator('article.panel').filter({ hasText: 'Recent Transactions' });
  const alertsPanel = page.locator('article.panel').filter({ hasText: 'Recent Alerts' });

  await expect(transactionsPanel.getByRole('cell', { name: txId })).toBeVisible();
  await expect(alertsPanel.getByRole('cell', { name: txId })).toBeVisible();
});

test('submits low-risk transaction and does not create alert', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Risk Operations Dashboard' })).toBeVisible();

  const txId = `tx-e2e-low-${Date.now()}`;

  await page.locator('input[name="externalTransactionId"]').fill(txId);
  await page.locator('input[name="customerId"]').fill(`cust-low-${Date.now()}`);
  await page.locator('input[name="amount"]').fill('200');
  await page.locator('input[name="currencyCode"]').fill('USD');
  await page.locator('input[name="countryCode"]').fill('US');

  await page.getByRole('button', { name: 'Submit & Score' }).click();

  await expect(page.locator('.status.success')).toContainText('as LOW');

  const transactionsPanel = page.locator('article.panel').filter({ hasText: 'Recent Transactions' });
  const alertsPanel = page.locator('article.panel').filter({ hasText: 'Recent Alerts' });

  await expect(transactionsPanel.getByRole('cell', { name: txId })).toBeVisible();
  await expect(alertsPanel.getByRole('cell', { name: txId })).toHaveCount(0);
});

test('submits medium-risk transaction and does not create alert', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Risk Operations Dashboard' })).toBeVisible();

  const txId = `tx-e2e-med-${Date.now()}`;

  await page.locator('input[name="externalTransactionId"]').fill(txId);
  await page.locator('input[name="customerId"]').fill(`cust-med-${Date.now()}`);
  await page.locator('input[name="amount"]').fill('5000');
  await page.locator('input[name="currencyCode"]').fill('USD');
  await page.locator('input[name="countryCode"]').fill('US');

  await page.getByRole('button', { name: 'Submit & Score' }).click();

  await expect(page.locator('.status.success')).toContainText('as MEDIUM');

  const transactionsPanel = page.locator('article.panel').filter({ hasText: 'Recent Transactions' });
  const alertsPanel = page.locator('article.panel').filter({ hasText: 'Recent Alerts' });

  await expect(transactionsPanel.getByRole('cell', { name: txId })).toBeVisible();
  await expect(alertsPanel.getByRole('cell', { name: txId })).toHaveCount(0);
});

test('maps 0.40 boundary to MEDIUM and does not create alert', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Risk Operations Dashboard' })).toBeVisible();

  const txId = `tx-e2e-bnd-med-${Date.now()}`;

  await page.locator('input[name="externalTransactionId"]').fill(txId);
  await page.locator('input[name="customerId"]').fill(`cust-bnd-med-${Date.now()}`);
  await page.locator('input[name="amount"]').fill('4000');
  await page.locator('input[name="currencyCode"]').fill('USD');
  await page.locator('input[name="countryCode"]').fill('US');

  await page.getByRole('button', { name: 'Submit & Score' }).click();

  await expect(page.locator('.status.success')).toContainText('as MEDIUM');

  const transactionsPanel = page.locator('article.panel').filter({ hasText: 'Recent Transactions' });
  const alertsPanel = page.locator('article.panel').filter({ hasText: 'Recent Alerts' });

  await expect(transactionsPanel.getByRole('cell', { name: txId })).toBeVisible();
  await expect(alertsPanel.getByRole('cell', { name: txId })).toHaveCount(0);
});

test('maps 0.75 boundary to HIGH and creates alert', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Risk Operations Dashboard' })).toBeVisible();

  const txId = `tx-e2e-bnd-high-${Date.now()}`;

  await page.locator('input[name="externalTransactionId"]').fill(txId);
  await page.locator('input[name="customerId"]').fill(`cust-bnd-high-${Date.now()}`);
  await page.locator('input[name="amount"]').fill('7500');
  await page.locator('input[name="currencyCode"]').fill('USD');
  await page.locator('input[name="countryCode"]').fill('US');

  await page.getByRole('button', { name: 'Submit & Score' }).click();

  await expect(page.locator('.status.success')).toContainText('as HIGH');

  const transactionsPanel = page.locator('article.panel').filter({ hasText: 'Recent Transactions' });
  const alertsPanel = page.locator('article.panel').filter({ hasText: 'Recent Alerts' });

  await expect(transactionsPanel.getByRole('cell', { name: txId })).toBeVisible();
  await expect(alertsPanel.getByRole('cell', { name: txId })).toBeVisible();
});
