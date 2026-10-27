/**
 * Las dos cabeceras de seguridad que dependen de qué usa el sitio de verdad, aparte de `server.ts`
 * para poder probarlas. Las cuatro fijas (HSTS, `nosniff`, `DENY`, `Referrer-Policy`) siguen allá.
 */

/**
 * Qué APIs del navegador puede usar este documento, y ninguna para un marco ajeno.
 *
 * Las que el sitio usa quedan en `(self)`, no en `()`: la cámara y los tres sensores de orientación
 * los usa el asistente de captura —el nivelador de `captura360`, el que reproduce
 * `npm run nivel-360`; Chrome entrega `deviceorientation` solo si `accelerometer`, `gyroscope` y
 * `magnetometer` están permitidos—, la pantalla despierta también, y el portapapeles lo usa
 * `core/portapapeles`.
 *
 * **No se pueden acotar a la ruta del asistente**, y `docs/08` lo prometía: esto es una aplicación
 * de una sola página, y la política es del documento que cargó primero. Quien entra por la portada
 * y navega al asistente del panel sigue con la política de la portada, así que una cámara apagada
 * fuera de la ruta del asistente lo dejaría sin cámara según por dónde se entró. `(self)` es lo que
 * sí se puede: ningún iframe de terceros —el de Google, por ejemplo— hereda nada.
 *
 * Solo funciones que los navegadores reconocen: una desconocida no hace daño, pero Chrome la
 * anuncia como error en la consola de cada visita, y Lighthouse cuenta esos errores.
 */
export const POLITICA_DE_PERMISOS = [
  'camera=(self)',
  'accelerometer=(self)',
  'gyroscope=(self)',
  'magnetometer=(self)',
  'screen-wake-lock=(self)',
  'clipboard-write=(self)',
  'microphone=()',
  'geolocation=()',
  'payment=()',
  'usb=()',
  'display-capture=()',
].join(', ');

/**
 * La política de contenido, **en modo informe** (`Content-Security-Policy-Report-Only`): el
 * navegador anuncia en la consola lo que bloquearía y no bloquea nada.
 *
 * Sin nonce todavía, y es una decisión de alcance, no un olvido. Angular sí tiene la pieza
 * (`ngCspNonce` en el elemento raíz, o el token `CSP_NONCE`), pero falta todo lo de alrededor:
 * generar un nonce por respuesta en este servidor, escribirlo en el HTML que devuelve
 * `AngularNodeAppEngine` y en la cabecera a la vez, y comprobar en el navegador que el CSS crítico
 * que se inyecta en línea y el script del tema de `index.html` lo llevan. Hasta entonces esta
 * política **va a informar** el script en línea del tema, y es lo esperado: es el inventario de lo
 * que el nonce tiene que cubrir.
 *
 * Los orígenes son los que el sitio usa hoy, uno por uno:
 * - **Google** (`accounts.google.com`): el botón de "Entrar con Google" carga su script, su hoja,
 *   su marco y sus llamadas desde ahí (ADR-0074; la URL del script la fija `GOOGLE_URL_SCRIPT`).
 * - **Cloud Storage** (`storage.googleapis.com`): las imágenes del catálogo, y las subidas del panel
 *   y de la ingesta, que van con URL firmada directo al bucket.
 * - **Imágenes de cualquier `https:`**: el panel muestra las de Icecat y las de la ficha del
 *   fabricante antes de publicarlas. Una imagen no ejecuta nada.
 * - **Wompi y Sistecrédito no están, y no faltan**: el sitio los abre con una navegación de primer
 *   nivel (`window.location.href`), que esta política no gobierna.
 * - **Estilos en línea**: Angular inyecta los de cada componente en `<style>`, así que `style-src`
 *   necesita `'unsafe-inline'` hasta tener el nonce. Un estilo no ejecuta código.
 */
export const POLITICA_DE_CONTENIDO = [
  "default-src 'self'",
  "script-src 'self' https://accounts.google.com/gsi/client",
  "style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/style",
  "img-src 'self' data: blob: https:",
  "font-src 'self'",
  "connect-src 'self' https://storage.googleapis.com https://accounts.google.com/gsi/",
  'frame-src https://accounts.google.com/gsi/',
  "worker-src 'self'",
  "manifest-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');
