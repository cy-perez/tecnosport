/**
 * Origen absoluto del sitio, para la URL canónica y las alternativas de idioma.
 *
 * Espejo exacto de `core/http/base-url.ts` y por el mismo motivo: en el
 * navegador el origen se conoce (`location.origin`), pero en el SSR no hay
 * página contra la cual resolver nada, y `<link rel="canonical">` no admite una
 * ruta relativa — un canónico relativo lo resuelve cada rastreador contra el
 * host por el que entró, que es justo lo que el canónico existe para evitar.
 *
 * De `APP_URL_PUBLICA`, nunca literal (regla dura #5). Ya está en
 * `.env.example` y en `docs/07-infra-gcp.md`; la usa el backend para armar los
 * enlaces de los correos, y aquí sirve para lo mismo: decir cuál es el nombre
 * público de este sitio cuando el proceso no puede deducirlo.
 *
 * El valor por defecto es el del `ng serve` local, igual que el de `baseUrl()`.
 */
export function origenPublico(): string {
  const publica = typeof process !== 'undefined' ? process.env['APP_URL_PUBLICA'] : undefined;
  if (publica) {
    return new URL(publica).origin;
  }
  if (typeof window !== 'undefined') {
    return window.location.origin;
  }
  return 'http://localhost:4200';
}
