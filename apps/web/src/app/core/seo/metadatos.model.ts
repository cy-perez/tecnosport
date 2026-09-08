/**
 * Lo que una ruta declara en su `data.seo`. No lleva texto: lleva el prefijo de
 * las claves de Transloco de donde salen el título y la descripción
 * (`${clave}.titulo`, `${clave}.descripcion`), porque ningún texto visible se
 * escribe en el código (regla dura #4) y un `<title>` es texto visible: sale en
 * la pestaña, en el marcador y en el resultado de búsqueda.
 */
export interface SeoDeRuta {
  readonly clave: string;
  /**
   * Por omisión **false**, y esa omisión es la que no hace daño: una pantalla
   * nueva a la que se le olvide declararlo se queda fuera del índice, que se
   * arregla con un commit. Al revés —indexable por omisión— el descuido publica
   * el carrito, el checkout o el panel, y eso no se arregla con un commit sino
   * pidiéndole a Google que desindexe. Mismo criterio que `prioritaria` en
   * `ts-tarjeta-producto` (apps/web/CLAUDE.md).
   */
  readonly indexable?: boolean;
}

/** Metadatos ya resueltos, listos para escribirse en el `<head>`. */
export interface MetadatosPagina {
  readonly titulo: string;
  readonly descripcion: string;
  readonly indexable: boolean;
  /** URL absoluta de la imagen para compartir. Sin ella no se emite `og:image`. */
  readonly imagen?: string;
}
