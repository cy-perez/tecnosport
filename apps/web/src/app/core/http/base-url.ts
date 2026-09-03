/**
 * En el navegador, base relativa a propósito, nunca una URL literal: en
 * producción el balanceador enruta /api al backend en el mismo dominio
 * (docs/07-infra-gcp.md); en local, proxy.conf.json hace lo mismo en
 * `ng serve`. Pero `fetch` en Node (SSR) no tiene un origen de página contra
 * el cual resolver una ruta relativa — ahí sí hace falta una base absoluta,
 * tomada de API_URL_PUBLICA (ya documentada en docs/07-infra-gcp.md y
 * .env.example), con el mismo valor por defecto que usa el backend en local.
 */
export function baseUrl(): string {
  if (typeof window !== 'undefined') {
    return '';
  }
  const publica = typeof process !== 'undefined' ? process.env['API_URL_PUBLICA'] : undefined;
  return publica ? new URL(publica).origin : 'http://localhost:8080';
}
