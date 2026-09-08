import { enlacesAlternativos, urlAbsoluta } from '../app/core/seo/enlaces-alternativos';

/**
 * Una página del sitio, tal como entra al sitemap.
 *
 * `ruta` va **sin** prefijo de idioma: el constructor emite una entrada por idioma y las cruza
 * entre sí. `lastmod` es opcional a propósito — ver {@link construirSitemap}.
 */
export interface PaginaDelSitio {
  /** Ruta sin prefijo de idioma: `''` para la portada, `/productos`, `/legales/terminos`. */
  readonly ruta: string;
  /** Instante ISO-8601 de la última modificación real. Se omite si no se conoce de verdad. */
  readonly lastmod?: string;
}

const IDIOMAS = ['es', 'en'] as const;

/** Tope del formato, el mismo que aplica el backend. Aquí protege de un consumidor descuidado. */
const MAXIMO_URLS = 50_000;

/**
 * Escapa lo que XML no admite dentro de un valor de atributo ni de texto.
 *
 * No es defensivo por si acaso: un slug con `&` —que la base admite, porque la restricción de slug
 * vive en el dominio y no en la columna— produciría un XML mal formado, y un sitemap mal formado
 * no se ignora parcialmente: Google descarta el archivo entero. Una URL rara no puede tumbar las
 * demás.
 */
function escapar(texto: string): string {
  return texto
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/**
 * Construye el `sitemap.xml` completo del sitio.
 *
 * **Una entrada por idioma, cada una declarando las dos alternativas y `x-default`.** Es lo que
 * pide el formato: no basta con listar `/es/...` y confiar en que el rastreador descubra `/en/...`.
 *
 * **Las alternativas salen de `enlacesAlternativos`**, la misma función que escribe los `<link
 * rel="alternate">` del `<head>` y a la que también recurre el selector de idioma del encabezado.
 * Eso no es reutilización por ahorro: si el sitemap y la página declararan alternativas distintas,
 * el rastreador recibiría dos versiones contradictorias del mismo hecho, y ese es justo el tipo de
 * error que nadie ve hasta que las páginas dejan de aparecer.
 *
 * **`lastmod` se omite cuando no se conoce.** Un `lastmod` inventado —la fecha de hoy en cada
 * despliegue, que es lo que hace medio internet— es peor que ninguno: Google aprende a ignorarlo y
 * deja de servir para lo único que sirve, que es avisar de un cambio real.
 */
export function construirSitemap(origen: string, paginas: readonly PaginaDelSitio[]): string {
  const entradas = paginas
    .slice(0, MAXIMO_URLS)
    .flatMap((pagina) =>
      IDIOMAS.map((idioma) => entradaXml(origen, `/${idioma}${pagina.ruta}`, pagina.lastmod)),
    );

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"' +
      ' xmlns:xhtml="http://www.w3.org/1999/xhtml">',
    ...entradas,
    '</urlset>',
    '',
  ].join('\n');
}

function entradaXml(origen: string, ruta: string, lastmod: string | undefined): string {
  const alternativas = enlacesAlternativos(origen, ruta)
    .map(
      (alternativa) =>
        `    <xhtml:link rel="alternate" hreflang="${escapar(alternativa.hreflang)}"` +
        ` href="${escapar(alternativa.href)}"/>`,
    )
    .join('\n');

  return [
    '  <url>',
    `    <loc>${escapar(urlAbsoluta(origen, ruta))}</loc>`,
    ...(lastmod ? [`    <lastmod>${escapar(lastmod)}</lastmod>`] : []),
    alternativas,
    '  </url>',
  ].join('\n');
}

/**
 * `robots.txt`.
 *
 * **Solo se prohíbe `/admin`, y esa restricción es deliberadamente estrecha.** El carrito, el
 * checkout y la cuenta ya salen con `noindex` en su `<head>`, y añadirles un `Disallow` sería un
 * error clásico y contraproducente: prohibir el rastreo impide que el rastreador **lea** el
 * `noindex`, con lo cual esas URL pueden acabar indexadas igual —sin descripción, a partir de
 * enlaces externos— y sin forma de sacarlas. Prohibir y desindexar son cosas opuestas: para no
 * aparecer hay que dejar entrar.
 *
 * `/admin` es la excepción justificada: se renderiza solo en el cliente
 * (`app.routes.server.ts`), así que el HTML que recibe un rastreador sin JavaScript no lleva su
 * `noindex`. Ahí `Disallow` es lo único que queda.
 */
export function construirRobots(origen: string): string {
  return [
    'User-agent: *',
    'Allow: /',
    ...IDIOMAS.map((idioma) => `Disallow: /${idioma}/admin`),
    '',
    `Sitemap: ${urlAbsoluta(origen, '/sitemap.xml')}`,
    '',
  ].join('\n');
}
