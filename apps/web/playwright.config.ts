import { defineConfig, devices } from '@playwright/test';

/**
 * Recorridos completos, la herramienta que `docs/06-testing.md` nombra desde el principio y que
 * no existía hasta la Fase 6.
 *
 * **Fuera de `npm run verificar` a propósito.** Estas pruebas necesitan `bootRun`, PostgreSQL y
 * `ng serve` levantados de verdad; `verificar` tiene que seguir corriendo en seco, sin servicios.
 * El procedimiento está en el README de esta app.
 *
 * **Usa el Chrome instalado en la máquina (`channel: 'chrome'`) y no el Chromium que Playwright
 * descarga**, porque esa descarga falla en este equipo. No es un apaño: probar contra el navegador
 * que de verdad usan los compradores es más fiel, y evita bajar 150 MB por cada entorno nuevo.
 */
export default defineConfig({
  testDir: './e2e',
  // Un solo worker: las pruebas comparten una base de datos real y un carrito por navegador.
  // Paralelizarlas haría que se pisaran el inventario sembrado.
  workers: 1,
  fullyParallel: false,
  reporter: [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    // Solo al fallar: la traza es cara y en verde no la mira nadie.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chrome',
      use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    },
  ],
});
