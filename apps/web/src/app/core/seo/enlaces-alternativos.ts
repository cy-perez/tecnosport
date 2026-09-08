import { urlEnOtroIdioma } from '../idioma/idioma.servicio';

export interface EnlaceAlternativo {
  readonly hreflang: string;
  readonly href: string;
}

/**
 * Los dos idiomas del sitio con el código que espera `hreflang`, que no es el
 * mismo que el prefijo de la URL: `docs/05-i18n.md` pide `es-CO` y `en`.
 * El español lleva región porque se vende en Colombia y los precios, los plazos
 * y las garantías del texto son los colombianos; el inglés no la lleva porque
 * no está dirigido a ningún país en particular.
 */
const IDIOMAS = [
  { prefijo: 'es', hreflang: 'es-CO' },
  { prefijo: 'en', hreflang: 'en' },
] as const;

/** El idioma al que apunta `x-default`, y el mismo al que redirige la raíz del sitio. */
const IDIOMA_POR_OMISION = 'es';

/**
 * La ruta que se declara como canónica: la del router, **sin** parámetros de
 * consulta ni fragmento.
 *
 * Esa poda es una decisión, no una simplificación. La única pantalla del sitio
 * con parámetros de consulta es la rejilla, y ahí `?categoria=`, `?orden=` y
 * `?cursor=` no producen páginas distintas —mismo encabezado, mismo texto, misma
 * plantilla— sino recortes de la misma. Declararlas todas canónicas sería pedir
 * que se indexe una combinación por cada filtro, orden y página del catálogo:
 * cientos de URL con el mismo contenido compitiendo entre sí. Todas apuntan a
 * `/{idioma}/productos`, que es la que de verdad existe.
 *
 * El día que una categoría tenga ruta propia y texto propio, deja de ser un
 * recorte y pasa a merecer su canónico; ese día se cambia aquí.
 */
export function rutaCanonica(url: string): string {
  const sinFragmento = url.split('#')[0];
  const sinConsulta = sinFragmento.split('?')[0];
  // Sin barra final, salvo que la ruta sea solo la barra: `/es/productos/` y
  // `/es/productos` son la misma página y solo una puede ser la canónica.
  return sinConsulta.length > 1 ? sinConsulta.replace(/\/+$/, '') : '/';
}

/** Une origen y ruta sin arriesgar una barra doble ni una barra faltante. */
export function urlAbsoluta(origen: string, ruta: string): string {
  return `${origen.replace(/\/+$/, '')}${ruta.startsWith('/') ? ruta : `/${ruta}`}`;
}

/**
 * Las alternativas de idioma de una página: una por idioma más `x-default`.
 *
 * `urlEnOtroIdioma` es la misma función que usa el selector de idioma del
 * encabezado, y eso es a propósito: lo que `hreflang` le promete al rastreador
 * —"esta misma página, en el otro idioma"— tiene que ser exactamente adonde va
 * el visitante que pulsa el selector. Si algún día las rutas se traducen
 * (`/en/products/...`, que `docs/05-i18n.md` ya contempla), las dos cosas
 * cambian a la vez porque salen del mismo sitio.
 */
export function enlacesAlternativos(origen: string, ruta: string): EnlaceAlternativo[] {
  const canonica = rutaCanonica(ruta);
  const alternativas = IDIOMAS.map(({ prefijo, hreflang }) => ({
    hreflang,
    href: urlAbsoluta(origen, urlEnOtroIdioma(canonica, prefijo)),
  }));
  return [
    ...alternativas,
    {
      hreflang: 'x-default',
      href: urlAbsoluta(origen, urlEnOtroIdioma(canonica, IDIOMA_POR_OMISION)),
    },
  ];
}
