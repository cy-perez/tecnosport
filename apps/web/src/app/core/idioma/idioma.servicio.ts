/**
 * Reemplaza el primer segmento (el prefijo de idioma) de una URL, dejando el
 * resto de la ruta y los parámetros de consulta intactos. Función pura:
 * docs/05-i18n.md — "el selector de idioma lleva a la misma página en el
 * otro idioma, no a la portada".
 */
export function urlEnOtroIdioma(url: string, nuevoIdioma: string): string {
  const [ruta, query] = url.split('?');
  const segmentos = ruta.split('/').filter((segmento) => segmento.length > 0);
  segmentos[0] = nuevoIdioma;
  const nuevaRuta = '/' + segmentos.join('/');
  return query ? `${nuevaRuta}?${query}` : nuevaRuta;
}
