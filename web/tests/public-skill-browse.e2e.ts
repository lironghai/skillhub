import { test } from '@e2e-dev/web';
import { expect } from 'e2e';

test('a visitor searches for skills and preserves the selected sort', async ({ app, screen, browser }) => {
  await browser.addInitScript(() => {
    window.localStorage.setItem('i18nextLng', 'en');
  });
  await app.open('/');
  await expect(screen.getByRole('link', 'Browse all')).toBeVisible();

  const search = screen.getByPlaceholder('Search skills...');
  await search.fill('agent ops');
  await search.press('Enter');

  await expect(browser).toHaveURL(/\/search\?q=agent(\+|%20)ops&sort=relevance&page=0&starredOnly=false$/);
  await expect(search).toHaveValue('agent ops');
  await screen.getByRole('button', 'Downloads').tap();
  await expect(browser).toHaveURL(/\/search\?q=agent(\+|%20)ops&sort=downloads&page=0&starredOnly=false$/);

  await browser.reload();
  await expect(search).toHaveValue('agent ops');
  await expect(screen.getByRole('button', 'Downloads')).toBeVisible();
  await expect(browser).toHaveURL(/\/search\?q=agent(\+|%20)ops&sort=downloads&page=0&starredOnly=false$/);
});
