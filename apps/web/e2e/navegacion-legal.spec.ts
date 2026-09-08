import { expect, test } from '@playwright/test';

/**
 * La regla de cierre de fase de `docs/09-plan-de-arranque.md`: cada pantalla nueva tiene que ser
 * alcanzable con clics desde la portada, sin teclear una URL. Ya costó dos correcciones —las fases
 * 3 y 4 se cerraron con dieciocho pantallas escondidas, y la 5 con el panel inalcanzable—, así que
 * a partir de aquí lo comprueba una prueba y no la memoria de nadie.
 */
test.describe('todo se alcanza con clics desde la portada', () => {
  for (const [enlace, encabezado] of [
    ['Términos y condiciones', /términos y condiciones/i],
    ['Política de datos', /política de tratamiento de datos/i],
    ['Cookies', /política de cookies/i],
  ] as const) {
    test(`portada -> pie -> ${enlace}`, async ({ page }) => {
      await page.goto('/es');

      await page.getByRole('link', { name: enlace }).click();

      await expect(page.getByRole('heading', { level: 1, name: encabezado })).toBeVisible();
    });
  }

  test('portada -> pie -> panel administrativo pide iniciar sesión', async ({ page }) => {
    await page.goto('/es');

    await page.getByRole('link', { name: 'Panel administrativo' }).click();

    // Sin sesión, adminGuard redirige al ingreso anotando a dónde iba.
    await expect(page).toHaveURL(/\/admin\/iniciar-sesion\?destino=/);
  });
});
