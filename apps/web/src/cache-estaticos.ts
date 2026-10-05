import { relative, sep } from 'node:path';

/**
 * Lo que el build de Angular nombra con el hash del contenido (`outputHashing: "all"`): los
 * bundles (`main-IZ72O7P5.js`, `chunk-305WEk-I2.js`), la hoja de estilos y lo que va a `media/`
 * (las fuentes). Si el contenido cambia, cambia el nombre, y por eso estos sí pueden quedarse un
 * año en la caché del navegador.
 */
const CON_HASH = /^(?:main|chunk|polyfills|styles)-[A-Za-z0-9_-]{8,}\.(?:js|mjs|css)$/;

const UN_ANO = 'public, max-age=31536000, immutable';

/**
 * Todo lo demás conserva su nombre entre despliegues: las traducciones de `assets/i18n`, los
 * logos de `assets/marca`, los íconos, el manifiesto y `ngsw-worker.js`. Se pueden guardar, pero
 * se revalidan contra el ETag en cada uso (`no-cache` no significa "no guardar"): un 304 cuesta
 * una ida y vuelta sin cuerpo.
 */
const REVALIDAR = 'no-cache';

/**
 * La cabecera `Cache-Control` de un archivo estático, según si su nombre lleva el hash.
 *
 * Existe porque `express.static` tenía `maxAge: '1y'` para todo `browser/`, y una traducción
 * nueva no le llegaba a quien ya había visitado: el 4 de octubre de 2026 la ficha de dev mostraba
 * «catalogo.marca Honor». La red tenía el `es.json` con la clave; el navegador seguía con el del 2
 * de octubre, que podía guardar un año.
 */
export function cabeceraDeCache(rutaArchivo: string, carpetaRaiz: string): string {
  const relativa = relative(carpetaRaiz, rutaArchivo).split(sep).join('/');
  // Los bundles van en la raíz; uno con la misma forma dentro de `assets/` lo copió Angular tal cual.
  const enLaRaiz = !relativa.includes('/');
  if (relativa.startsWith('media/') || (enLaRaiz && CON_HASH.test(relativa))) {
    return UN_ANO;
  }
  return REVALIDAR;
}
