/**
 * Resolución del tema en el servidor. Funciones puras, aparte de `server.ts`,
 * para poder probarlas: el bug que llevó a extraerlas no lo atrapaba nada.
 *
 * El cliente persiste el tema elegido en una cookie (no en `localStorage`: el
 * servidor no puede leerlo, y el punto es resolver el tema aquí para no
 * parpadear al hidratar). "sistema" no se resuelve en el servidor —no hay forma
 * confiable de saber la preferencia del sistema operativo del visitante— y se
 * deja para el script en línea de `index.html`.
 */

const TEMAS_VALIDOS = new Set(['claro', 'oscuro']);

export function leerTema(cabeceraCookie: string | undefined): string | undefined {
  const valor = /(?:^|;\s*)ts-tema=([^;]+)/.exec(cabeceraCookie ?? '')?.[1];
  return valor && TEMAS_VALIDOS.has(valor) ? valor : undefined;
}

/**
 * Inyecta `data-tema` en la etiqueta `<html>` del documento servido.
 *
 * Coincide contra la etiqueta de apertura, no contra `<html lang="es">`
 * literal: Angular sirve `lang="en"` en el prefijo de idioma inglés, así que un
 * reemplazo por cadena fija fallaba en silencio y **el tema elegido no se
 * aplicaba en medio sitio**. Encontrado recorriendo el sitio en el navegador —
 * en inglés, recargar volvía a claro con el selector diciendo "Dark".
 */
export function conTemaAplicado(html: string, tema: string): string {
  return html.replace(/<html\b/, `<html data-tema="${tema}"`);
}
